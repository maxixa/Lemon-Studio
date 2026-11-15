//! Fast trigonometric approximations for the audio hot path.
//!
//! `Engine::process` spends most of its CPU in the windowed-sinc
//! resampler's coefficient kernel (`sinc` and `blackman` in `engine.rs`).
//! Those call `sin`/`cos` three times per tap, per output sample, per
//! voice. On ARM, libm's `sin`/`cos` cost hundreds of cycles; the audio
//! path needs only ~1e-6 accuracy (f32 samples quantise far coarser), and
//! wavetable interpolation reaches that in ~10 cycles.

use std::sync::OnceLock;

const TABLE_BITS: u32 = 11;                // 2048 entries — ~1e-6 error
const TABLE_SIZE: usize = 1 << TABLE_BITS;
const TABLE_MASK: usize = TABLE_SIZE - 1;
const TAU: f64 = std::f64::consts::TAU;
const INV_TAU: f64 = 1.0 / TAU;
const TABLE_SCALE: f64 = (TABLE_SIZE as f64) * INV_TAU;

static SIN_TABLE: OnceLock<Vec<f64>> = OnceLock::new();

fn table() -> &'static [f64] {
    SIN_TABLE.get_or_init(|| {
        (0..TABLE_SIZE)
            .map(|i| ((i as f64) * TAU / (TABLE_SIZE as f64)).sin())
            .collect()
    })
}

/// Fast sine, ~1e-6 max error, ~10–20× faster than libm on ARM.
/// Domain: any f64. Range: [-1, 1].
#[inline]
pub fn fast_sin(x: f64) -> f64 {
    let x = x.rem_euclid(TAU);
    let fidx = x * TABLE_SCALE;
    let i0 = (fidx as usize) & TABLE_MASK;
    let i1 = (i0 + 1) & TABLE_MASK;
    let frac = fidx - fidx.floor();
    let t = table();
    t[i0] + (t[i1] - t[i0]) * frac
}

/// Fast cosine via `fast_sin(x + π/2)`. ~1e-6 max error.
#[inline]
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
            let x = -20.0 + i as f64 * 0.002;
            let err = (fast_sin(x) - x.sin()).abs();
            if err > worst { worst = err; }
        }
        assert!(worst < 1e-5, "worst sin error = {worst:e}");
    }

    #[test]
    fn matches_libm_cos() {
        let mut worst = 0.0f64;
        for i in 0..20_000 {
            let x = -20.0 + i as f64 * 0.002;
            let err = (fast_cos(x) - x.cos()).abs();
            if err > worst { worst = err; }
        }
        assert!(worst < 1e-5, "worst cos error = {worst:e}");
    }

    #[test]
    fn cardinal_points() {
        assert!(fast_sin(0.0).abs() < 1e-9);
        assert!((fast_sin(std::f64::consts::FRAC_PI_2) - 1.0).abs() < 1e-5);
        assert!((fast_sin(std::f64::consts::PI)).abs() < 1e-5);
        assert!((fast_cos(0.0) - 1.0).abs() < 1e-5);
        assert!((fast_cos(std::f64::consts::PI) + 1.0).abs() < 1e-5);
    }

    #[test]
    fn wraps_large_args() {
        for x in [-1000.0, -100.0, std::f64::consts::TAU * 10.0, 1000.0] {
            let err = (fast_sin(x) - x.sin()).abs();
            assert!(err < 1e-4, "sin({x}) error = {err:e}");
        }
    }
}
