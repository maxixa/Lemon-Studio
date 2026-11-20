//! Extension API for Lemon Studio.
//!
//! Three trait families cover the note-domain extensions:
//! - [`NoteEditor`] transforms existing notes (quantize, humanize, scale-lock)
//! - [`NoteGenerator`] produces new notes from context (arpeggiator, solo)
//! - [`SongGenerator`] produces full multi-track sketches (lo-fi beat, arrangement)
//!
//! Extensions live in their own crate, depend on `lemon-ext-api`, register
//! themselves via `inventory::submit!`, and are picked up automatically at
//! build time. See `docs/ext-api/` for the full guide.
//!
//! ## Stability
//! This crate promises semver 1.x source compatibility. Additive changes
//! (new traits, new builder methods, new widget kinds) happen in minor
//! versions. Breaking changes require 2.0 with a 6-month deprecation.

pub mod error;
pub mod params;
pub mod context;
pub mod instrument;
pub mod note;
pub mod song;
pub mod ui;
pub mod registry;
pub mod rng;

pub use error::*;
pub use params::*;
pub use context::*;
pub use instrument::*;
pub use note::*;
pub use song::*;
pub use ui::*;
pub use registry::*;
pub use rng::*;

/// Semver of this crate's API, e.g. `"1.0.0"`.
pub const EXT_API_VERSION: &str = "1.0.0";
pub const EXT_API_MAJOR: u32 = 1;
pub const EXT_API_MINOR: u32 = 0;
pub const EXT_API_PATCH: u32 = 0;
