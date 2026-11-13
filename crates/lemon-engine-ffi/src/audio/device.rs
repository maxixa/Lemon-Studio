// SPDX-License-Identifier: GPL-3.0-or-later
//! Android audio output via cpal; the buffer comes from an `os_engine::Engine`
//! rendered by the reference wiring in tools/os-play/src/main.rs.
//!
//! When `engine` is `None` (no project loaded), the device outputs silence.

use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU32, AtomicU64, Ordering};

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{BufferSize, FromSample, SampleFormat, SizedSample, Stream, StreamConfig};
use os_engine::Engine;

pub struct AudioDevice {
    _stream: Stream,
}

impl AudioDevice {
    pub fn new(
        engine: Option<Arc<Mutex<Engine>>>,
        peak: Arc<AtomicU32>,
        preferred_buffer_frames: u32,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let host = cpal::default_host();
        let device = host
            .default_output_device()
            .ok_or("no default output device")?;
        let supported = device.default_output_config()?;
        let format = supported.sample_format();
        let config: StreamConfig = supported.into();

        log::info!(
            "audio config: {} Hz, {} ch, {:?}",
            config.sample_rate,
            config.channels,
            format
        );

        let (stream, chosen_buf) = build_with_fallback(&device, &config, format, engine, peak.clone(), preferred_buffer_frames)?;

        stream.play()?;

        // AAudio tuning report — grep logcat for "Lemon: audio: buffer"
        match chosen_buf {
            BufferSize::Fixed(n) => {
                let rate = config.sample_rate as f64;
                let ms = (n as f64 / rate) * 1000.0;
                log::info!("audio: buffer {} frames @ {} Hz ≈ {:.2} ms", n, rate, ms);
            }
            _ => log::info!(
                "audio: buffer Default (dynamically tuned) @ {} Hz",
                config.sample_rate
            ),
        }
        Ok(Self { _stream: stream })
    }
}

/// Try a sequence of buffer sizes, falling back on failure. cpal's AAudio
/// backend already requests PERFORMANCE_MODE_LOW_LATENCY, but its default
/// buffer size (Unknown) makes cpal tune dynamically from a large initial
/// buffer — audible cold-start latency. Requesting Fixed sizes skips that.
///
/// Order: 256 frames (≈5.3 ms @ 48 kHz) → 512 (≈10.7 ms) → Default. Every
/// fallback is byte-identical to today's behavior on the worst device.
fn build_with_fallback(
    device: &cpal::Device,
    base_config: &StreamConfig,
    format: SampleFormat,
    engine: Option<Arc<Mutex<Engine>>>,
    peak: Arc<AtomicU32>,
    preferred_buffer_frames: u32,
) -> Result<(Stream, BufferSize), Box<dyn std::error::Error>> {
    // 512 frames @ 48 kHz ≈ 10.7 ms — still a big win over the ~40 ms
    // default, but with ~2× the headroom of 256 to absorb CPU spikes.
    // 2048 frames @ 48 kHz ≈ 42.7 ms — debug-build Engine::process is
    // 5-10× slower than release; 512/1024 underrun constantly. Lower
    // back to 512 once a release build ships.
    let candidates: Vec<BufferSize> = if preferred_buffer_frames > 0 {
        vec![BufferSize::Fixed(preferred_buffer_frames), BufferSize::Default]
    } else {
        vec![BufferSize::Fixed(8192), BufferSize::Fixed(16384), BufferSize::Default]
    };
    for buf in candidates {
        let mut cfg = base_config.clone();
        cfg.buffer_size = buf;
        let result = match format {
            SampleFormat::F32 => build::<f32>(device, cfg, engine.clone(), peak.clone()),
            SampleFormat::I16 => build::<i16>(device, cfg, engine.clone(), peak.clone()),
            SampleFormat::I32 => build::<i32>(device, cfg, engine.clone(), peak.clone()),
            SampleFormat::U16 => build::<u16>(device, cfg, engine.clone(), peak.clone()),
            other => return Err(format!("unsupported sample format: {other:?}").into()),
        };
        match result {
            Ok(s) => {
                log::info!("audio: opened with {:?}", buf);
                return Ok((s, buf));
            }
            Err(e) => {
                log::warn!("audio: {:?} failed ({e}); trying next candidate", buf);
            }
        }
    }
    Err("all audio buffer candidates failed".into())
}

