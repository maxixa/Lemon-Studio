
SongGenerator

Produce a full multi-track sketch. Capability-declared (patterns-only vs. with arrangement).

Trait

```rust
pub trait SongGenerator: Send + Sync + 'static {
    fn id(&self) -> &'static str;
    fn name(&self) -> &'static str;
    fn params(&self) -> &'static [ParamSpec];
    fn ui(&self) -> UiSpec;
    fn capabilities(&self) -> SongCapabilities;
    fn generate(&self, ctx: &SongContext, params: &ParamValues)
        -> ExtResult<SongSketch>;
}

bitflags SongCapabilities: u8 {
    const PATTERNS    = 1 << 0;   // emits TrackSketch.patterns
    const ARRANGEMENT = 1 << 1;   // emits SongSketch.arrangement
}
```

SongSketch structure

```
SongSketch {
  tempo: f64,
  time_sig,
  ppq,
  tracks: [TrackSketch {
    name, instrument: InstrumentHint, color_hint,
    patterns: [PatternSketch { name, length_ticks, notes }],
    volume, pan, muted,
  }],
  arrangement: Option<ArrangementSketch {
    clips: [ClipRef { track_idx, pattern_idx, start_bar, length_bars }],
    sections: [SectionLabel { name, start_bar, length_bars }],  // Phase B
  }>,
  summary: String,
}
```

Worked example — Lo-fi beat, PATTERNS | ARRANGEMENT

```rust
use lemon_ext_api::*;

struct LofiBeat;

impl SongGenerator for LofiBeat {
    fn id(&self) -> &'static str { "lofi-beat" }
    fn name(&self) -> &'static str { "Lo-fi Beat" }
    fn capabilities(&self) -> SongCapabilities { SongCapabilities::BOTH }
    fn params(&self) -> &'static [ParamSpec] {
        static P: &[ParamSpec] = &[
            ParamSpec::new("tempo", "Tempo (BPM)", 60.0, 100.0, 78.0),
            ParamSpec::new("bars", "Song length (bars)", 4.0, 64.0, 16.0),
        ];
        P
    }
    fn ui(&self) -> UiSpec {
        UiSpec::empty()
            .with_preview(PreviewKind::Timeline)
            .with_action("Generate")
    }

    fn generate(&self, ctx: &SongContext, p: &ParamValues) -> ExtResult<SongSketch> {
        let tempo = p.f32_or("tempo", 78.0) as f64;
        let bars = p.i32_or("bars", 16) as u32;
        let mut rng = ExtRng::new(ctx.seed);
        let bar_ticks = ctx.time_sig.ticks_per_bar(ctx.ppq);

        // Drums track: 1 pattern of 1 bar
        let mut drum_notes = Vec::new();
        for beat in 0..4 {
            drum_notes.push(Note::new(beat * ctx.ppq, ctx.ppq / 4, 36, 110)); // kick
            drum_notes.push(Note::new(beat * ctx.ppq + ctx.ppq / 2, ctx.ppq / 4, 42, 70)); // hat
        }
        let drums = TrackSketch::new("Drums", InstrumentHint::of(InstrumentCategory::Drums))
            .with_pattern(PatternSketch::new("beat", bar_ticks).with_notes(drum_notes));

        // Bass track: chord root, one per bar
        let mut bass_notes = Vec::new();
        let roots = [36u8, 36, 43, 43]; // C, C, G, G
        for i in 0..bars {
            let r = roots[(i % roots.len() as u32) as usize];
            bass_notes.push(Note::new(i * bar_ticks, bar_ticks - 96, r, 100));
        }
        let bass = TrackSketch::new("Bass", InstrumentHint::of(InstrumentCategory::Bass))
            .with_pattern(PatternSketch::new("bass", bars * bar_ticks).with_notes(bass_notes));

        // Arrangement: drums loop whole song, bass fills whole song, plus section labels
        let arrangement = ArrangementSketch::new()
            .with_clip(ClipRef { track_idx: 0, pattern_idx: 0, start_bar: 0, length_bars: bars })
            .with_clip(ClipRef { track_idx: 1, pattern_idx: 0, start_bar: 0, length_bars: bars })
            .with_section(SectionLabel::new("Intro",  0, 4))
            .with_section(SectionLabel::new("Verse",  4, 8))
            .with_section(SectionLabel::new("Outro", 12, 4));

        Ok(SongSketch::new(tempo, ctx.time_sig, ctx.ppq)
            .with_track(drums)
            .with_track(bass)
            .with_arrangement(arrangement)
            .with_summary(format!("Lo-fi {} bars @ {} BPM", bars, tempo as i32)))
    }
}

inventory::submit! { SongGeneratorReg(&LofiBeat) }
```

Section labels (Phase B)

SectionLabel names structural regions (Intro, Verse, Chorus, Bridge, Outro). Host renders these as colored bands above the arrangement timeline. Empty sections is valid (older extensions, or generators that don't think structurally).

References (solo generator pattern)

SongContext.references follows the same contract as GenContext.references: locked input from an existing track. Enables "arrange a full song around this existing melody" — melody is input, everything else is generated.

Determinism

Same (ctx, params) → same output. Use ExtRng::new(ctx.seed). Section labels, track orders, and note pitches must all be deterministic.
