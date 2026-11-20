
NoteGenerator

Produce new notes from context. Pure function. Multi-output supported.

Trait

```rust
pub trait NoteGenerator: Send + Sync + 'static {
    fn id(&self) -> &'static str;
    fn name(&self) -> &'static str;
    fn params(&self) -> &'static [ParamSpec];
    fn ui(&self) -> UiSpec;
    fn needs(&self) -> InputNeeds;                 // bitflags
    fn generate(&self, ctx: &GenContext, params: &ParamValues)
        -> ExtResult<NoteGeneratorOutput>;
    fn max_output_notes(&self) -> usize;           // default 100_000
}
```

InputNeeds — declare what you consume

```rust
bitflags InputNeeds: u8 {
    const TARGET_RANGE = 1 << 0;   // where output goes (always set in practice)
    const HELD_NOTES   = 1 << 1;   // currently-held notes (for arpeggiator)
    const REFERENCES   = 1 << 2;   // other tracks' notes (for solo guitar)
}
```

The host shows UI based on needs:

· REFERENCES set → "Reference track" picker appears
· HELD_NOTES set → requires user to have keys held
· Combinations allowed (e.g. arpeggiator over chords → HELD_NOTES | REFERENCES)

Worked example — solo guitar following chords

```rust
use lemon_ext_api::*;

struct SoloGuitar;

impl NoteGenerator for SoloGuitar {
    fn id(&self) -> &'static str { "solo-guitar" }
    fn name(&self) -> &'static str { "Solo Guitar over Chords" }
    fn params(&self) -> &'static [ParamSpec] {
        static P: &[ParamSpec] = &[
            ParamSpec::new("density", "Note density", 0.1, 1.0, 0.6),
            ParamSpec::new("octave", "Octave range", 1.0, 3.0, 2.0),
        ];
        P
    }
    fn needs(&self) -> InputNeeds {
        InputNeeds::TARGET_RANGE | InputNeeds::REFERENCES
    }

    fn generate(&self, ctx: &GenContext, p: &ParamValues)
        -> ExtResult<NoteGeneratorOutput>
    {
        // Reference track holds the chord progression — locked input.
        let chords = ctx.references.first()
            .ok_or_else(|| ExtError::InvalidInput {
                reason: "pick a reference (chords) track".into(),
            })?;

        let density = p.f32_or("density", 0.6);
        let octave_range = p.f32_or("octave", 2.0);
        let mut rng = ExtRng::new(ctx.seed);

        // Simulate a solo: for each chord, emit N scale-notes.
        let mut notes = Vec::new();
        for chord in &chords.notes {
            let scale = ctx.scale.unwrap_or(Scale::new(0, ScaleMode::Minor));
            let hits = (chord.length_ticks as f32 * density / 4.0) as u32;
            for i in 0..hits {
                let start = chord.start_tick + i * (chord.length_ticks / hits.max(1));
                let base = chord.pitch;
                let jitter = rng.range_i32(0, (12.0 * octave_range) as i32) as u8;
                let pitch = scale.snap(base.saturating_add(jitter), true);
                notes.push(Note::new(start, ctx.ppq / 4, pitch, 90));
            }
        }

        // Optional: add a harmony line one octave below.
        let harmony: Vec<Note> = notes.iter().map(|n| Note {
            pitch: n.pitch.saturating_sub(12), ..*n
        }).collect();

        let output = NoteGeneratorOutput::new(notes)
            .with_secondary(SecondaryTrack {
                target: SecondaryTarget::NewTrack {
                    name: format!("Harmony for {}", chords.track_name),
                    instrument: InstrumentHint::of(InstrumentCategory::Guitar),
                    color_hint: None,
                },
                pattern_idx: 0,
                notes: harmony,
            });

        Ok(output)
    }
}

inventory::submit! { NoteGeneratorReg(&SoloGuitar) }
```

Multi-output semantics

NoteGeneratorOutput.primary → written to ctx.target.track_idx / pattern_idx / start_tick..start_tick+length_ticks.

NoteGeneratorOutput.secondary → for each:

· ExistingTrack { idx } — writes to that track (idx comes from ctx.references[i].track_idx)
· NewTrack { name, instrument, color_hint } — host creates a new track, then writes

max_output_notes() applies to primary + all secondaries combined.

Phase A (Reich phasing) — as a NoteGenerator

```rust
fn needs(&self) -> InputNeeds { InputNeeds::TARGET_RANGE | InputNeeds::REFERENCES }

fn generate(&self, ctx: &GenContext, p: &ParamValues) -> ExtResult<NoteGeneratorOutput> {
    let input = ctx.references.first()
        .ok_or_else(|| ExtError::InvalidInput { reason: "pick a reference track".into() })?;
    let copies = p.i32_or("copies", 2).clamp(1, 8) as usize;
    let offset = p.f32_or("offset_ticks", 1.0) as u32;

    let mut primary = Vec::new();
    for c in 0..copies {
        let shift = c as u32 * offset;
        for n in &input.notes {
            primary.push(Note { start_tick: n.start_tick + shift, ..*n });
        }
    }
    Ok(NoteGeneratorOutput::new(primary))
}
```

Determinism

Same (ctx, params) → same output. Use ExtRng::new(ctx.seed) for any randomness.
