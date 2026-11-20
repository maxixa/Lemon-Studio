
NoteEditor

Transform existing notes. Pure function. Runs on UI thread.

Trait

```rust
pub trait NoteEditor: Send + Sync + 'static {
    fn id(&self) -> &'static str;                // "quantize", "humanize"
    fn name(&self) -> &'static str;              // "Quantize"
    fn description(&self) -> &'static str;       // optional
    fn version(&self) -> &'static str;           // your extension's version
    fn params(&self) -> &'static [ParamSpec];
    fn ui(&self) -> UiSpec;                      // optional; default empty
    fn needs(&self) -> InputNeeds;               // default TARGET_RANGE
    fn edit(&self, notes: &[Note], params: &ParamValues) -> ExtResult<Vec<Note>>;
    fn accepts_empty(&self) -> bool;             // default false
}
```

edit must not mutate notes. Return a new Vec. Input notes are read-only.

Worked example — Quantize

```rust
use lemon_ext_api::*;

struct Quantize;

impl NoteEditor for Quantize {
    fn id(&self) -> &'static str { "quantize" }
    fn name(&self) -> &'static str { "Quantize" }
    fn description(&self) -> &'static str { "Snap note starts to a grid" }

    fn params(&self) -> &'static [ParamSpec] {
        static P: &[ParamSpec] = &[
            ParamSpec::new("grid", "Grid (in ppq units)", 1.0, 96.0, 24.0),
        ];
        P
    }

    fn edit(&self, notes: &[Note], p: &ParamValues) -> ExtResult<Vec<Note>> {
        let grid = p.f32_or("grid", 24.0) as u32;
        if grid == 0 { return Err(ExtError::InvalidParams { field: "grid", reason: "must be ≥ 1".into() }); }
        Ok(notes.iter().map(|n| {
            let snapped = ((n.start_tick + grid / 2) / grid) * grid;
            Note { start_tick: snapped, ..*n }
        }).collect())
    }
}

inventory::submit! { NoteEditorReg(&Quantize) }
```

Testing

See 08-testing.md. At minimum, add a golden test:

```rust
#[test]
fn quantize_snaps_to_grid() {
    let ed = Quantize;
    let input = vec![Note::new(13, 96, 60, 100)];
    let mut p = ParamValues::new();
    p.set("grid", ParamValue::Float(24.0));
    let out = ed.edit(&input, &p).unwrap();
    assert_eq!(out[0].start_tick, 24);
}
```

Phase editor (Reich-style phasing) — via references

For "make N copies of these notes at increasing offsets" — that's a NoteGenerator with InputNeeds::TARGET_RANGE | REFERENCES, not an editor. Phase A lives in generators because it adds notes, doesn't transform them. See 03-note-generator.md.
