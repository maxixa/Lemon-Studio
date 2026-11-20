//! Auto-discovery via the `inventory` crate. Extensions register one line:
//!
//! ```ignore
//! inventory::submit! { NoteEditorReg(&QuantizeEditor) }
//! ```

use crate::note::{NoteEditor, NoteGenerator};
use crate::song::SongGenerator;

pub struct NoteEditorReg(pub &'static dyn NoteEditor);
pub struct NoteGeneratorReg(pub &'static dyn NoteGenerator);
pub struct SongGeneratorReg(pub &'static dyn SongGenerator);

inventory::collect!(NoteEditorReg);
inventory::collect!(NoteGeneratorReg);
inventory::collect!(SongGeneratorReg);

pub fn all_note_editors() -> impl Iterator<Item = &'static dyn NoteEditor> {
    inventory::iter::<NoteEditorReg>.into_iter().map(|r| r.0)
}
pub fn all_note_generators() -> impl Iterator<Item = &'static dyn NoteGenerator> {
    inventory::iter::<NoteGeneratorReg>.into_iter().map(|r| r.0)
}
pub fn all_song_generators() -> impl Iterator<Item = &'static dyn SongGenerator> {
    inventory::iter::<SongGeneratorReg>.into_iter().map(|r| r.0)
}
