
Testing

Every extension ships with tests. Minimum: one golden test, one determinism test.

Golden tests

```rust
#[cfg(test)]
mod tests {
    use super::*;

    fn ctx() -> GenContext {
        GenContext {
            tempo_bpm: 120.0,
            time_sig: TimeSig::COMMON,
            ppq: 96,
            target: TargetRange { track_idx: 0, pattern_idx: 0, start_tick: 0, length_ticks: 384 },
            held_notes: vec![],
            references: vec![],
            scale: Some(Scale::new(0, ScaleMode::Minor)),
            seed: 42,
            pattern_len: 384,
        }
    }

    #[test]
    fn euclidean_three_over_eight() {
        let gen = Euclidean { pulses: 3, steps: 8 };
        let mut p = ParamValues::new();
        p.set("pulses", ParamValue::Int(3));
        p.set("steps", ParamValue::Int(8));
        let out = gen.generate(&ctx(), &p).unwrap();

        // Golden: exactly 3 notes, at positions 0, 3, 6
        assert_eq!(out.primary.len(), 3);
        let positions: Vec<u32> = out.primary.iter().map(|n| n.start_tick).collect();
        assert_eq!(positions, vec![0, 3 * 96 / 8, 6 * 96 / 8]);
    }
}
```

Store larger fixtures (e.g. a 16-bar song sketch as JSON) in tests/fixtures/.

Determinism test

```rust
#[test]
fn deterministic_for_same_seed() {
    let gen = LofiBeat;
    let mut p = ParamValues::new();
    p.set("tempo", ParamValue::Float(78.0));
    p.set("bars", ParamValue::Int(16));
    let ctx = ctx_song();

    let a = gen.generate(&ctx, &p).unwrap();
    let b = gen.generate(&ctx, &p).unwrap();

    // Compare structurally (implement PartialEq on SongSketch or serialize + compare).
    assert_eq!(format!("{:?}", a), format!("{:?}", b));
}

#[test]
fn different_seeds_differ() {
    let gen = LofiBeat;
    let mut p = ParamValues::new();
    let mut ctx = ctx_song();
    ctx.seed = 1;
    let a = gen.generate(&ctx, &p).unwrap();
    ctx.seed = 2;
    let b = gen.generate(&ctx, &p).unwrap();
    assert_ne!(format!("{:?}", a), format!("{:?}", b));
}
```

Performance test

```rust
#[test]
fn completes_under_budget() {
    let gen = LofiBeat;
    let mut p = ParamValues::new();
    p.set("bars", ParamValue::Int(64));  // stress
    let ctx = ctx_song();
    let start = std::time::Instant::now();
    let _ = gen.generate(&ctx, &p).unwrap();
    assert!(start.elapsed().as_millis() < 2000, "exceeded 2s soft budget");
}
```

Target: < 2s for typical params, < 5s for heavy ones. If you hit 5s, add a progress dialog by setting ui().estimated_ms.

Integration test (optional)

Host runs on device via JNI. Declarative tests above cover extension logic; integration test covers JNI bridge. Skip unless you changed the JNI surface.
