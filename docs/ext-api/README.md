# Lemon Studio Extension API

Three trait families. Compile-time extensions. Frozen for 1.x.

| Family | Input → Output | Examples |
|---|---|---|
| `NoteEditor` | existing notes → transformed | Quantize, Humanize, Scale Lock, Phase Copy |
| `NoteGenerator` | context → new notes (+optional extra tracks) | Arpeggiator, Solo Guitar, Euclidean |
| `SongGenerator` | context → full multi-track sketch | Lo-fi Beat, Jazz Progression, Song Structure |

## Quickstart (3 files)

**`extensions/my-gen/Cargo.toml`:**
```toml
[package]
name = "ext-my-gen"
version = "0.1.0"
[dependencies]
lemon-ext-api = { path = "../../crates/lemon-ext-api" }
```

extensions/my-gen/src/lib.rs:

```rust
use lemon_ext_api::*;

struct MyGen;
impl NoteGenerator for MyGen {
    fn id(&self) -> &'static str { "my-gen" }
    fn name(&self) -> &'static str { "My Generator" }
    fn params(&self) -> &'static [ParamSpec] { &[] }
    fn generate(&self, ctx: &GenContext, _p: &ParamValues) -> ExtResult<NoteGeneratorOutput> {
        let mut rng = ExtRng::new(ctx.seed);
        let pitch = rng.range_i32(60, 72) as u8;
        Ok(NoteGeneratorOutput::new(vec![
            Note::new(ctx.target.start_tick, ctx.ppq, pitch, 100),
        ]))
    }
}
inventory::submit! { NoteGeneratorReg(&MyGen) }
```

lemon-engine-ffi/Cargo.toml — one line:

```toml
ext-my-gen = { path = "../../extensions/my-gen" }
```

Build → extension appears in the piano-roll menu. Zero changes to Lemon source.

Stability promise

1.x stays source-compatible. Additive changes only. Breaking changes require 2.0 + 6-month deprecation. Pin lemon-ext-api = "1.0" in your Cargo.toml (not "1.0.0"), so patch/minor updates are automatic.

Doc index

# File Covers
1 01-architecture.md Crates, discovery, threading, data flow
2 02-note-editor.md NoteEditor + Quantize example
3 03-note-generator.md NoteGenerator + multi-output + refs
4 04-song-generator.md SongGenerator + sections + refs
5 05-ui-spec.md UiSpec, every widget, conditions
6 06-custom-ui.md Kotlin CustomExtScreen escape hatch
7 07-params.md ParamSpec, curves, ParamValues
8 08-testing.md Golden files, determinism, perf
9 09-versioning.md Semver contract
10 10-instrument-hints.md Categories + resolution

