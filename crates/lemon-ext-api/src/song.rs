//! Multi-track song sketches and the SongGenerator trait.

use crate::context::{SongContext, TimeSig};
use crate::error::ExtResult;
use crate::instrument::InstrumentHint;
use crate::note::Note;
use crate::params::{ParamSpec, ParamValues};
use crate::ui::UiSpec;

bitflags::bitflags! {
    #[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
    pub struct SongCapabilities: u8 {
        const PATTERNS    = 1 << 0;
        const ARRANGEMENT = 1 << 1;
    }
}

impl SongCapabilities {
    pub const BOTH: Self = Self::PATTERNS.union(Self::ARRANGEMENT);
}

pub trait SongGenerator: Send + Sync + 'static {
    fn id(&self) -> &'static str;
    fn name(&self) -> &'static str;
    fn description(&self) -> &'static str { "" }
    fn version(&self) -> &'static str { "0.1.0" }
    fn params(&self) -> &'static [ParamSpec];
    fn ui(&self) -> UiSpec;
    fn capabilities(&self) -> SongCapabilities;
    fn generate(&self, ctx: &SongContext, params: &ParamValues) -> ExtResult<SongSketch>;
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct SongSketch {
    pub tempo: f64,
    pub time_sig: TimeSig,
    pub ppq: u32,
    pub tracks: Vec<TrackSketch>,
    pub arrangement: Option<ArrangementSketch>,
    pub summary: String,
}

impl SongSketch {
    pub fn new(tempo: f64, time_sig: TimeSig, ppq: u32) -> Self {
        Self { tempo, time_sig, ppq, tracks: Vec::new(), arrangement: None, summary: String::new() }
    }
    pub fn with_track(mut self, t: TrackSketch) -> Self { self.tracks.push(t); self }
    pub fn with_arrangement(mut self, a: ArrangementSketch) -> Self { self.arrangement = Some(a); self }
    pub fn with_summary(mut self, s: impl Into<String>) -> Self { self.summary = s.into(); self }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct TrackSketch {
    pub name: String,
    pub instrument: InstrumentHint,
    pub color_hint: Option<u32>,
    pub patterns: Vec<PatternSketch>,
    pub volume: f32,   // 0..=1, default 1.0
    pub pan: f32,      // -1..=1, default 0.0
    pub muted: bool,
}

impl TrackSketch {
    pub fn new(name: impl Into<String>, instrument: InstrumentHint) -> Self {
        Self { name: name.into(), instrument, color_hint: None, patterns: Vec::new(),
               volume: 1.0, pan: 0.0, muted: false }
    }
    pub fn with_pattern(mut self, p: PatternSketch) -> Self { self.patterns.push(p); self }
    pub fn with_color(mut self, argb: u32) -> Self { self.color_hint = Some(argb); self }
    pub fn with_mix(mut self, vol: f32, pan: f32) -> Self {
        self.volume = vol.clamp(0.0, 1.0); self.pan = pan.clamp(-1.0, 1.0); self
    }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct PatternSketch {
    pub name: String,
    pub length_ticks: u32,
    pub notes: Vec<Note>,
}

impl PatternSketch {
    pub fn new(name: impl Into<String>, length_ticks: u32) -> Self {
        Self { name: name.into(), length_ticks, notes: Vec::new() }
    }
    pub fn with_notes(mut self, notes: Vec<Note>) -> Self { self.notes = notes; self }
}

#[non_exhaustive]
#[derive(Debug, Clone, Default)]
pub struct ArrangementSketch {
    pub clips: Vec<ClipRef>,
    pub sections: Vec<SectionLabel>,
}

impl ArrangementSketch {
    pub fn new() -> Self { Self::default() }
    pub fn with_clip(mut self, c: ClipRef) -> Self { self.clips.push(c); self }
    pub fn with_section(mut self, s: SectionLabel) -> Self { self.sections.push(s); self }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct ClipRef {
    pub track_idx: usize,
    pub pattern_idx: usize,
    pub start_bar: u32,
    pub length_bars: u32,
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct SectionLabel {
    pub name: String,           // "Intro", "Verse 1", "Chorus", "Bridge"
    pub start_bar: u32,
    pub length_bars: u32,
    pub color_hint: Option<u32>,
}

impl SectionLabel {
    pub fn new(name: impl Into<String>, start_bar: u32, length_bars: u32) -> Self {
        Self { name: name.into(), start_bar, length_bars, color_hint: None }
    }
}
