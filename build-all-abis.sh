#!/usr/bin/env bash
set -euo pipefail

# Build both armeabi-v7a (32-bit) and arm64-v8a, then copy all needed .so
# outputs into /root/projectm/arm32 and /root/projectm/arm64 for easy scp.
# Also still populates app/src/main/jniLibs for local APK builds.

: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME not set}"
SRC_DIR="projectm"
ABIS=("armeabi-v7a" "arm64-v8a")
CMAKE_COMMON=(-G Ninja \
  -DANDROID_PLATFORM=21 \
  -DCMAKE_BUILD_TYPE=Release \
  -DANDROID_STL=c++_shared \
  -DENABLE_GLES=ON \
  -DBUILD_SHARED_LIBS=ON \
  -DUSE_SYSTEM_PROJECTM_EVAL=OFF)

# Root export directories (adjust if you prefer another base path)
EXPORT_BASE_32="/root/projectm/arm32"
EXPORT_BASE_64="/root/projectm/arm64"

mkdir -p "$EXPORT_BASE_32" "$EXPORT_BASE_64"

echo "Using NDK: $ANDROID_NDK_HOME"

declare -A EXPORT_MAP
EXPORT_MAP[armeabi-v7a]="$EXPORT_BASE_32"
EXPORT_MAP[arm64-v8a]="$EXPORT_BASE_64"

for ABI in "${ABIS[@]}"; do
  BUILD_DIR="build-$ABI"
  echo "=== Configuring $ABI ==="
  cmake -S "$SRC_DIR" -B "$BUILD_DIR" \
    -DANDROID_ABI="$ABI" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    "${CMAKE_COMMON[@]}" \
    $([ "$ABI" = "armeabi-v7a" ] && echo -DANDROID_ARM_NEON=ON || true)

  echo "=== Building $ABI ==="
  cmake --build "$BUILD_DIR" --parallel

done

echo "=== Copying libs into app/src/main/jniLibs and export dirs ==="
JNI_BASE="app/src/main/jniLibs"
mkdir -p "$JNI_BASE"

copy_stl() {
  local abi="$1"; local outdir="$2"
  local triple api=21
  case "$abi" in
    armeabi-v7a) triple="arm-linux-androideabi" ;;
    arm64-v8a)   triple="aarch64-linux-android" ;;
    *) echo "Unknown ABI $abi" >&2; return 1 ;;
  esac
  # NDK layouts differ: some have .../usr/lib/<triple>/libc++_shared.so (no API dir), others include /<api>/.
  local base_glob="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"/*/sysroot/usr/lib/$triple
  local candidates=(
    "$base_glob/$api/libc++_shared.so"
    "$base_glob/libc++_shared.so"
  )
  local found=""
  for c in "${candidates[@]}"; do
    # shellcheck disable=SC2086 # we intentionally allow glob expansion
    for expanded in $c; do
      if [ -f "$expanded" ]; then
        found="$expanded"; break 2
      fi
    done
  done
  if [ -z "$found" ]; then
    echo "ERROR: Could not locate libc++_shared.so for $abi (searched: ${candidates[*]})" >&2
    exit 1
  fi
  cp "$found" "$outdir/" || { echo "Failed to copy $found"; exit 1; }
}

for ABI in "${ABIS[@]}"; do
  ABI_DIR="$JNI_BASE/$ABI"; mkdir -p "$ABI_DIR"
  EXPORT_DIR="${EXPORT_MAP[$ABI]}"; mkdir -p "$EXPORT_DIR"

  # Dynamically discover produced libs (main + playlist + any wrapper) to avoid hard-coded subpaths.
  BUILD_DIR="build-$ABI"
  if [ ! -d "$BUILD_DIR" ]; then
    echo "ERROR: Missing build dir $BUILD_DIR" >&2; exit 1
  fi
  mapfile -t FOUND_LIBS < <(find "$BUILD_DIR" -maxdepth 5 -type f \( -name 'libprojectM-4*.so' -o -name 'libprojectmtv*.so' \))
  if [ ${#FOUND_LIBS[@]} -eq 0 ]; then
    echo "WARN: No projectM .so files found for $ABI under $BUILD_DIR"
  else
    for lib in "${FOUND_LIBS[@]}"; do
      echo "Copying $(basename "$lib") for $ABI"
      cp "$lib" "$ABI_DIR/"
      cp "$lib" "$EXPORT_DIR/"
    done
  fi

  # STL runtime
  copy_stl "$ABI" "$ABI_DIR"
  copy_stl "$ABI" "$EXPORT_DIR"
done

echo "=== Export directory summaries ==="
for dir in "$EXPORT_BASE_32" "$EXPORT_BASE_64"; do
  echo "-- $dir"; ls -1 "$dir" || true; done

echo "=== jniLibs contents ==="
find "$JNI_BASE" -maxdepth 2 -type f -name "*.so" -print

echo "Done. Exported .so files are in:"
echo "  $EXPORT_BASE_32" 

echo "  $EXPORT_BASE_64"
echo "Build complete. You can now run ./gradlew installDebug or scp the export dirs."