fn build<T>(
    device: &cpal::Device,
    config: StreamConfig,
    engine: Option<Arc<Mutex<Engine>>>,
    peak: Arc<AtomicU32>,
) -> Result<Stream, Box<dyn std::error::Error>>
where
    T: SizedSample + FromSample<f32>,
{
    let channels = usize::from(config.channels);
    // Reusable stereo scratch buffer. 2 channels interleaved, matches
    // `Engine::process(&mut [f32])`'s expectation in tools/os-play.
    let mut stereo: Vec<f32> = Vec::with_capacity(8192);

    let cb_count = Arc::new(AtomicU64::new(0));
    let sum_us = Arc::new(AtomicU64::new(0));
    let xrun_counter_cb = Arc::new(AtomicU32::new(0));
    let stream = device.build_output_stream::<T, _, _>(
        config,
        move |out: &mut [T], _| {
            let t_start = std::time::Instant::now();
            let frames = out.len() / channels;
            stereo.resize(frames * 2, 0.0);

            // Fill stereo via engine, or zero if no project / engine busy.
            // try_lock never blocks: on contention, the previous frame's
            // samples are still in `stereo`, which is fine for one callback.
            match engine.as_ref() {
                Some(arc) => match arc.try_lock() {
                    Ok(mut guard) => {
                        if guard.finished() {
                            // Song ended — output silence.
                            stereo.fill(0.0);
                        } else {
                            guard.process(&mut stereo);
                        }
                    }
                    // try_lock failed (JNI thread holds the engine mutex).
                    // Leave `stereo` untouched: re-emitting the previous frame
                    // is a tiny ~10 ms pop; zeroing it produces a full-silence
                    // click that is far louder. The previous code zeroed here,
                    // contradicting its own comment — that was the crackle.
                    Err(_) => { /* de-click: preserve previous frame */ }
                },
                // No project loaded — output silence.
                None => stereo.fill(0.0),
            }

            // Peak accumulation
            {
                let mut local_peak = 0.0f32;
                for s in stereo.iter() { local_peak = local_peak.max(s.abs()); }
                let prev = f32::from_bits(peak.load(Ordering::Relaxed));
                if local_peak > prev {
                    peak.store(local_peak.to_bits(), Ordering::Relaxed);
                }
            }

            // Map stereo to the device's channel layout.
            for (frame, s) in out.chunks_mut(channels).zip(stereo.chunks_exact(2)) {
                for (c, slot) in frame.iter_mut().enumerate() {
                    let v = match c {
                        0 => s[0],
                        1 => s[1],
                        _ => 0.0,
                    };
                    *slot = T::from_sample(v.clamp(-1.0, 1.0));
                }
            }

            let elapsed_us = t_start.elapsed().as_micros() as u64;
            let n = cb_count.fetch_add(1, Ordering::Relaxed) + 1;
            sum_us.fetch_add(elapsed_us, Ordering::Relaxed);
            if n % 200 == 0 {
                let avg = sum_us.swap(0, Ordering::Relaxed) / 200;
                let budget_us = (frames as u64 * 1_000_000) / 48000;
                log::info!("audio cb#{}: avg={}us budget={}us", n, avg, budget_us);
            }
        },
        move |err| {
            let n = xrun_counter_cb.fetch_add(1, Ordering::Relaxed) + 1;
            if n == 1 || n % 20 == 0 {
                log::error!("audio stream xrun #{}: {err}", n);
            }
        },
        None,
    )?;
    Ok(stream)
}
