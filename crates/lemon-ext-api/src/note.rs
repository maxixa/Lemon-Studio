//! Note data and the two note-domain traits.

use crate::context::{GenContext, InputNeeds};
use crate::error::ExtResult;
use crate::instrument::InstrumentHint;
use crate::params::{ParamSpec, ParamValues};
use crate::ui::UiSpec;

#[non_exhaustive]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Note {
    pub start_tick: u32,
    pub length_ticks: u32,
    pub pitch: u8,
    pub velocity: u8,
    pub channel: u8,
    pub flags: NoteFlags,
}

impl Note {
    pub fn new(start_tick: u32, length_ticks: u32, pitch: u8, velocity: u8) -> Self {
        Self { start_tick, length_ticks, pitch: pitch.min(127), velocity: velocity.min(127),
               channel: 0, flags: NoteFlags::empty() }
    }
    pub fn end_tick(&self) -> u32 { self.start_tick.saturating_add(self.length_ticks) }
    pub fn is_in(&self, start: u32, end: u32) -> bool {
        self.start_tick < end && self.end_tick() > start
    }
}

bitflags::bitflags! {
    #[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
    pub struct NoteFlags: u8 {
        const LEGATO     = 1 << 0;
        const PORTAMENTO = 1 << 1;
        const MUTED      = 1 << 2;
        const SLIDE      = 1 << 3;
    }
}

// ─────────────────────────────────────────────────────────────
// NoteEditor
// ─────────────────────────────────────────────────────────────

/// Transforms existing notes (quantize, humanize, scale-lock, phase-copy).
///
/// `edit` MUST be pure: same (notes, params) → same output. No I/O, no threads,
/// no allocations-per-sample in a loop; the host calls it on the UI worker
/// thread with a 2s soft / 5s hard budget.
pub trait NoteEditor: Send + Sync + 'static {
    fn id(&self) -> &'static str;
    fn name(&self) -> &'static str;
    fn description(&self) -> &'static str { "" }
    fn version(&self) -> &'static str { "0.1.0" }
    fn params(&self) -> &'static [ParamSpec];
    fn ui(&self) -> UiSpec { UiSpec::empty() }
    fn needs(&self) -> InputNeeds { InputNeeds::TARGET_RANGE }
    fn edit(&self, notes: &[Note], params: &ParamValues) -> ExtResult<Vec<Note>>;
    fn accepts_empty(&self) -> bool { false }
}

// ─────────────────────────────────────────────────────────────
// NoteGenerator
// ─────────────────────────────────────────────────────────────

/// Produces new notes from context. `generate` MUST be pure and MUST use only
/// `ctx.seed` for any randomness (via `ExtRng` or any SplitMix64-derived RNG).
pub trait NoteGenerator: Send + Sync + 'static {
    fn id(&self) -> &'static str;
    fn name(&self) -> &'static str;
    fn description(&self) -> &'static str { "" }
    fn version(&self) -> &'static str { "0.1.0" }
    fn params(&self) -> &'static [ParamSpec];
    fn ui(&self) -> UiSpec { UiSpec::empty() }
    fn needs(&self) -> InputNeeds { InputNeeds::TARGET_RANGE }
    fn generate(&self, ctx: &GenContext, params: &ParamValues) -> ExtResult<NoteGeneratorOutput>;
    fn max_output_notes(&self) -> usize { 100_000 }
}

#[non_exhaustive]
#[derive(Debug, Clone, Default)]
pub struct NoteGeneratorOutput {
    /// Notes placed in the target track/pattern.
    pub primary: Vec<Note>,
    /// Optional extra tracks — call-and-response, harmony, counterpoint.
    pub secondary: Vec<SecondaryTrack>,
}

impl NoteGeneratorOutput {
    pub fn new(primary: Vec<Note>) -> Self { Self { primary, secondary: Vec::new() } }
    pub fn with_secondary(mut self, s: SecondaryTrack) -> Self { self.secondary.push(s); self }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub struct SecondaryTrack {
    pub target: SecondaryTarget,
    pub pattern_idx: usize,
    pub notes: Vec<Note>,
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub enum SecondaryTarget {
    /// Write into an existing track (identified by `GenContext.references[i].track_idx`).
    ExistingTrack { idx: usize },
    /// Ask the host to create a new track (with name + instrument) and fill it.
    NewTrack { name: String, instrument: InstrumentHint, color_hint: Option<u32> },
}
