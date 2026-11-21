//! Lo-fi beat generator — reference `SongGenerator` extension.
//!
//! Generates:
//! - Drums (kick / snare / hats) — 4-bar loop pattern
//! - Bass (root notes) — follows a i-VI-III-VII minor progression
//! - Keys (chord voicings) — same progression, voice-led
//!
//! Arrangement: 16 bars total → Intro (drums) → Verse (all) → Outro (drums + keys)
//! with `SectionLabel`s. Deterministic via `ctx.seed`.
//!
//! Copy this crate as a template for your own song generators.

use lemon_ext_api::*;

struct LofiBeat;

impl SongGenerator for LofiBeat {
    fn id(&self) -> &'static str { "song-gen-lofi" }
    fn name(&self) -> &'static str { "Lo-fi Beat" }
    fn description(&self) -> &'static str {
        "Drums, bass, and keys over a minor progression. Intro / Verse / Outro."
    }
    fn version(&self) -> &'static str { "0.1.0" }

    fn params(&self) -> &'static [ParamSpec] {
        static P: &[ParamSpec] = &[
            ParamSpec::new("tempo", "Tempo (BPM)", 60.0, 100.0, 78.0).unit("BPM"),
            ParamSpec::new("bars", "Song length (bars)", 8.0, 32.0, 16.0),
            ParamSpec::new("swing", "Swing amount", 0.0, 0.3, 0.15),
        ];
        P
    }

    fn ui(&self) -> UiSpec {
        UiSpec::new()
            .with_preview(PreviewKind::Timeline)
            .with_action("Generate")
            .with_estimated_ms(200)
            .with_section(
                Section::new("Song")
                    .with_field(Field::new("tempo", "Tempo", WidgetKind::Slider {
                        min: 60.0, max: 100.0, step: 1.0, default: 78.0, unit: "BPM",
                    }))
                    .with_field(Field::new("bars", "Length", WidgetKind::Spinner {
                        min: 8, max: 32, default: 16,
                    }))
                    .with_field(Field::new("swing", "Swing", WidgetKind::Slider {
                        min: 0.0, max: 0.3, step: 0.01, default: 0.15, unit: "",
                    }))
            )
    }

    fn capabilities(&self) -> SongCapabilities {
        SongCapabilities::PATTERNS | SongCapabilities::ARRANGEMENT
    }

    fn generate(&self, ctx: &SongContext, p: &ParamValues) -> ExtResult<SongSketch> {
        let tempo = p.f32_or("tempo", 78.0) as f64;
        let bars = p.i32_or("bars", 16).clamp(8, 32) as u32;
        let swing = p.f32_or("swing", 0.15).clamp(0.0, 0.3);

        if !(60.0..=100.0).contains(&(tempo as f32)) {
            return Err(ExtError::InvalidParams { field: "tempo", reason: "60-100".into() });
        }

        let ppq = ctx.ppq;
        let bar_ticks = ctx.time_sig.ticks_per_bar(ppq);
        let mut rng = ExtRng::new(ctx.seed);

        // Chord progression: i - VI - III - VII in A minor (Am - F - C - G)
        let roots: [(u8, [u8; 3]); 4] = [
            (45, [45, 48, 52]), // Am  (A2, C3, E3)
            (41, [41, 45, 48]), // F   (F2, A2, C3)
            (48, [48, 52, 55]), // C   (C3, E3, G3)
            (43, [43, 47, 50]), // G   (G2, B2, D3)
        ];

        // ── Drums: 4-bar loop (k on 1 & 3, sn on 2 & 4, hats on 8ths with swing)
        let swing_offset = (swing * (ppq as f32 / 4.0)) as u32;
        let mut drum_notes = Vec::new();
        for bar in 0..4u32 {
            let base = bar * bar_ticks;
            // Kick on beats 1 and 3
            drum_notes.push(Note::new(base, ppq / 4, 36, 110));
            drum_notes.push(Note::new(base + 2 * ppq, ppq / 4, 36, 100));
            // Snare on beats 2 and 4
            drum_notes.push(Note::new(base + ppq, ppq / 4, 38, 100));
            drum_notes.push(Note::new(base + 3 * ppq, ppq / 4, 38, 100));
            // Hats on 8ths with swing
            for eighth in 0..8u32 {
                let pos = base + eighth * (ppq / 2);
                let pos = if eighth % 2 == 1 { pos + swing_offset } else { pos };
                let vel = if eighth % 2 == 0 { 80 } else { 60 };
                drum_notes.push(Note::new(pos, ppq / 8, 42, vel));
            }
        }

        // ── Bass: one root note per bar, following progression
        let mut bass_notes = Vec::new();
        for bar in 0..4u32 {
            let (root, _) = roots[(bar as usize) % 4];
            let start = bar * bar_ticks;
            // Root held for 3 beats, then a passing note
            bass_notes.push(Note::new(start, 3 * ppq, root, 100));
            bass_notes.push(Note::new(start + 3 * ppq, ppq, root, 90));
        }

        // ── Keys: chord voicing per bar, slight velocity variation via rng
        let mut keys_notes = Vec::new();
        for bar in 0..4u32 {
            let (_, chord) = roots[(bar as usize) % 4];
            let start = bar * bar_ticks;
            for (i, &pitch) in chord.iter().enumerate() {
                let vel = 70 + rng.range_i32(-5, 5);
                keys_notes.push(Note::new(
                    start + (i as u32) * (ppq / 32),
                    bar_ticks - ppq / 8,
                    pitch,
                    vel.clamp(0, 127) as u8,
                ));
            }
        }

        // ── Build sketch
        let drums = TrackSketch::new("Drums", InstrumentHint::of(InstrumentCategory::Drums))
            .with_pattern(PatternSketch::new("beat", 4 * bar_ticks).with_notes(drum_notes))
            .with_color(0xFF6B5B95)
            .with_mix(0.9, 0.0);

        let bass = TrackSketch::new("Bass", InstrumentHint::of(InstrumentCategory::Bass))
            .with_pattern(PatternSketch::new("bass", 4 * bar_ticks).with_notes(bass_notes))
            .with_color(0xFF88B04B)
            .with_mix(0.85, 0.0);

        let keys = TrackSketch::new("Keys", InstrumentHint::of(InstrumentCategory::Keys))
            .with_pattern(PatternSketch::new("chords", 4 * bar_ticks).with_notes(keys_notes))
            .with_color(0xFFF7CAC9)
            .with_mix(0.7, 0.0);

        // ── Arrangement: 16 bars (intro 4 / verse 8 / outro 4)
        // Drums full song, bass verse only, keys intro+verse+outro
        let verse_start = 4;
        let outro_start = 12;

        let arrangement = ArrangementSketch::new()
            // Drums play from bar 0 through end
            .with_clip(ClipRef { track_idx: 0, pattern_idx: 0, start_bar: 0, length_bars: bars })
            // Bass enters at verse, plays through verse + outro (or just verse if short)
            .with_clip(ClipRef {
                track_idx: 1, pattern_idx: 0,
                start_bar: verse_start,
                length_bars: bars.saturating_sub(verse_start),
            })
            // Keys play intro + verse + outro
            .with_clip(ClipRef { track_idx: 2, pattern_idx: 0, start_bar: 0, length_bars: bars })
            // Section labels
            .with_section(SectionLabel::new("Intro",  0, verse_start.min(bars)))
            .with_section(SectionLabel::new("Verse",  verse_start, (outro_start - verse_start).min(bars.saturating_sub(verse_start))))
            .with_section(SectionLabel::new("Outro",  outro_start.min(bars), bars.saturating_sub(outro_start)));

        Ok(SongSketch::new(tempo, ctx.time_sig, ppq)
            .with_track(drums)
            .with_track(bass)
            .with_track(keys)
            .with_arrangement(arrangement)
            .with_summary(format!(
                "Lo-fi · {} bars · {} BPM · swing {:.0}% · A minor",
                bars, tempo as i32, swing * 100.0
            )))
    }
}

