cd ~/src/Lemon-Studio
UI=android-ui/app/src/main/java/com/maxixa/lemon
D="$UI/ui/playlist"

echo "════════ A. Ship 2a build verification ════════"
echo "APK: $(ls -la android-ui/app/build/outputs/apk/debug/app-debug.apk 2>/dev/null | awk '{print $5" bytes, "$6" "$7" "$8}')"
echo ""
echo "Last build log:"
tail -3 ~/.lemon-logs/fade-drag-overlay-gradle.log 2>/dev/null || echo "  (no fade-drag-overlay log — Ship 2a may not have built)"

echo ""
echo "════════ B. Ship 2a render function present ════════"
grep -q "fun DrawScope.drawFadeDragOverlay" "$D/PlaylistFadeRender.kt" && echo "  ✓ drawFadeDragOverlay present" || echo "  ✗ missing"
grep -q "drawFadeDragOverlay(" "$D/PlaylistClipRender.kt" && echo "  ✓ call wired in ClipRender" || echo "  ✗ call missing"

echo ""
echo "════════ C. Ship 3 prerequisite — Clip::gain() signature ════════"
grep -n "pub fn gain" crates/os-model/src/lib.rs -B3 -A8

echo ""
echo "════════ D. Ship 3 prerequisite — set_gain exists? ════════"
grep -n "pub fn set_gain\|fn set_gain" crates/os-model/src/lib.rs -A6 || echo "  (no set_gain — will need raw-byte write or skip)"

echo ""
echo "════════ E. Current CLIP wire size ════════"
grep -n "pub const CLIP\|const VERSION" crates/lemon-engine-ffi/src/playlist.rs