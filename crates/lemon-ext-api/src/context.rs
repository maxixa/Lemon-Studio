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
