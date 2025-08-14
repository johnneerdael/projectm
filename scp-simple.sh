#!/usr/bin/env bash
# Ultra-simple non-interactive scp of native libs (arm32 + arm64) from a remote build host.
# WARNING: Embedding plaintext passwords is insecure. Prefer SSH keys.
# Supports three auth modes (in order):
#   1. SSH key via IDENTITY
#   2. sshpass + SSHPASS environment variable (install sshpass first)
#   3. Falls back to normal scp (will prompt)
#
# Usage:
#   ./scp-simple.sh                             # default values, key/prompt
#   HOST=192.168.50.67 USER=root ./scp-simple.sh
#   IDENTITY=~/.ssh/id_ed25519 ./scp-simple.sh
#   SSHPASS='mypassword' ./scp-simple.sh        # requires sshpass
#
DEBUG=${DEBUG:-}
[ -n "$DEBUG" ] && set -x

# Configurable env vars:
#   HOST          remote host/IP (default 192.168.50.67)
#   USER          remote user (default root)
#   REMOTE_BASE32 remote dir with arm32 libs (default /root/projectm/arm32)
#   REMOTE_BASE64 remote dir with arm64 libs (default /root/projectm/arm64)
#   LOCAL_JNILIBS local jniLibs base (default app/src/main/jniLibs)
#   FILES         space-separated list of libs to copy (default core + playlist + libc++_shared)
#   IDENTITY      ssh private key path
#   SSHPASS       password (if using sshpass)
#   QUIET=1       minimal output
#   SKIP_ARM32=1  skip arm32
#   SKIP_ARM64=1  skip arm64

set -euo pipefail

HOST=${HOST:-192.168.50.67}
USER=${USER:-root}
REMOTE_BASE32=${REMOTE_BASE32:-/root/projectm/arm32}
REMOTE_BASE64=${REMOTE_BASE64:-/root/projectm/arm64}
LOCAL_JNILIBS=${LOCAL_JNILIBS:-app/src/main/jniLibs}
IDENTITY=${IDENTITY:-}
FILES=${FILES:-"libprojectM-4.so libprojectM-4-playlist.so libc++_shared.so libprojectmtv.so"}
QUIET=${QUIET:-}
# If you insist on embedding a password, set EMBED_PASS below (INSECURE). Prefer SSH keys.
EMBED_PASS=${EMBED_PASS:-}
SSHPASS=${SSHPASS:-${EMBED_PASS}}

ARM32_LOCAL="$LOCAL_JNILIBS/armeabi-v7a"
ARM64_LOCAL="$LOCAL_JNILIBS/arm64-v8a"

mkdir -p "$ARM32_LOCAL" "$ARM64_LOCAL"

log() { [ -n "$QUIET" ] || echo "$*"; }

# Build scp command base
SCP_CMD=(scp -p)
[ -n "$IDENTITY" ] && SCP_CMD+=( -i "$IDENTITY" )

# If password provided, attempt sshpass
if [ -n "${SSHPASS:-}" ]; then
  if command -v sshpass >/dev/null 2>&1; then
    SCP_CMD=(sshpass -e "${SCP_CMD[@]}") # sshpass reads password from SSHPASS env
    export SSHPASS
    log "Using sshpass with SSHPASS env variable."
  else
    echo "WARN: SSHPASS set but sshpass not installed; falling back to normal scp (will prompt)." >&2
  fi
fi

copy_set() {
  local abi=$1 remote_dir=$2 local_dir=$3
  [ -n "${SKIP_ARM32:-}" ] && [ "$abi" = "armeabi-v7a" ] && { log "Skipping $abi"; return; }
  [ -n "${SKIP_ARM64:-}" ] && [ "$abi" = "arm64-v8a" ] && { log "Skipping $abi"; return; }
  log "-- Copying $abi from $USER@$HOST:$remote_dir"
  # Sanity: if USER != root and remote_dir starts with /root/, warn
  if [[ $remote_dir == /root/* && $USER != root ]]; then
    echo "WARN: Attempting to read /root path as non-root user '$USER' – likely to fail (permission denied)." >&2
  fi
  for f in $FILES; do
    local src="$USER@$HOST:$remote_dir/$f"
    local dst="$local_dir/$f"
    # Use -q for quiet if requested
    if [ -n "$QUIET" ]; then
      if ! "${SCP_CMD[@]}" -q "$src" "$dst" 2>/dev/null; then
        log "  (missing) $f"
      else
        log "  copied $f"
      fi
    else
      if ! "${SCP_CMD[@]}" "$src" "$dst" 2>"$dst.error"; then
        echo "WARN: Could not copy $src" >&2
        if [ -s "$dst.error" ]; then
          echo "--- scp error detail ($f) ---" >&2
          sed 's/^/    /' "$dst.error" >&2
          rm -f "$dst.error"
        fi
      fi
    fi
  done
}

copy_set armeabi-v7a "$REMOTE_BASE32" "$ARM32_LOCAL"
copy_set arm64-v8a "$REMOTE_BASE64" "$ARM64_LOCAL"

log "== Summary =="
[ -n "$QUIET" ] || { ls -l "$ARM32_LOCAL" 2>/dev/null || true; ls -l "$ARM64_LOCAL" 2>/dev/null || true; }
log "Done. Rebuild APK if needed: ./gradlew installDebug"
