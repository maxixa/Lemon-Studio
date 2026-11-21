//! Declarative UI spec. Host renders this generically; no per-extension
//! Kotlin code needed unless the extension opts into `PreviewKind::Custom`.

use crate::params::ParamValue;

#[derive(Debug, Clone)]
pub struct UiSpec {
    pub sections: Vec<Section>,
    pub preview: PreviewKind,
    pub primary_action: &'static str,   // "Apply", "Generate", "Phase"
    pub estimated_ms: u32,              // hint for progress UI; 0 = unknown
}

impl UiSpec {
    pub fn empty() -> Self {
        Self { sections: Vec::new(), preview: PreviewKind::None,
               primary_action: "Apply", estimated_ms: 0 }
    }
    pub fn new() -> Self { Self::empty() }
    pub fn with_section(mut self, s: Section) -> Self { self.sections.push(s); self }
    pub fn with_preview(mut self, p: PreviewKind) -> Self { self.preview = p; self }
    pub fn with_action(mut self, a: &'static str) -> Self { self.primary_action = a; self }
    pub fn with_estimated_ms(mut self, ms: u32) -> Self { self.estimated_ms = ms; self }
}

#[derive(Debug, Clone)]
pub struct Section {
    pub title: &'static str,
    pub fields: Vec<Field>,
    pub collapsed_by_default: bool,
}

impl Section {
    pub fn new(title: &'static str) -> Self {
        Self { title, fields: Vec::new(), collapsed_by_default: false }
    }
    pub fn with_field(mut self, f: Field) -> Self { self.fields.push(f); self }
    pub fn collapsed(mut self) -> Self { self.collapsed_by_default = true; self }
}

#[derive(Debug, Clone)]
pub struct Field {
    pub id: &'static str,
    pub label: &'static str,
    pub tooltip: Option<&'static str>,
    pub widget: WidgetKind,
    pub visible_if: Option<Condition>,
}

impl Field {
    pub fn new(id: &'static str, label: &'static str, widget: WidgetKind) -> Self {
        Self { id, label, tooltip: None, widget, visible_if: None }
    }
    pub fn with_tooltip(mut self, t: &'static str) -> Self { self.tooltip = Some(t); self }
    pub fn visible_when(mut self, c: Condition) -> Self { self.visible_if = Some(c); self }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub enum WidgetKind {
    Slider { min: f32, max: f32, step: f32, default: f32, unit: &'static str },
    Spinner { min: i32, max: i32, default: i32 },
    Dropdown { options: &'static [DropdownOption], default_idx: usize },
    Checkbox { default: bool },
    Text { default: &'static str, max_len: usize },
    Range { min: f32, max: f32, step: f32, default_lo: f32, default_hi: f32 },
}

#[derive(Debug, Clone, Copy)]
pub struct DropdownOption {
    pub id: &'static str,
    pub label: &'static str,
}

#[derive(Debug, Clone)]
pub struct Condition {
    pub field_id: &'static str,
    pub equals: ParamValue,
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub enum PreviewKind {
    None,
    Notes,
    Timeline,
    Custom(CustomUiId),
}

/// Namespaced id (`"my-ext.drum-designer-v1"`). Kotlin registers a
/// `CustomExtScreen` under this id; if missing, host falls back to `Notes`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct CustomUiId(pub &'static str);
