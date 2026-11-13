For a DAW, you'll need to build a small pipeline using a few specialized crates rather than looking for one monolithic solution.

· BPM & Beat Detection: Choose bpm-key for a pure-Rust, zero-dependency solution that analyzes a file and returns BPM with confidence scores. For pure speed and high accuracy matching librosa, beat-track-rs is excellent. If you need everything including key/chord/mood analysis, oximedia-mir is a comprehensive "MIR Swiss Army knife" powered by a MirAnalyzer.
· Transient & Onset Slicing: For the actual "cutting," use nidhi as a robust all-in-one sampler with built-in onset detection and loop points. If you only need to cut loops at silence, silence-split is a specialized, lightweight tool for splitting PCM audio at quiet points. Otherwise, oximedia-audio-analysis provides a pure-Rust transient detector, or bliss-audio-aubio-rs offers battle-tested C-library bindings for advanced segmentation.

🚀 Suggested Integration Plan

Don't try to implement all of these at once. A phased approach will be most effective:

1. Phase 1: BPM & Beat Grid: Start by integrating bpm-key. It's pure Rust, easy to use, and will give you the initial BPM and a beat grid to work with.
2. Phase 2: Slicing & Loop Detection: Add nidhi as your sampler engine. It can handle playback, time-stretching, and has built-in onset detection, giving you a solid foundation for loop manipulation.
3. Phase 3: Advanced Analysis (Optional): If you later want features like key detection, chord recognition, or advanced structural segmentation, you can bring in oximedia-mir.

To get started, you'll need to add bpm-key and nidhi to your crates/lemon-engine-ffi/Cargo.toml file.

Hope this helps you build out the advanced loop manipulation features. Let me know if you want a deeper dive into any of these crates.