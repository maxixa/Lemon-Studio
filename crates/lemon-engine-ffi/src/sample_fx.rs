// SPDX-License-Identifier: GPL-3.0-or-later
//! Derived-sample transform: apply ChannelSound fx (trim, reverse, swap,
//! clip, normalize, start offset) to a decoded sample, then write it
//! to a content-addressed cache WAV. Zero modifications to os-engine —
//! the engine just plays the derived file as if it were the original.

use std::path::{Path, PathBuf};

use os_engine::sample::Sample;
use os_model::ChannelSound;

/// Where derived files live. Cleared on app start; rebuilt on demand.
pub fn cache_dir() -> PathBuf {
    std::env::temp_dir().join("lemon_derived")
}

/// Deterministic content hash of everything the transform reads.
pub fn content_hash(original: &str, sound: &ChannelSound) -> String {
    let mut h: u64 = 0xcbf29ce484222325; // FNV-1a 64-bit offset basis
    let mut feed = |b: u8| {
        h ^= b as u64;
        h = h.wrapping_mul(0x100000001b3);
    };
    for b in original.as_bytes() { feed(*b); }
    for v in sound.start_offset_bits.to_le_bytes() { feed(v); }
    for v in sound.fx.flags.to_le_bytes() { feed(v); }
    for v in sound.fx.start_bits.to_le_bytes() { feed(v); }
    for v in sound.fx.length_bits.to_le_bytes() { feed(v); }
    feed(sound.fx.normalize as u8);
    format!("{:016x}", h)
}

/// True when the sound's fx would change the sample at all.
pub fn needs_transform(sound: &ChannelSound) -> bool {
    let fx = &sound.fx;
    if fx.flags & (os_model::SampleFx::REVERSE
                 | os_model::SampleFx::SWAP
                 | os_model::SampleFx::CLIP) != 0 { return true; }
    if fx.normalize { return true; }
    if sound.start_offset() > 0.0 { return true; }
    if fx.start() > 0.0 { return true; }
    if fx.length() < 1.0 { return true; }
    false
}

/// Apply the transform to a decoded sample. Order:
/// 1. Skip via `start_offset` (channel start)
/// 2. Skip via `fx.start()` (trim start)
/// 3. Truncate to `fx.length()` (trim length)
/// 4. Reverse if REVERSE
/// 5. Swap L/R if SWAP
/// 6. Clip if CLIP
/// 7. Normalize if `normalize`
pub fn transform(mut s: Sample, sound: &ChannelSound) -> Sample {
    let n = s.left.len().min(s.right.len());
    if n == 0 { return s; }

    // 1. Channel start offset
    let skip1 = (sound.start_offset().clamp(0.0, 0.999) * n as f64) as usize;
    if skip1 > 0 && skip1 < n {
        s.left.drain(..skip1);
        s.right.drain(..skip1);
    }
    let m = s.left.len();
    if m == 0 { return s; }

    // 2. Trim start (fx.start)
    let trim_start = (sound.fx.start().clamp(0.0, 1.0) * m as f64) as usize;
    if trim_start > 0 && trim_start < m {
        s.left.drain(..trim_start);
        s.right.drain(..trim_start);
    }

    // 3. Trim length (fx.length — fraction of remainder)
    let remaining = s.left.len();
    let keep = (sound.fx.length().clamp(0.0, 1.0) * remaining as f64) as usize;
    if keep > 0 && keep < remaining {
        s.left.truncate(keep);
        s.right.truncate(keep);
    }

    // 4. Reverse
    if sound.fx.flags & os_model::SampleFx::REVERSE != 0 {
        s.left.reverse();
        s.right.reverse();
    }

    // 5. Swap L/R
    if sound.fx.flags & os_model::SampleFx::SWAP != 0 {
        std::mem::swap(&mut s.left, &mut s.right);
    }

    // 6. Clip (soft-limit)
    if sound.fx.flags & os_model::SampleFx::CLIP != 0 {
        for v in s.left.iter_mut() { *v = v.clamp(-1.0, 1.0); }
        for v in s.right.iter_mut() { *v = v.clamp(-1.0, 1.0); }
    }

    // 7. Normalize
    if sound.fx.normalize {
        let peak = s.left.iter().chain(&s.right)
            .fold(0.0f32, |a, &b| a.max(b.abs()));
        if peak > 1e-6 {
            let scale = 1.0 / peak;
            for v in s.left.iter_mut() { *v *= scale; }
            for v in s.right.iter_mut() { *v *= scale; }
        }
    }

    s
}

/// Write a `Sample` to a WAV file (32-bit float stereo, os-engine's writer).
pub fn write_wav(path: &Path, sample: &Sample) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("mkdir: {e}"))?;
    }
    // Interleave L/R for os_engine::wav
    let n = sample.left.len().min(sample.right.len());
    let mut interleaved = Vec::with_capacity(n * 2);
    for i in 0..n {
        interleaved.push(sample.left[i]);
        interleaved.push(sample.right[i]);
    }
    let mut out = Vec::with_capacity(interleaved.len() * 4 + 44);
    os_engine::wav::write_f32_stereo(&mut out, sample.rate, &interleaved)
        .map_err(|e| format!("wav encode: {e:?}"))?;
    std::fs::write(path, &out).map_err(|e| format!("write: {e}"))?;
    Ok(())
}

/// Load, transform, write — or return cached path if already present.
/// Returns derived path (or original if no transform needed).
pub fn derive(
    original_path: &str,
    sound: &ChannelSound,
) -> Result<Option<String>, String> {
    if !needs_transform(sound) { return Ok(None); }
    let hash = content_hash(original_path, sound);
    let path = cache_dir().join(format!("{hash}.wav"));
    if path.exists() {
        return Ok(Some(path.to_string_lossy().into_owned()));
    }
    let sample = os_engine::sample::load(std::path::Path::new(original_path))
        .map_err(|e| format!("load: {e:?}"))?;
    let transformed = transform(sample, sound);
    write_wav(&path, &transformed)?;
    Ok(Some(path.to_string_lossy().into_owned()))
}

/// Clear the derived cache directory (called on app start).
pub fn clear_cache() {
    let dir = cache_dir();
    if dir.exists() {
        let _ = std::fs::remove_dir_all(&dir);
        log::info!("cleared derived-sample cache");
    }
}
