//! Reference extension — quantizes note starts to a grid.

use lemon_ext_api::*;

struct Quantize;

impl NoteEditor for Quantize {
    fn id(&self) -> &'static str { "quantize" }
    fn name(&self) -> &'static str { "Quantize" }
    fn description(&self) -> &'static str { "Snap note starts to a grid" }

    fn params(&self) -> &'static [ParamSpec] {
        static P: &[ParamSpec] = &[
            ParamSpec::new("grid", "Grid size (ppq units)", 1.0, 96.0, 24.0),
        ];
        P
    }

    fn edit(&self, notes: &[Note], p: &ParamValues) -> ExtResult<Vec<Note>> {
        let grid = p.f32_or("grid", 24.0).round() as u32;
        if grid == 0 {
            return Err(ExtError::InvalidParams {
                field: "grid",
                reason: "must be >= 1".into(),
            });
        }
        Ok(notes
            .iter()
            .map(|n| Note {
                start_tick: ((n.start_tick + grid / 2) / grid) * grid,
                ..*n
            })
            .collect())
    }
}

inventory::submit! { NoteEditorReg(&Quantize) }
