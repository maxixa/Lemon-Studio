//! Solo guitar over an existing chord progression.
//!
//! This is a `NoteGenerator` that consumes another track's notes as input.
//! The user picks a reference track (the chords) in the host UI; the host
//! populates `ctx.references[0].notes`; we generate a solo line on top.
//!
//! Demonstrates:
//! - `InputNeeds::REFERENCES` — declares dependency on another track
//! - `GenContext.references` — read-only access to the reference notes
//! - `NoteGeneratorOutput.secondary` — multi-track output (harmony line)
//! - `SecondaryTarget::NewTrack` — host creates the harmony track
//! - `Scale::snap` — snaps generated pitches to the active scale
//!
//! Copy this crate as the template for any "follow existing content" generator:
//! bass following chords, piano comping, melody harmony, call-and-response.

use lemon_ext_api::*;

struct SoloGuitar;

impl NoteGenerator for SoloGuitar {
    fn id(&self) -> &'static str { "solo-guitar" }
    fn name(&self) -> &'static str { "Solo Guitar over Chords" }
    fn description(&self) -> &'static str {
        "Solo melody over an existing chord progression. Optional parallel harmony."
    }
    fn version(&self) -> &'static str { "0.1.0" }

    fn params(&self) -> &'static [ParamSpec] {
        static P: &[ParamSpec] = &[
            ParamSpec::new("density", "Note density", 0.1, 1.0, 0.6),
            ParamSpec::new("octave_range", "Octave range", 1.0, 3.0, 2.0),
            ParamSpec::new("harmony", "Add harmony line", 0.0, 1.0, 1.0)
                .curve(ParamCurve::Discrete),
        ];
        P
    }

    fn ui(&self) -> UiSpec {
        UiSpec::new()
            .with_preview(PreviewKind::Notes)
            .with_action("Generate")
            .with_estimated_ms(80)
            .with_section(
                Section::new("Solo")
                    .with_field(Field::new("density", "Density", WidgetKind::Slider {
                        min: 0.1, max: 1.0, step: 0.05, default: 0.6, unit: "",
                    }))
                    .with_field(Field::new("octave_range", "Octave range", WidgetKind::Slider {
                        min: 1.0, max: 3.0, step: 0.5, default: 2.0, unit: "oct",
                    }))
                    .with_field(Field::new("harmony", "Harmony", WidgetKind::Checkbox {
                        default: true,
                    }))
            )
    }

    fn needs(&self) -> InputNeeds {
        InputNeeds::TARGET_RANGE | InputNeeds::REFERENCES
    }

    fn generate(&self, ctx: &GenContext, p: &ParamValues) -> ExtResult<NoteGeneratorOutput> {
        // Read reference track — required
        let chords = ctx.references.first().ok_or_else(|| ExtError::InvalidInput {
            reason: "pick a reference (chords) track".into(),
        })?;

        if chords.notes.is_empty() {
            return Err(ExtError::InvalidInput {
                reason: "reference track has no notes to follow".into(),
            });
        }

        let density = p.f32_or("density", 0.6).clamp(0.1, 1.0);
        let octave_range = p.f32_or("octave_range", 2.0).clamp(1.0, 3.0);
        let add_harmony = p.f32_or("harmony", 1.0) >= 0.5;

        let scale = ctx.scale.unwrap_or(Scale::new(9, ScaleMode::Minor)); // A minor default
        let mut rng = ExtRng::new(ctx.seed);

        // Sort chord notes chronologically so we walk through the progression
        let mut sorted: Vec<&Note> = chords.notes.iter().collect();
        sorted.sort_by_key(|n| n.start_tick);

        // Solo register: one octave above the top chord note
        let chord_max = sorted.iter().map(|n| n.pitch).max().unwrap_or(60);
        let solo_base = chord_max.saturating_add(12);
        let max_offset = (12.0 * octave_range) as i32;

        // Generate solo line
        let mut primary: Vec<Note> = Vec::new();
        for chord in &sorted {
            let hits = ((chord.length_ticks as f32 * density / 4.0).round() as u32)
                .clamp(1, 8);
            let step = (chord.length_ticks / hits).max(1);

            for i in 0..hits {
                let start = chord.start_tick + i * step;
                let offset = rng.range_i32(-max_offset, max_offset);
                let raw = (solo_base as i32 + offset).clamp(0, 127) as u8;
                let pitch = scale.snap(raw, true);
                let vel = (80 + rng.range_i32(-15, 15)).clamp(20, 127) as u8;
                let len = (step.saturating_sub(step / 8)).max(2);

                primary.push(Note::new(start, len, pitch, vel));
            }
        }

        if primary.is_empty() {
            return Err(ExtError::InvalidOutput {
                reason: "generated no notes — check density/range".into(),
            });
        }

        // Optional harmony: sparse parallel line one octave below
        let secondary = if add_harmony {
            let harmony: Vec<Note> = primary
                .iter()
                .enumerate()
                .filter(|(i, _)| i % 3 == 0)
                .map(|(_, n)| Note {
                    pitch: n.pitch.saturating_sub(12),
                    velocity: (n.velocity as u32 * 70 / 100) as u8,
                    ..*n
                })
                .collect();

            if harmony.is_empty() {
                vec![]
            } else {
                vec![SecondaryTrack {
                    target: SecondaryTarget::NewTrack {
                        name: format!("Harmony ({})", chords.track_name),
                        instrument: InstrumentHint::of(InstrumentCategory::Guitar),
                        color_hint: Some(0xFF8899AA),
                    },
                    pattern_idx: 0,
                    notes: harmony,
                }]
            }
        } else {
            vec![]
        };

        Ok(NoteGeneratorOutput { primary, secondary })
    }
}

inventory::submit! { NoteGeneratorReg(&SoloGuitar) }

