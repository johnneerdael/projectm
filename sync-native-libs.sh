#!/usr/bin/env bash
# Sync (download) all required native .so libraries from a remote build box
# into the local Android project's jniLibs directories using scp.
#
# Assumes the remote build script placed artifacts in:
#   /root/projectm/arm32  (armeabi-v7a)
#   /root/projectm/arm64  (arm64-v8a)
# and that each contains:
#   libprojectM-4.so
#   libprojectM-4-playlist.so (optional)
#   libprojectmtv.so         (optional wrapper)
#   libc++_shared.so         (C++ runtime)
#
# Usage examples:
#   ./sync-native-libs.sh                       # use defaults
#   HOST=192.168.50.99 ./sync-native-libs.sh    # override host
#   IDENTITY=~/.ssh/id_ed25519 ./sync-native-libs.sh
#   DRY_RUN=1 ./sync-native-libs.sh             # show what would copy
#
# Environment variables you can override:
#   HOST          - remote host/IP (default 192.168.50.67)
#   USER          - remote SSH user (default root)
#   IDENTITY      - ssh private key path (optional)
#   REMOTE_BASE32 - remote arm32 dir (default /root/projectm/arm32)
#   REMOTE_BASE64 - remote arm64 dir (default /root/projectm/arm64)
#   LOCAL_JNILIBS - local base jniLibs dir (default app/src/main/jniLibs)
#   DRY_RUN       - if set (non-empty) only print actions
#   STRICT        - if set, fail when optional libs missing
#
# macOS note: Script is zsh/bash compatible; requires scp + ssh.

set -euo pipefail

HOST=${HOST:-192.168.50.67}
USER=${USER:-root}
IDENTITY=${IDENTITY:-}
REMOTE_BASE32=${REMOTE_BASE32:-/root/projectm/arm32}
REMOTE_BASE64=${REMOTE_BASE64:-/root/projectm/arm64}
LOCAL_JNILIBS=${LOCAL_JNILIBS:-/Users/jneerdaekl/Scripts/projectm-android-tv/app/src/main/jniLibs}
DRY_RUN=${DRY_RUN:-}
STRICT=${STRICT:-}

ARM32_LOCAL="$LOCAL_JNILIBS/armeabi-v7a"
ARM64_LOCAL="$LOCAL_JNILIBS/arm64-v8a"

ALLOW_PASSWORD=${ALLOW_PASSWORD:-}
# Expected libs (first is mandatory, others optional unless STRICT set)
MANDATORY_LIBS=(libprojectM-4.so libc++_shared.so)
OPTIONAL_LIBS=(libprojectM-4-playlist.so libprojectmtv.so)

SSH_BASE=(ssh -o BatchMode=yes -o ConnectTimeout=5)
SCP_BASE=(scp -p)
[ -n "$IDENTITY" ] && SSH_BASE+=( -i "$IDENTITY" ) && SCP_BASE+=( -i "$IDENTITY" )

if [ -n "$ALLOW_PASSWORD" ]; then
  echo "INFO: Password authentication enabled (BatchMode disabled)."
  SSH_BASE=(ssh -o ConnectTimeout=5)
else
  SSH_BASE=(ssh -o BatchMode=yes -o ConnectTimeout=5)
fi
remote_ls() { "${SSH_BASE[@]}" "$USER@$HOST" "ls -1 $1" 2>/dev/null || true; }

fail() { echo "ERROR: $*" >&2; exit 1; }

echo "== Sync native libs from $USER@$HOST =="

# Quick connectivity & remote directory checks
for dir in "$REMOTE_BASE32" "$REMOTE_BASE64"; do
  echo "-- Checking remote dir: $dir"
  content=$(remote_ls "$dir")
  if [ -z "$content" ]; then
    # Distinguish between missing dir and permission/empty
    if "${SSH_BASE[@]}" "$USER@$HOST" "[ -d '$dir' ]" 2>/dev/null; then
      echo "WARN: Directory exists but listing returned empty (permission? no files?)"
    else
      echo "WARN: Directory does not exist or not accessible: $dir"
    fi
  else
    echo "$content" | sed 's/^/   /'
  fi
done

# Ensure local directories exist
mkdir -p "$ARM32_LOCAL" "$ARM64_LOCAL"

download_set() {
  local remote_dir=$1 local_dir=$2 abi=$3
  echo "-- Processing $abi"

  # Build list of candidate libs present remotely
  local present
  present=$(remote_ls "$remote_dir")

  # Validate mandatory libs
  for lib in "${MANDATORY_LIBS[@]}"; do
    if grep -qx "$lib" <<<"$present"; then
      :
    else
      fail "Mandatory library $lib missing in $remote_dir (ABI=$abi)"
    fi
  done

  if [ -z "$present" ]; then
    echo "DEBUG: No entries listed in $remote_dir (user=$USER). Use ALLOW_PASSWORD=1 if password auth is required or adjust USER/REMOTE_BASE vars." >&2
  fi
  # Compose full list (include optional if exists or STRICT)
  local to_fetch=("${MANDATORY_LIBS[@]}")
  for opt in "${OPTIONAL_LIBS[@]}"; do
    if grep -qx "$opt" <<<"$present"; then
      to_fetch+=("$opt")
    else
      if [ -n "$STRICT" ]; then
        fail "Optional library $opt missing (STRICT mode) in $remote_dir"
      else
        echo "INFO: Optional lib $opt not found in $remote_dir (skipping)"
      fi
    fi
  done

  # Fetch each
  for lib in "${to_fetch[@]}"; do
    local src="$USER@$HOST:$remote_dir/$lib"
    local dst="$local_dir/$lib"
    if [ -n "$DRY_RUN" ]; then
      echo "DRY-RUN scp $src -> $dst"
    else
      echo "Copying $lib -> $dst"
      "${SCP_BASE[@]}" "$src" "$dst"
    fi
  done
}

download_set "$REMOTE_BASE32" "$ARM32_LOCAL" "armeabi-v7a"
download_set "$REMOTE_BASE64" "$ARM64_LOCAL" "arm64-v8a"

echo "== Summary =="
for d in "$ARM32_LOCAL" "$ARM64_LOCAL"; do
  echo "-- $d"; ls -l "$d" || true; done

echo "Done. Rebuild APK if needed: ./gradlew installDebug"
