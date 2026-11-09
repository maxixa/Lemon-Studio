// SPDX-License-Identifier: GPL-3.0-or-later
//! Android audio output via cpal; the buffer comes from an `os_engine::Engine`
//! rendered by the reference wiring in tools/os-play/src/main.rs.
//!
//! When `engine` is `None` (no project loaded), the device outputs silence.

use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU32, Ordering};

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{FromSample, SampleFormat, SizedSample, Stream, StreamConfig};
use os_engine::Engine;

pub struct AudioDevice {
    _stream: Stream,
}

impl AudioDevice {
    pub fn new(
        engine: Option<Arc<Mutex<Engine>>>,
        peak: Arc<AtomicU32>,
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

        let stream = match format {
            SampleFormat::F32 => build::<f32>(&device, config, engine, peak.clone())?,
            SampleFormat::I16 => build::<i16>(&device, config, engine, peak.clone())?,
            SampleFormat::I32 => build::<i32>(&device, config, engine, peak.clone())?,
            SampleFormat::U16 => build::<u16>(&device, config, engine, peak.clone())?,
            other => return Err(format!("unsupported sample format: {other:?}").into()),
        };

        stream.play()?;
        Ok(Self { _stream: stream })
    }
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

    let stream = device.build_output_stream::<T, _, _>(
        config,
        move |out: &mut [T], _| {
            let frames = out.len() / channels;
            stereo.resize(frames * 2, 0.0);

            // Fill stereo via engine, or zero if no project / engine busy.
            // try_lock never blocks: on contention, the previous frame's
            // samples are still in `stereo`, which is fine for one callback.
            match engine.as_ref() {
                Some(arc) => match arc.try_lock() {
                    Ok(mut guard) if !guard.finished() => guard.process(&mut stereo),
                    _ => stereo.fill(0.0),
                },
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
        },
        |err| log::error!("audio stream error: {err}"),
        None,
    )?;
    Ok(stream)
}
