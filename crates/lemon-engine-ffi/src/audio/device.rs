// SPDX-License-Identifier: GPL-3.0-or-later
//! Android audio output via cpal; the buffer comes from an `os_engine::Engine`
//! rendered by the reference wiring in tools/os-play/src/main.rs.
//!
//! When `engine` is `None` (no project loaded), the device outputs silence.

use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, AtomicUsize, Ordering};

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{BufferSize, FromSample, SampleFormat, SizedSample, Stream, StreamConfig};
use os_engine::Engine;
use std::thread::JoinHandle;
use ringbuf::{HeapRb, HeapProd, HeapCons};
use ringbuf::traits::{Split, Producer, Consumer};

const BLOCK_FRAMES: usize = 4096;
const RING_BLOCKS: usize = 8;

type Block = Box<[f32]>;

fn spawn_render_thread(
    engine: Option<Arc<Mutex<Engine>>>,
    mut producer: HeapProd<Block>,
    stop: Arc<AtomicBool>,
    peak: Arc<AtomicU32>,
    ring_fill: Arc<AtomicUsize>,
) -> JoinHandle<()> {
    std::thread::Builder::new()
        .name("lemon-render".into())
        .spawn(move || {
            let mut block: Block = vec![0.0f32; BLOCK_FRAMES * 2].into_boxed_slice();
            while !stop.load(Ordering::Relaxed) {
                match engine.as_ref() {
                    Some(arc) => match arc.try_lock() {
                        Ok(mut guard) => {
                            if guard.finished() { block.fill(0.0); }
                            else { guard.process(&mut block); }
                        }
                        Err(_) => {
                            // A1q.1 — JNI holds engine mutex. Don't skip push:
                            // that drains the ring while render thread idles.
                            // Push silence so ring stays fed; retry next iteration.
                            TL_FAILS.fetch_add(1, Ordering::Relaxed);
                            block.fill(0.0);
                        }
                    },
                    None => block.fill(0.0),
                }
                let mut local_peak = 0.0f32;
                for &s in block.iter() {
                    let a = s.abs();
                    if a > local_peak { local_peak = a; }
                }
                let prev = f32::from_bits(peak.load(Ordering::Relaxed));
                if local_peak > prev { peak.store(local_peak.to_bits(), Ordering::Relaxed); }
                let mut to_push = block.clone();
                loop {
                    if stop.load(Ordering::Relaxed) { return; }
                    match producer.try_push(to_push) {
                        Ok(()) => { ring_fill.fetch_add(1, Ordering::Relaxed); break; }
                        Err(returned) => {
                            to_push = returned;
                            std::thread::sleep(std::time::Duration::from_millis(1));
                        }
                    }
                }
            }
        })
        .expect("spawn lemon-render thread")
}

pub struct AudioDevice {
    _stream: Stream,
    _render_thread: Option<JoinHandle<()>>,
    stop: Arc<AtomicBool>,
}

impl AudioDevice {
    pub fn new(
        engine: Option<Arc<Mutex<Engine>>>,
        peak: Arc<AtomicU32>,
        preferred_buffer_frames: u32,
        output_gain: Arc<AtomicU32>,
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

        let ring_fill = Arc::new(AtomicUsize::new(0));
        let (stream, chosen_buf, producer) = build_with_fallback(
            &device, &config, format,
            preferred_buffer_frames, output_gain, ring_fill.clone(),
        )?;
        let stop = Arc::new(AtomicBool::new(false));
        let _render_thread = Some(spawn_render_thread(
            engine, producer, stop.clone(), peak, ring_fill,
        ));

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
        Ok(Self { _stream: stream, _render_thread, stop })
    }
}

/// Try a sequence of buffer sizes, falling back on failure. cpal's AAudio
/// backend already requests PERFORMANCE_MODE_LOW_LATENCY, but its default
/// buffer size (Unknown) makes cpal tune dynamically from a large initial
/// buffer — audible cold-start latency. Requesting Fixed sizes skips that.
///
/// Order: 256 frames (≈5.3 ms @ 48 kHz) → 512 (≈10.7 ms) → Default. Every
/// fallback is byte-identical to today's behavior on the worst device.

static TL_FAILS: std::sync::LazyLock<Arc<AtomicU32>> =
    std::sync::LazyLock::new(|| Arc::new(AtomicU32::new(0)));

impl Drop for AudioDevice {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
        if let Some(handle) = self._render_thread.take() {
            let _ = handle.join();
        }
    }
}