inventory::submit! { SongGeneratorReg(&LofiBeat) }

// ═══════════════════════════════════════════════════════════════
// Tests — prove determinism, structure, and performance
// ═══════════════════════════════════════════════════════════════

#[cfg(test)]
mod tests {
    use super::*;

    fn ctx(seed: u64) -> SongContext {
        SongContext::new(96, seed).with_length_bars(16)
    }

    fn params() -> ParamValues {
        let mut p = ParamValues::new();
        p.set("tempo", ParamValue::Float(78.0));
        p.set("bars",  ParamValue::Int(16));
        p.set("swing", ParamValue::Float(0.15));
        p
    }

    #[test]
    fn determinism_same_seed() {
        let lofi = LofiBeat;
        let a = lofi.generate(&ctx(42), &params()).unwrap();
        let b = lofi.generate(&ctx(42), &params()).unwrap();
        // Compare via summary + track count + total notes
        let count = |s: &SongSketch| -> usize {
            s.tracks.iter().map(|t| t.patterns.iter().map(|p| p.notes.len()).sum::<usize>()).sum()
        };
        assert_eq!(a.summary, b.summary);
        assert_eq!(a.tracks.len(), b.tracks.len());
        assert_eq!(count(&a), count(&b));
        // Note-by-note determinism
        for (ta, tb) in a.tracks.iter().zip(b.tracks.iter()) {
            for (pa, pb) in ta.patterns.iter().zip(tb.patterns.iter()) {
                assert_eq!(pa.notes.len(), pb.notes.len());
                for (na, nb) in pa.notes.iter().zip(pb.notes.iter()) {
                    assert_eq!((na.start_tick, na.pitch, na.velocity),
                               (nb.start_tick, nb.pitch, nb.velocity));
                }
            }
        }
    }

    #[test]
    fn different_seeds_differ() {
        let lofi = LofiBeat;
        let a = lofi.generate(&ctx(1), &params()).unwrap();
        let b = lofi.generate(&ctx(2), &params()).unwrap();
        // Keys track has rng-jittered velocity — should differ
        let keys_a = &a.tracks[2].patterns[0].notes;
        let keys_b = &b.tracks[2].patterns[0].notes;
        let differs = keys_a.iter().zip(keys_b.iter()).any(|(x, y)| x.velocity != y.velocity);
        assert!(differs, "keys velocity should jitter per seed");
    }

    #[test]
    fn structure_correct() {
        let lofi = LofiBeat;
        let s = lofi.generate(&ctx(42), &params()).unwrap();
        assert_eq!(s.tracks.len(), 3);
        assert_eq!(s.tracks[0].name, "Drums");
        assert_eq!(s.tracks[1].name, "Bass");
        assert_eq!(s.tracks[2].name, "Keys");
        assert_eq!(s.time_sig.numerator, 4);

        let arr = s.arrangement.as_ref().expect("arrangement present");
        assert_eq!(arr.sections.len(), 3);
        assert_eq!(arr.sections[0].name, "Intro");
        assert_eq!(arr.sections[1].name, "Verse");
        assert_eq!(arr.sections[2].name, "Outro");
    }

    #[test]
    fn completes_within_budget() {
        let lofi = LofiBeat;
        let mut p = params();
        p.set("bars", ParamValue::Int(32));
        let start = std::time::Instant::now();
        let _ = lofi.generate(&ctx(42), &p).unwrap();
        assert!(start.elapsed().as_millis() < 500, "should be < 500ms even at max bars");
    }
}
