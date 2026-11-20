
UI Spec

Declarative UI — host renders generically. Escape hatch (custom Compose) is rare.

UiSpec

```rust
UiSpec {
  sections: Vec<Section>,
  preview: PreviewKind,
  primary_action: &'static str,  // "Apply", "Generate", "Phase", "Run"
  estimated_ms: u32,             // hint; 0 = unknown
}
```

Widgets

Widget Use for Fields
Slider continuous numeric min, max, step, default, unit
Spinner integer 0..N min, max, default
Dropdown enumerated choices options: &[DropdownOption], default_idx
Checkbox boolean default
Text short strings default, max_len
Range min/max pair (rare) min, max, step, defaults

Field

```rust
Field {
  id: &'static str,           // matches ParamSpec id
  label: &'static str,
  tooltip: Option<&'static str>,
  widget: WidgetKind,
  visible_if: Option<Condition>,  // conditional rendering
}
```

id must match a ParamSpec.id. The host uses the id to route slider values back to ParamValues. A mismatch = slider does nothing.

Conditions

```rust
Condition { field_id: "mode", equals: ParamValue::Text("custom".into()) }
```

Show/hide a field based on another field's value. Enables contextual UI (e.g., triplet toggle visible only when grid = 1/8).

PreviewKind

Kind Host renders
None No preview (parameter-only)
Notes Piano-roll preview of returned notes
Timeline Bar grid preview of SongSketch.tracks + arrangement.sections
Custom(CustomUiId) Kotlin screen registered under that id (see 06)

Layout rules (soft)

· ≤ 4 sections per spec
· ≤ 8 fields per section
· Action button label matches primary_action — "Generate" for generative, "Apply" for editors
· estimated_ms: set it if you know (> 100ms = shows progress spinner)

Builder pattern

```rust
UiSpec::empty()
    .with_preview(PreviewKind::Timeline)
    .with_action("Generate")
    .with_estimated_ms(500)
    .with_section(
        Section::new("Main")
            .with_field(Field::new("tempo", "Tempo", WidgetKind::Spinner {
                min: 60, max: 180, default: 120,
            }))
            .with_field(Field::new("style", "Style", WidgetKind::Dropdown {
                options: &[/* ... */],
                default_idx: 0,
            }))
    )
```

Host renders a form. Extension never touches Android UI APIs.
