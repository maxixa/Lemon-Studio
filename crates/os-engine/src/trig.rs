//! Fast trigonometric approximations for the audio hot path.
//!
//! `Engine::process` spends most of its CPU in the windowed-sinc
//! resampler's coefficient kernel (`sinc` + `blackman` in `engine.rs`).
//! Those call trig three times per tap, per output sample, per voice.
//! On ARM libm sin/cos cost hundreds of cycles; the audio path needs only
//! ~1e-5 accuracy (f32 samples quantise far coarser) and wavetable
//! interpolation reaches that in ~25 cycles.

use std::sync::OnceLock;

const TABLE_BITS: u32 = 11;
const TABLE_SIZE: usize = 1 << TABLE_BITS;
const TABLE_MASK: usize = TABLE_SIZE - 1;
const TAU: f64 = std::f64::consts::TAU;
const TABLE_SCALE: f64 = (TABLE_SIZE as f64) / TAU;

static SIN_TABLE: OnceLock<Vec<f64>> = OnceLock::new();

#[inline(always)]
fn table() -> &'static [f64] {
    SIN_TABLE.get_or_init(|| {
        (0..TABLE_SIZE)
            .map(|i| ((i as f64) * TAU / (TABLE_SIZE as f64)).sin())
            .collect()
    })
}

/// Fast sine. Accuracy <1e-4 for |x| < 200 rad (covers resampler range).
/// Precision degrades gracefully beyond that; callers in the audio path
/// stay within the bound.
#[inline(always)]
pub fn fast_sin(x: f64) -> f64 {
    let fidx = x * TABLE_SCALE;
    let ffloor = fidx.floor();
    // Wrap: i0 = floor(fidx) mod TABLE_SIZE. Two's complement + mask
    // handles negatives correctly; floor() is what makes it correct for
    // negative fidx (`as isize` alone truncates toward zero — wrong).
    let i0 = (ffloor as isize as usize) & TABLE_MASK;
    let i1 = (i0 + 1) & TABLE_MASK;
    let frac = fidx - ffloor;
    let t = table();
    t[i0] + (t[i1] - t[i0]) * frac
}

/// Fast cosine via `fast_sin(x + pi/2)`.
#[inline(always)]
pub fn fast_cos(x: f64) -> f64 {
    fast_sin(x + std::f64::consts::FRAC_PI_2)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_libm_sin() {
        let mut worst = 0.0f64;
        for i in 0..20_000 {
            let x = -200.0 + i as f64 * 0.02;
            let err = (fast_sin(x) - x.sin()).abs();
            if err > worst { worst = err; }
        }
        assert!(worst < 1e-4, "worst sin error = {worst:e}");
    }

    #[test]
    fn matches_libm_cos() {
        let mut worst = 0.0f64;
        for i in 0..20_000 {
            let x = -200.0 + i as f64 * 0.02;
            let err = (fast_cos(x) - x.cos()).abs();
            if err > worst { worst = err; }
        }
        assert!(worst < 1e-4, "worst cos error = {worst:e}");
    }

    #[test]
    fn negative_args_correct() {
        // Regression test: naive `as isize` truncation gives wrong results
        // for negative fidx. Explicit `.floor()` fixes it.
        for x in [-0.5, -1.0, -1.5, -3.14, -10.0, -100.0] {
            let err = (fast_sin(x) - x.sin()).abs();
            assert!(err < 1e-4, "sin({x}) err = {err:e}");
        }
    }

    #[test]
    fn cardinal_points() {
        assert!(fast_sin(0.0).abs() < 1e-9);
        assert!((fast_sin(std::f64::consts::FRAC_PI_2) - 1.0).abs() < 1e-4);
        assert!((fast_sin(std::f64::consts::PI)).abs() < 1e-4);
        assert!((fast_cos(0.0) - 1.0).abs() < 1e-4);
        assert!((fast_cos(std::f64::consts::PI) + 1.0).abs() < 1e-4);
    }
}
