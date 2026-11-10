#!/usr/bin/env bash
# diff-summary.sh — read-only summary of $BASE..$HEAD
# Fixes vs previous: snapKey matches member-or-extension form; effect catalog uses -F substring match
set -euo pipefail

REPO="${REPO:-$HOME/src/Lemon-Studio}"
BASE="${BASE:-e357d850}"
HEAD="${HEAD:-HEAD}"
LOG_DIR="${LOG_DIR:-$HOME/.lemon-logs}"
mkdir -p "$LOG_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
LOG="$LOG_DIR/diff-summary-$STAMP.log"
cd "$REPO"

exec > >(tee -a "$LOG") 2>&1

UI="android-ui/app/src/main/java/com/maxixa/lemon"
FFI="crates/lemon-engine-ffi"
hr() { printf '%.0s─' {1..64}; echo; }

hr; echo "  MERGED-DIFF SUMMARY   $BASE..$HEAD"; hr
echo "  repo: $REPO"
echo "  log : $LOG"
echo

git rev-parse --verify "$BASE" >/dev/null || { echo "!! BASE not found: $BASE"; exit 1; }
git rev-parse --verify "$HEAD" >/dev/null || { echo "!! HEAD not found: $HEAD"; exit 1; }

echo "════════ [1/8] Commit range ════════"
git log --oneline "$BASE".."$HEAD" || true
echo "HEAD: $(git rev-parse --short "$HEAD")"
git show -s --format='      %an <%ae>  %ai%n      %s' "$HEAD"
echo

echo "════════ [2/8] Upstream-crate guard (must be EMPTY) ════════"
GUARD=$(git diff --stat "$BASE".."$HEAD" -- \
    crates/os-engine crates/os-model crates/os-flp \
    crates/os-dsp crates/os-host crates/os-clap 2>/dev/null || true)
if [ -z "$GUARD" ]; then
    echo "  ✓ clean — upstream crates untouched"
else
    echo "  ✗ VIOLATION:"; echo "$GUARD"
fi
echo

echo "════════ [3/8] Overall stats ════════"
git diff --shortstat "$BASE".."$HEAD"
echo
echo "── dirstat (lines, threshold 0.5%) ──"
git diff --dirstat=lines,0.5 "$BASE".."$HEAD" | sed 's/^/  /' || true
echo

echo "════════ [4/8] Rust: per-crate ════════"
for c in crates/lemon-engine-ffi crates/os-engine crates/os-model crates/os-flp \
         crates/os-dsp crates/os-host crates/os-clap; do
    [ -d "$c" ] || continue
    S=$(git diff --numstat "$BASE".."$HEAD" -- "$c" 2>/dev/null || true)
    if [ -z "$S" ]; then
        printf "  %-22s unchanged\n" "$(basename "$c")"
    else
        F=$(echo "$S" | wc -l | tr -d ' ')
        A=$(echo "$S" | awk '$1!="-"{s+=$1} END{print s+0}')
        D=$(echo "$S" | awk '$2!="-"{s+=$2} END{print s+0}')
        printf "  %-22s %3d files  +%-6d -%-6d\n" "$(basename "$c")" "$F" "$A" "$D"
    fi
done
echo

echo "════════ [5/8] Kotlin: files touched by package ════════"
git diff --name-only "$BASE".."$HEAD" -- "$UI" \
    | grep '\.kt$' || true \
    | sed "s|^$UI/||" \
    | awk -F'/' '{
        if (NF==1) print "(root)"
        else if (NF==2) print $1
        else print $1"/"$2
    }' \
    | sort | uniq -c | sort -rn | sed 's/^/  /'
echo

