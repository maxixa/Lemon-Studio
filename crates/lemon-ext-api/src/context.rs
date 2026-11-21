//! Inputs handed to extensions: timing, selection, references, scale, seed.

use crate::instrument::InstrumentCategory;
use crate::note::Note;

#[derive(Debug, Clone, Copy)]
pub struct TimeSig {
    pub numerator: u8,
    pub denominator: u8,
}

impl TimeSig {
    pub const fn new(numerator: u8, denominator: u8) -> Self { Self { numerator, denominator } }
    pub const COMMON: Self = Self::new(4, 4);
    pub fn ticks_per_bar(&self, ppq: u32) -> u32 {
        ((ppq as u64 * 4 * self.numerator as u64) / self.denominator as u64) as u32
    }
}

#[non_exhaustive]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScaleMode {
    Major, Minor, Dorian, Phrygian, Lydian, Mixolydian, Locrian,
    HarmonicMinor, MelodicMinor,
    PentatonicMajor, PentatonicMinor,
    Blues, Chromatic,
}

impl ScaleMode {
    pub fn intervals(&self) -> &'static [u8] {
        match self {
            Self::Major           => &[0, 2, 4, 5, 7, 9, 11],
            Self::Minor           => &[0, 2, 3, 5, 7, 8, 10],
            Self::Dorian          => &[0, 2, 3, 5, 7, 9, 10],
            Self::Phrygian        => &[0, 1, 3, 5, 7, 8, 10],
            Self::Lydian          => &[0, 2, 4, 6, 7, 9, 11],
            Self::Mixolydian      => &[0, 2, 4, 5, 7, 9, 10],
            Self::Locrian         => &[0, 1, 3, 5, 6, 8, 10],
            Self::HarmonicMinor   => &[0, 2, 3, 5, 7, 8, 11],
            Self::MelodicMinor    => &[0, 2, 3, 5, 7, 9, 11],
            Self::PentatonicMajor => &[0, 2, 4, 7, 9],
            Self::PentatonicMinor => &[0, 3, 5, 7, 10],
            Self::Blues           => &[0, 3, 5, 6, 7, 10],
            Self::Chromatic       => &[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub struct Scale {
    pub root: u8,   // 0..=11, C=0
    pub mode: ScaleMode,
}

impl Scale {
    pub fn new(root: u8, mode: ScaleMode) -> Self { Self { root: root % 12, mode } }
    pub fn contains(&self, pitch: u8) -> bool {
        let pc = ((pitch as i32 - self.root as i32).rem_euclid(12)) as u8;
        self.mode.intervals().contains(&pc)
    }
    /// Nearest in-scale pitch (ties prefer `up`).
    pub fn snap(&self, pitch: u8, up: bool) -> u8 {
        for delta in 0..=6u8 {
            if up {
                let p = pitch.saturating_add(delta);
                if self.contains(p) { return p; }
            } else {
                let p = pitch.saturating_sub(delta);
                if self.contains(p) { return p; }
            }
        }
        pitch
    }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct TargetRange {
    pub track_idx: usize,
    pub pattern_idx: usize,
    pub start_tick: u32,
    pub length_ticks: u32,
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct ReferenceTrack {
    pub track_idx: usize,
    pub pattern_idx: usize,
    pub track_name: String,
    pub instrument_category: Option<InstrumentCategory>,
    pub notes: Vec<Note>,
}

impl TargetRange {
    pub fn new(track_idx: usize, pattern_idx: usize, start_tick: u32, length_ticks: u32) -> Self {
        Self { track_idx, pattern_idx, start_tick, length_ticks }
    }
}

impl ReferenceTrack {
    pub fn new(track_idx: usize, pattern_idx: usize, track_name: impl Into<String>, notes: Vec<Note>) -> Self {
        Self { track_idx, pattern_idx, track_name: track_name.into(), instrument_category: None, notes }
    }
    pub fn with_instrument_category(mut self, cat: InstrumentCategory) -> Self {
        self.instrument_category = Some(cat); self
    }
}



bitflags::bitflags! {
    /// Bitflags — combine freely.
    #[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
    pub struct InputNeeds: u8 {
        const TARGET_RANGE = 1 << 0;
        const HELD_NOTES   = 1 << 1;
        const REFERENCES   = 1 << 2;
    }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct GenContext {
    pub tempo_bpm: f64,
    pub time_sig: TimeSig,
    pub ppq: u32,
    pub target: TargetRange,
    pub held_notes: Vec<Note>,
    pub references: Vec<ReferenceTrack>,
    pub scale: Option<Scale>,
    pub seed: u64,
    pub pattern_len: u32,
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct SongContext {
    pub tempo_hint: Option<f64>,
    pub time_sig: TimeSig,
    pub key: Option<Scale>,
    pub length_bars: u32,
    pub ppq: u32,
    pub seed: u64,
    pub max_tracks: u32,
    pub references: Vec<ReferenceTrack>,
}

impl GenContext {
    /// Minimal constructor. Use `.with_*()` builders to fill optional fields.
    pub fn new(ppq: u32, target: TargetRange, seed: u64) -> Self {
        let bar_ticks = ((ppq as u64 * 4) / 4) as u32; // 4/4 default
        Self {
            tempo_bpm: 120.0,
            time_sig: TimeSig::COMMON,
            ppq,
            target,
            held_notes: vec![],
            references: vec![],
            scale: None,
            seed,
            pattern_len: bar_ticks * 4,
        }
    }
    pub fn with_tempo(mut self, bpm: f64) -> Self { self.tempo_bpm = bpm; self }
    pub fn with_time_sig(mut self, ts: TimeSig) -> Self { self.time_sig = ts; self }
    pub fn with_scale(mut self, s: Scale) -> Self { self.scale = Some(s); self }
    pub fn with_held_notes(mut self, notes: Vec<Note>) -> Self { self.held_notes = notes; self }
    pub fn with_references(mut self, refs: Vec<ReferenceTrack>) -> Self { self.references = refs; self }
    pub fn with_pattern_len(mut self, ticks: u32) -> Self { self.pattern_len = ticks; self }
}

impl SongContext {
    /// Minimal constructor. Use `.with_*()` builders for optional fields.
    pub fn new(ppq: u32, seed: u64) -> Self {
        Self {
            tempo_hint: None,
            time_sig: TimeSig::COMMON,
            key: None,
            length_bars: 16,
            ppq,
            seed,
            max_tracks: 16,
            references: vec![],
        }
    }
    pub fn with_tempo_hint(mut self, bpm: f64) -> Self { self.tempo_hint = Some(bpm); self }
    pub fn with_time_sig(mut self, ts: TimeSig) -> Self { self.time_sig = ts; self }
    pub fn with_key(mut self, s: Scale) -> Self { self.key = Some(s); self }
    pub fn with_length_bars(mut self, bars: u32) -> Self { self.length_bars = bars; self }
    pub fn with_max_tracks(mut self, n: u32) -> Self { self.max_tracks = n; self }
    pub fn with_references(mut self, refs: Vec<ReferenceTrack>) -> Self { self.references = refs; self }
}


