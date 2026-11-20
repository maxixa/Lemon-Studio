//! Portable instrument hints. Extensions declare a *category* and Lemon
//! maps it to whatever instrument ships today (and whatever ships tomorrow).

#[non_exhaustive]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InstrumentCategory {
    Drums, Bass, Lead, Pad, Keys, Guitar, Strings, Brass, Woodwind, Vocal, Percussion, Fx, Other,
}

impl InstrumentCategory {
    pub fn display_name(&self) -> &'static str {
        match self {
            Self::Drums      => "Drums",
            Self::Bass       => "Bass",
            Self::Lead       => "Lead",
            Self::Pad        => "Pad",
            Self::Keys       => "Keys",
            Self::Guitar     => "Guitar",
            Self::Strings    => "Strings",
            Self::Brass      => "Brass",
            Self::Woodwind   => "Woodwind",
            Self::Vocal      => "Vocal",
            Self::Percussion => "Percussion",
            Self::Fx         => "FX",
            Self::Other      => "Other",
        }
    }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct InstrumentHint {
    pub category: InstrumentCategory,
    /// Optional specific preset id. Prefer `None` for portable extensions.
    pub preferred: Option<String>,
}

impl InstrumentHint {
    pub fn of(category: InstrumentCategory) -> Self {
        Self { category, preferred: None }
    }
    pub fn specific(category: InstrumentCategory, preset_id: impl Into<String>) -> Self {
        Self { category, preferred: Some(preset_id.into()) }
    }
}