echo "════════ [6/8] Feature markers ════════"
check() {
    local label="$1" file="$2" pat="$3"
    if [ ! -f "$REPO/$file" ]; then
        printf "  ✗ %-26s (missing file: %s)\n" "$label" "$(basename "$file")"
        return
    fi
    local n
    n=$(grep -cE "$pat" "$REPO/$file" || true)
    if [ "${n:-0}" -gt 0 ]; then
        printf "  ✓ %-26s %s (%d)\n" "$label" "$pat" "$n"
    else
        printf "  ✗ %-26s %s\n" "$label" "$pat"
    fi
}
check "snapKey def"       "$UI/state/PianoRollSelection.kt"            "fun snapKey[(]"
check "snapKey pencil"    "$UI/ui/piano/PianoRollGestures.kt"          "state.snapKey"
check "quantize+swing"    "$UI/state/PianoRollSelection.kt"            "quantizeWithSwing"
check "chord generator"   "$UI/ui/piano/AddChordDialog.kt"             "fun AddChordDialog"
check "scale lock"        "$UI/ui/piano/ScaleLockDialog.kt"            "fun ScaleLockDialog"
check "loop range"        "$UI/ui/settings/LoopRangeDialog.kt"         "fun LoopRangeDialog"
check "automation edit"   "$UI/ui/playlist/AutomationEditorDialog.kt"  "fun AutomationEditorDialog"
check "arrangement pick"  "$UI/ui/playlist/ArrangementPickerDialog.kt" "fun ArrangementPickerDialog"
check "fill dialog"       "$UI/ui/rack/FillDialog.kt"                  "fun FillDialog"
check "sample editor"     "$UI/ui/settings/SampleEditorDialog.kt"      "fun SampleEditorDialog"
check "timesig dialog"    "$UI/ui/settings/TimeSigDialog.kt"           "fun TimeSigDialog"
check "record dialog"     "$UI/ui/record/RecordDialog.kt"              "fun RecordDialog"
check "export dialog"     "$UI/ui/export/ExportDialog.kt"              "fun ExportDialog"

echo
echo "── Effect catalog (12 expected in $FFI/src/effects.rs) ──"
EFF="$REPO/$FFI/src/effects.rs"
if [ -f "$EFF" ]; then
    found=0; total=0
    for e in "Reverb" "Delay" "Limiter" "Parametric EQ" "Filter" "Chorus" \
             "Balance" "Stereo Shaper" "Stereo Enhancer" "Soft Clipper" \
             "Fast Distortion" "Transient Processor"; do
        total=$((total+1))
        if grep -qiF "$e" "$EFF" 2>/dev/null; then
            printf "  ✓ %s\n" "$e"; found=$((found+1))
        else
            printf "  ✗ %s\n" "$e"
        fi
    done
    echo "  ── $found / $total"
else
    echo "  (effects.rs missing)"
fi
echo

echo "════════ [7/8] LOC ceiling audit (target 250-300; >300 = split) ════════"
find "$REPO/$UI" -name '*.kt' -exec sh -c '
    n=$(wc -l < "$1")
    if [ "$n" -gt 300 ]; then echo "  SPLIT  $n  ${1#'"$REPO"'/}"
    elif [ "$n" -ge 250 ]; then echo "  TARGET $n  ${1#'"$REPO"'/}"
    fi' _ {} \; | sort -rn
echo
echo "── Rust .rs >300 (engine.rs exempt) ──"
find "$REPO/crates/lemon-engine-ffi/src" -name '*.rs' -exec sh -c '
    n=$(wc -l < "$1")
    b=$(basename "$1")
    if [ "$n" -gt 300 ] && [ "$b" != "engine.rs" ]; then
        echo "  $n  ${1#'"$REPO"'/}"
    fi' _ {} \; | sort -rn
echo

echo "════════ [8/8] Build artifacts ════════"
SO="$REPO/android-ui/app/src/main/jniLibs/arm64-v8a/liblemon_engine_ffi.so"
APK="$REPO/android-ui/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$SO" ]; then
    echo "  SO   $(stat -c '%s bytes  %y' "$SO")"
    echo "  JNI exports (T Java_*): $(nm -D "$SO" 2>/dev/null | grep -c ' T Java_' || true)"
else
    echo "  SO   (missing)"
fi
if [ -f "$APK" ]; then
    echo "  APK  $(stat -c '%s bytes  %y' "$APK")"
else
    echo "  APK  (missing)"
fi
echo
hr; echo "  DONE — log at $LOG"; hr
