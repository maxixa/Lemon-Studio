I checked the mainstream README and its capability table against everything we've built. Here's the honest gap analysis.

Where we now match desktop

From the README's capability table, these areas are feature-complete relative to what we ported:

· FLP round-trip — same project::apply/create path, same FL Studio 24.2+ requirement, same "unknown events preserved" guarantee
· Basic patterns, playlist, mixer — we have all three
· Built-in instrument parity — Lemon Trio, Citrus, Lemon Sampler, Lemon Drum Sampler all play
· Basic stretch family — Resample/Stretch/Stretch Pro/Auto (4 of 12)
· Sample FX — trim/reverse/swap/clip/normalize now work via derived WAVs
· Export formats — WAV, FLAC, MP3, MIDI (no OGG yet)

Big things entirely missing

These are whole feature areas the desktop has that we don't touch:

Area What desktop has
Automation Clips for channel/mixer/plugin params, every curve shape, volume/pan recording
Plugin hosting CLAP, VST3, VST2 with editors, automation clips from plugin parameters, crash protection
MIDI input USB/BLE controllers, velocity curves, sustain pedal, pitch bend, aftertouch
MIDI sync Controller linking (Multilink), MIDI clock / timecode in and out
MIDI file import Full import into new project / open project / piano roll channel
Audio recording into playlist One-click record into playlist or audio editor, from system inputs
Tesla audio editor Markers, regions, destructive edits, tools — after FL's Edison
Themes Lemon/Citrus skins, 25 built-in themes, custom .flstheme import, color picker
Full settings Audio, MIDI, File, Theme, Project, Info pages
PDC settings UI Engine works, UI to edit doesn't — same limitation as desktop
Older FLP versions FL Studio < 24.2 (roadmap on desktop too)

Partial features we do have

Area We have Desktop also has
Piano roll Tap/drag/select/erase, velocity lane, basic quantize (1/16), copy/paste, note properties, snap grid 4 modes Chord detection, note names, chop, randomize, strum, arpeggiate, flam, claw machine, flip, level scaling, limit, LFO, event lanes
Playlist Pattern clips, drag/resize, 4-bar viewport, auto-follow Audio clips, automation clips, clip groups, markers, multiple arrangements
Mixer Faders, pan, 4 effect slots, master strip, mute/solo Sends, routing UI, 10 slots, sidechain, per-track meters
Stretch 4 methods All 12 methods (E3, E2, Slice families)
Export WAV, FLAC, MP3, MIDI, WAV stems OGG, bit depth, dither, mono, tails, WAV markers, Quick render / render-and-replace
Undo Notes, arrangement, tempo, mute/solo, effects Full project history — knobs, faders, channels added/deleted, project settings, plugin editor changes
Built-in effects Reverb, Delay, Limiter, Parametric EQ, Filter (5) Also Chorus, Stereo Shaper, Stereo Enhancer, Balance, Soft Clipper, Fast Distortion, Transient Processor (12 total)

Android-specific gaps (no desktop equivalent)

Gap Impact
Background audio / AudioFocus App likely stops when screen sleeps
USB / BLE MIDI No hardware controller input — touch only
AAP plugin hosting Android's native plugin format; the multi-hour effort we discussed
SAF tree import for factory samples Text path input only (adb push workaround)
Low-latency AAudio mode cpal uses AAudio but we never set performance mode

Deliberate non-ports

· VST2/VST3/CLAP hosting — cannot work on Bionic; AAP is the Android answer
· ASIO / WASAPI / Core Audio — Android uses AAudio/Oboe
· Windows/macOS-specific crash isolation — Android's audio thread model is different
· .vstpreset / .fxp / .fxb preset import — desktop plugin format only

Realistic "what's next" ranking

If you keep going, the highest-value missing pieces in order:

1. Piano roll velocity/event lanes + quantize grid UI — closes the biggest musical gap. No engine changes.
2. Automation clips (basic) — model has automations: Vec<Automation>, engine has apply_edit. UI only.
3. MIDI file import — os-smf crate exists, pure Rust.
4. Sends/routing UI — model has send_levels, engine honors it. UI only.
5. Per-track meters — one Vec<AtomicU32> + polling. ~40 LOC engine-side.
6. Themes — 3 accent color palettes in Color.kt → new SkinStore in prefs. ~100 LOC, no Rust.
7. Tesla-style audio editor — huge. Weeks. Not worth it until the above are done.

Automation and MIDI input are the two features that would most change what the app feels like it can do — everything else is polish on existing workflows.