
Parameters

Re-uses nothing from audio-effect params — self-contained so this crate's semver is independent.

ParamSpec

```rust
ParamSpec {
  id: &'static str,     // "density", "tempo" — also matches UiSpec field id
  name: &'static str,   // "Note density" — display name
  min: f32,
  max: f32,
  default: f32,
  unit: &'static str,   // "BPM", "%", "" — for display
  curve: ParamCurve,
}

enum ParamCurve {
  Linear,          // slider maps 1:1
  Logarithmic,     // for freq/gain ranges
  Discrete,        // integer steps — spinners, dropdowns
}
```

ParamValues

```rust
pub struct ParamValues { /* BTreeMap<&'static str, ParamValue> */ }

enum ParamValue {
  Float(f32),
  Int(i32),
  Bool(bool),
  Text(String),
}
```

Accessor API

```rust
impl ParamValues {
    pub fn f32(&self, id: &str) -> Option<f32>;
    pub fn i32(&self, id: &str) -> Option<i32>;
    pub fn bool(&self, id: &str) -> Option<bool>;
    pub fn str(&self, id: &str) -> Option<&str>;

    // Convenience with defaults:
    pub fn f32_or(&self, id: &str, default: f32) -> f32;
    pub fn i32_or(&self, id: &str, default: i32) -> i32;
    pub fn bool_or(&self, id: &str, default: bool) -> bool;
}
```

Use _or variants in generate / edit. User might not have touched a param → falls back to your default. Never .unwrap() an Option — use _or or return ExtError::InvalidParams.

Validation

Extension validates params at entry:

```rust
fn generate(&self, ctx: &GenContext, p: &ParamValues) -> ExtResult<NoteGeneratorOutput> {
    let density = p.f32_or("density", 0.6);
    if !(0.1..=1.0).contains(&density) {
        return Err(ExtError::InvalidParams {
            field: "density", reason: "must be 0.1–1.0".into(),
        });
    }
    // ...
}
```

Host shows the reason as a toast. Never panic on bad params.

Presets

User can save a snapshot of ParamValues as a preset. Host serializes to JSON; on load, restores into ParamValues. Extension doesn't manage presets. Just declare ParamSpec and accept ParamValues — save/load comes free.
