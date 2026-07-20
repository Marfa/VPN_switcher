#!/bin/sh
# Remove build artifacts older than CUTOFF_DAYS. Safe to run manually or from post-commit.
set -e

CUTOFF_DAYS="${BUILD_ARTIFACT_MAX_AGE_DAYS:-7}"

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$ROOT"

remove_if_old() {
  path=$1
  [ -e "$path" ] || return 0
  if find "$path" -maxdepth 0 -mtime +"$CUTOFF_DAYS" 2>/dev/null | grep -q .; then
    rm -rf "$path"
    echo "cleanup-build-artifacts: removed $path"
  fi
}

# Directories listed in .gitignore as build / cache output
for dir in \
  .gradle \
  .android-sdk \
  .kotlin \
  build \
  app/build \
  captures \
  .externalNativeBuild \
  .cxx \
  stitch_ui \
  .idea
do
  remove_if_old "$dir"
done

# Loose artifact files at repo root
for f in *.apk *.aab stitch.zip output.json; do
  [ -e "$f" ] || continue
  if find "$f" -maxdepth 0 -mtime +"$CUTOFF_DAYS" 2>/dev/null | grep -q .; then
    rm -f "$f"
    echo "cleanup-build-artifacts: removed $f"
  fi
done

# IntelliJ module files anywhere in the tree
find . -name '*.iml' -mtime +"$CUTOFF_DAYS" -not -path './.git/*' 2>/dev/null \
  | while IFS= read -r f; do
      rm -f "$f"
      echo "cleanup-build-artifacts: removed $f"
    done