// ═══════════════════════════════════════════════════════════════
// Tests — determinism, required refs, structure, performance
// ═══════════════════════════════════════════════════════════════

#[cfg(test)]
mod tests {
    use super::*;

    fn chord_track() -> ReferenceTrack {
        // Am (bar 1) → F (bar 2). PPQ=96, so 1 bar = 384 ticks.
        // Chord notes span 3 beats each (288 ticks), then rest for 1 beat.
        let notes = vec![
            // Am — A2, C3, E3
            Note::new(0, 288, 45, 100),
            Note::new(0, 288, 48, 100),
            Note::new(0, 288, 52, 100),
            // F — F2, A2, C3
            Note::new(384, 288, 41, 100),
            Note::new(384, 288, 45, 100),
            Note::new(384, 288, 48, 100),
        ];
        ReferenceTrack::new(1, 0, "Chords", notes)
    }

    fn ctx_with_chords(seed: u64) -> GenContext {
        let target = TargetRange::new(0, 0, 0, 768); // 2 bars
        GenContext::new(96, target, seed)
            .with_tempo(120.0)
            .with_scale(Scale::new(9, ScaleMode::Minor))
            .with_references(vec![chord_track()])
            .with_pattern_len(768)
    }

    fn params() -> ParamValues {
        let mut p = ParamValues::new();
        p.set("density", ParamValue::Float(0.6));
        p.set("octave_range", ParamValue::Float(2.0));
        p.set("harmony", ParamValue::Float(1.0));
        p
    }

    #[test]
    fn determinism_same_seed() {
        let solo = SoloGuitar;
        let a = solo.generate(&ctx_with_chords(42), &params()).unwrap();
        let b = solo.generate(&ctx_with_chords(42), &params()).unwrap();

        assert_eq!(a.primary.len(), b.primary.len());
        for (na, nb) in a.primary.iter().zip(b.primary.iter()) {
            assert_eq!(
                (na.start_tick, na.pitch, na.velocity),
                (nb.start_tick, nb.pitch, nb.velocity),
                "same seed must produce identical primary notes"
            );
        }
        assert_eq!(a.secondary.len(), b.secondary.len());
        if let (Some(sa), Some(sb)) = (a.secondary.first(), b.secondary.first()) {
            assert_eq!(sa.notes.len(), sb.notes.len());
            for (na, nb) in sa.notes.iter().zip(sb.notes.iter()) {
                assert_eq!((na.start_tick, na.pitch), (nb.start_tick, nb.pitch));
            }
        }
    }

    #[test]
    fn requires_references() {
        let solo = SoloGuitar;
        // No references → must error
        let target = TargetRange::new(0, 0, 0, 384);
        let ctx = GenContext::new(96, target, 42);
        let err = solo.generate(&ctx, &params()).unwrap_err();
        match err {
            ExtError::InvalidInput { .. } => {}
            other => panic!("expected InvalidInput, got {other:?}"),
        }
    }

    #[test]
    fn all_notes_in_scale() {
        let solo = SoloGuitar;
        let output = solo.generate(&ctx_with_chords(42), &params()).unwrap();
        let scale = Scale::new(9, ScaleMode::Minor);

        assert!(!output.primary.is_empty(), "should generate primary notes");
        for n in &output.primary {
            assert!(
                scale.contains(n.pitch),
                "pitch {} not in A minor scale",
                n.pitch
            );
            assert!(n.pitch <= 127);
            assert!(n.velocity > 0);
        }
    }

    #[test]
    fn harmony_is_one_octave_below() {
        let solo = SoloGuitar;
        let output = solo.generate(&ctx_with_chords(42), &params()).unwrap();

        assert_eq!(output.secondary.len(), 1, "should have one harmony track");
        let harmony = &output.secondary[0];
        assert!(harmony.notes.len() < output.primary.len(), "harmony is sparser");

        // Harmony was built from every 3rd primary note (i % 3 == 0).
        // Iteration order is preserved, so harmony[k] corresponds to primary[3k].
        let expected: Vec<&Note> = output
            .primary
            .iter()
            .enumerate()
            .filter(|(i, _)| i % 3 == 0)
            .map(|(_, n)| n)
            .collect();

        assert_eq!(
            harmony.notes.len(),
            expected.len(),
            "harmony should have exactly one note per 3 primary notes"
        );

        for (hn, pn) in harmony.notes.iter().zip(expected.iter()) {
            assert_eq!(
                hn.start_tick, pn.start_tick,
                "harmony mirrors primary timing"
            );
            assert_eq!(
                pn.pitch.saturating_sub(12),
                hn.pitch,
                "harmony should be exactly one octave below its source primary"
            );
            assert_eq!(
                hn.velocity,
                (pn.velocity as u32 * 70 / 100) as u8,
                "harmony velocity is 70% of primary"
            );
        }
    }

    #[test]
    fn completes_within_budget() {
        let solo = SoloGuitar;
        let mut p = params();
        p.set("density", ParamValue::Float(1.0)); // max density
        p.set("octave_range", ParamValue::Float(3.0)); // max range

        // 8-bar context → ~32 chord notes → ~256 solo hits expected
        let notes: Vec<Note> = (0..32)
            .map(|i| Note::new(i * 96, 288, 48, 100))
            .collect();
        let target = TargetRange::new(0, 0, 0, 32 * 96);
        let ctx = GenContext::new(96, target, 42)
            .with_scale(Scale::new(9, ScaleMode::Minor))
            .with_references(vec![ReferenceTrack::new(1, 0, "Chords", notes)]);

        let start = std::time::Instant::now();
        let _ = solo.generate(&ctx, &p).unwrap();
        assert!(
            start.elapsed().as_millis() < 200,
            "should complete < 200ms even at max density"
        );
    }
}
