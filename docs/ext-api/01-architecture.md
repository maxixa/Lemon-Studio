
Architecture

Three crates

```
crates/lemon-ext-api/       ← YOU DEPEND ON THIS. Versioned 1.x.
crates/lemon-engine-ffi/    ← JNI bridge; depends on ext-api + each extension
android-ui/ext-api/         ← Kotlin AAR; renders UiSpec generically
extensions/*/               ← One crate per extension. Add via 1 line in ext-ffi/Cargo.toml.
```

Discovery — inventory

inventory is a link-time registry. Each extension registers via:

```rust
inventory::submit! { NoteEditorReg(&MyEditor) }
```

At build, the linker collects all NoteEditorReg static instances. Host iterates via all_note_editors(). No dynamic .so loading, no AAR plugin mechanisms, no ServiceLoader. Compile-time only — the extension is a dependency in Cargo.toml.

Adding an extension = 3 files (Cargo.toml, lib.rs, one line in host) + cargo build.

Threading

generate and edit run on the UI worker thread, not the audio thread. Budget: 2s soft / 5s hard timeout. No realtime constraints — allocation, Vec clones, small HashMaps are all fine.

Extensions must be Send + Sync + 'static. Host stores one instance per extension for the whole process lifetime. Use Mutex if you need interior mutability (rare).

Never spawn threads from generate/edit. Never block on I/O. Never call back into the host.

Data flow (song generator example)

```
Kotlin  →  JNI  →  registry  →  SongGenerator::generate  →  SongSketch
   ▲                                                          │
   └────────────  JNI  ←  validate  ←─────────────────────────┘
                                    │
                                    ▼
                          Kotlin applies to engine
                          (create tracks, commit notes, etc.)
                          (one undo transaction)
```

Extension is a pure function. No side effects. Host owns all mutation, undo, error handling.

Determinism

Two calls with same (ctx, params) MUST produce identical output. All randomness goes through ctx.seed:

```rust
let mut rng = ExtRng::new(ctx.seed);
```

ExtRng is SplitMix64 — deterministic on all platforms. Do not use rand::thread_rng(), SystemTime, or any OS randomness. Doing so breaks reproducibility and the host cannot validate.

Failure handling

Every ExtError variant maps to a user-visible toast (except Internal/Unsupported, which are logged only). Host wraps calls in catch_unwind. Extension cannot crash the host.