fn build_with_fallback(
    device: &cpal::Device,
    base_config: &StreamConfig,
    format: SampleFormat,
    preferred_buffer_frames: u32,
    output_gain: Arc<AtomicU32>,
    ring_fill: Arc<AtomicUsize>,
) -> Result<(Stream, BufferSize, HeapProd<Block>), Box<dyn std::error::Error>> {
    let candidates: Vec<BufferSize> = if preferred_buffer_frames > 0 {
        vec![BufferSize::Fixed(preferred_buffer_frames), BufferSize::Default]
    } else {
        vec![BufferSize::Fixed(8192), BufferSize::Fixed(16384), BufferSize::Default]
    };
    for buf in candidates {
        let mut cfg = base_config.clone();
        cfg.buffer_size = buf;
        let (producer, consumer) = HeapRb::<Block>::new(RING_BLOCKS).split();
        let result = match format {
            SampleFormat::F32 => build::<f32>(device, cfg, consumer, ring_fill.clone(), output_gain.clone()),
            SampleFormat::I16 => build::<i16>(device, cfg, consumer, ring_fill.clone(), output_gain.clone()),
            SampleFormat::I32 => build::<i32>(device, cfg, consumer, ring_fill.clone(), output_gain.clone()),
            SampleFormat::U16 => build::<u16>(device, cfg, consumer, ring_fill.clone(), output_gain.clone()),
            other => return Err(format!("unsupported format: {other:?}").into()),
        };
        match result {
            Ok(s) => {
                log::info!("audio: opened with {:?}", buf);
                return Ok((s, buf, producer));
            }
            Err(e) => {
                log::warn!("audio: {:?} failed: {e}", buf);
            }
        }
    }
    Err("all audio buffer candidates failed".into())
}

/// Smooth soft-clip. Linear below 0.7, gentle curve 0.7→1.5, saturates at 1.0.
/// Hard clamp produces square-wave edges on clipped samples (harsh crackle);
/// soft clip produces harmonics — audible as "loud" instead of "crunchy".
fn soft_clip(x: f32) -> f32 {
    let a = x.abs();
    if a <= 0.7 { return x; }
    if a >= 1.5 { return x.signum(); }
    let t = (a - 0.7) / 0.8;
    x.signum() * (0.7 + 0.3 * (t * t * (3.0 - 2.0 * t)))
}

fn build<T>(
    device: &cpal::Device,
    config: StreamConfig,
    mut consumer: HeapCons<Block>,
    ring_fill: Arc<AtomicUsize>,
    output_gain: Arc<AtomicU32>,
) -> Result<Stream, Box<dyn std::error::Error>>
where
    T: SizedSample + FromSample<f32>,
{
    let channels = usize::from(config.channels);
    let cb_count = Arc::new(AtomicU64::new(0));
    let sum_us = Arc::new(AtomicU64::new(0));
    let underrun_count = Arc::new(AtomicU32::new(0));
    let xrun_counter_cb = Arc::new(AtomicU32::new(0));

    let mut partial: Option<Block> = None;
    let mut pos_in_partial: usize = 0;

    let stream = device.build_output_stream::<T, _, _>(
        config,
        move |out: &mut [T], _| {
            let t_start = std::time::Instant::now();
            let total_frames = out.len() / channels;
            let gain = f32::from_bits(output_gain.load(Ordering::Relaxed));

            let mut written = 0usize;
            while written < total_frames {
                if partial.is_none() {
                    match consumer.try_pop() {
                        Some(b) => {
                            let _ = ring_fill.try_update(
                                Ordering::Relaxed, Ordering::Relaxed,
                                |v| Some(v.saturating_sub(1)),
                            );
                            partial = Some(b);
                            pos_in_partial = 0;
                        }
                        None => {
                            underrun_count.fetch_add(1, Ordering::Relaxed);
                            break;
                        }
                    }
                }
                let block = partial.as_ref().unwrap();
                let block_frames = block.len() / 2;
                let avail = block_frames - pos_in_partial;
                let take = (total_frames - written).min(avail);
                for i in 0..take {
                    let src_idx = (pos_in_partial + i) * 2;
                    let s0 = block[src_idx];
                    let s1 = block[src_idx + 1];
                    let frame_start = (written + i) * channels;
                    for c in 0..channels {
                        let v = match c { 0 => s0, 1 => s1, _ => 0.0 };
                        out[frame_start + c] = T::from_sample(soft_clip(
                            if v.is_finite() { v * gain } else { 0.0 },
                        ));
                    }
                }
                written += take;
                pos_in_partial += take;
                if pos_in_partial >= block_frames {
                    partial = None;
                    pos_in_partial = 0;
                }
            }

            if written < total_frames {
                for s in &mut out[written * channels..] {
                    *s = T::from_sample(0.0);
                }
            }

            let elapsed_us = t_start.elapsed().as_micros() as u64;
            let n = cb_count.fetch_add(1, Ordering::Relaxed) + 1;
            sum_us.fetch_add(elapsed_us, Ordering::Relaxed);
            if n % 20 == 0 {
                let avg = sum_us.swap(0, Ordering::Relaxed) / 200;
                let budget_us = (total_frames as u64 * 1_000_000) / 48000;
                let under = underrun_count.load(Ordering::Relaxed);
                log::info!(
                    "A1q cb#{}: avg={}us budget={}us underruns={} tl_fails={} gain={:.2}",
                    n, avg, budget_us, under, TL_FAILS.load(Ordering::Relaxed), gain
                );
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