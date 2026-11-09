// SPDX-License-Identifier: GPL-3.0-or-later
//! Offline render to WAV or FLAC over a file descriptor.

use std::fs::File;
use std::io::{BufWriter, Write};
use std::os::unix::io::FromRawFd;

use os_engine::{Engine, PlayMode, SampleLibrary};
use os_model::Project;
use flacenc::component::BitRepr;
use flacenc::error::Verify;

pub struct Stats {
    pub frames: u64,
    pub rate: u32,
    pub seconds: f64,
    pub peak: f32,
}

/// Render the project offline and return the interleaved stereo f32
/// samples plus stats. Shared by WAV and FLAC writers.
pub fn render_audio(
    project: &Project,
    song_mode: bool,
    rate: u32,
) -> (Vec<f32>, Stats) {
    let mode = if song_mode { PlayMode::Song } else { PlayMode::Pattern(1) };
    let mut library = SampleLibrary::new(None);
    let mut engine = Engine::new(project, &mut library, rate, mode);
    engine.follow_markers();

    let audio = engine.render_all(512);
    let frames = (audio.len() / 2) as u64;
    let seconds = frames as f64 / f64::from(rate);
    let peak = audio.iter().fold(0.0f32, |m, s| m.max(s.abs()));

    for (path, why) in &library.failures {
        log::warn!("missing sample: {path} ({why})");
    }
    let r = &engine.report;
    if r.plugin_channels_silent + r.effects_bypassed + r.automation_clips_ignored > 0 {
        log::warn!(
            "not-yet-supported: {} plugin channels silent, {} effects bypassed, {} automation ignored",
            r.plugin_channels_silent, r.effects_bypassed, r.automation_clips_ignored
        );
    }

    (audio, Stats { frames, rate, seconds, peak })
}

fn dup_fd(fd: i32) -> Result<File, String> {
    if fd < 0 { return Err(format!("invalid fd {fd}")); }
    let owned = std::mem::ManuallyDrop::new(unsafe { File::from_raw_fd(fd) });
    owned.try_clone().map_err(|e| format!("dup fd: {e}"))
}

// ─── WAV ────────────────────────────────────────────────────────
pub fn render_wav_to_fd(
    project: &Project,
    fd: i32,
    song_mode: bool,
    rate: u32,
) -> Result<Stats, String> {
    if rate == 0 { return Err("rate must be > 0".into()); }
    let dup = dup_fd(fd)?;
    let mut out = BufWriter::new(dup);

    log::info!("render WAV: rate={rate} song_mode={song_mode}");
    let (audio, stats) = render_audio(project, song_mode, rate);

    os_engine::wav::write_f32_stereo(&mut out, rate, &audio)
        .map_err(|e| format!("wav write: {e}"))?;

    log::info!(
        "WAV done: {} frames ({:.2}s) peak {:.4}",
        stats.frames, stats.seconds, stats.peak
    );
    Ok(stats)
}

// ─── FLAC ───────────────────────────────────────────────────────
pub fn render_flac_to_fd(
    project: &Project,
    fd: i32,
    song_mode: bool,
    rate: u32,
) -> Result<Stats, String> {
    if rate == 0 { return Err("rate must be > 0".into()); }
    let dup = dup_fd(fd)?;
    let mut out = BufWriter::new(dup);

    log::info!("render FLAC: rate={rate} song_mode={song_mode}");
    let (audio, stats) = render_audio(project, song_mode, rate);

    // Convert interleaved stereo f32 → interleaved stereo i32 (24-bit depth).
    // Matches os-app/src/export.rs lines 630-648 exactly — same call, same
    // API surface, no `into_verified` (which the reference never uses).
    let bits: usize = 24;
    let scale = (1i64 << (bits - 1)) as f32;
    let samples: Vec<i32> = audio.iter()
        .map(|&s| (s.clamp(-1.0, 1.0) * scale) as i32)
        .collect();

    let source = flacenc::source::MemSource::from_samples(
        &samples, 2, bits, rate as usize,
    );

    // flacenc 0.5: encode_with_fixed_block_size needs &Verified<Encoder>.
    // into_verified() takes 0 generics and returns Result<Verified<Self>, (Self, Error)>.
    let verified = flacenc::config::Encoder::default()
        .into_verified()
        .map_err(|(_, e)| format!("flac config: {e:?}"))?;
    let block_size = verified.block_size;

    let stream = flacenc::encode_with_fixed_block_size(&verified, source, block_size)
        .map_err(|e| format!("flac encode: {e:?}"))?;

    let mut sink = flacenc::bitsink::ByteSink::new();
    stream.write(&mut sink)
        .map_err(|e| format!("flac write sink: {e:?}"))?;

    out.write_all(sink.as_slice())
        .map_err(|e| format!("flac write file: {e}"))?;
    out.flush().map_err(|e| format!("flac flush: {e}"))?;


    log::info!(
        "FLAC done: {} frames ({:.2}s) peak {:.4}",
        stats.frames, stats.seconds, stats.peak
    );
    Ok(stats)
}

// ─── MP3 ────────────────────────────────────────────────────────
pub fn render_mp3_to_fd(
    project: &Project,
    fd: i32,
    song_mode: bool,
    rate: u32,
    kbps: u32,
) -> Result<Stats, String> {
    if rate == 0 { return Err("rate must be > 0".into()); }
    let dup = dup_fd(fd)?;
    let mut out = BufWriter::new(dup);

    log::info!("render MP3: rate={rate} kbps={kbps} song_mode={song_mode}");
    let (audio, stats) = render_audio(project, song_mode, rate);

    use mp3lame_encoder::{Bitrate, Builder, FlushGap, InterleavedPcm, Quality};

    let br = match kbps {
        64  => Bitrate::Kbps64,
        96  => Bitrate::Kbps96,
        128 => Bitrate::Kbps128,
        160 => Bitrate::Kbps160,
        192 => Bitrate::Kbps192,
        224 => Bitrate::Kbps224,
        256 => Bitrate::Kbps256,
        320 => Bitrate::Kbps320,
        _   => Bitrate::Kbps192,
    };

    let mut builder = Builder::new().expect("LAME builder");
    builder.set_sample_rate(rate);
    builder.set_num_channels(2);
    builder.set_brate(br);
    builder.set_quality(Quality::Best);
    let mut enc = builder.build().map_err(|e| format!("MP3 builder: {e:?}"))?;

    let frames = audio.len() / 2;
    let mut buf = Vec::with_capacity(mp3lame_encoder::max_required_buffer_size(frames));

    // FlushGap gives LAME's gapless info frame (os-app uses FlushGap).
    enc.encode_to_vec(InterleavedPcm(&audio), &mut buf)
        .map_err(|e| format!("MP3 encode: {e:?}"))?;
    enc.flush_to_vec::<FlushGap>(&mut buf)
        .map_err(|e| format!("MP3 flush: {e:?}"))?;

    out.write_all(&buf).map_err(|e| format!("mp3 write: {e}"))?;
    out.flush().map_err(|e| format!("mp3 flush: {e}"))?;

    log::info!("MP3 done: {} frames ({:.2}s)", stats.frames, stats.seconds);
    Ok(stats)
}

// ─── MIDI ───────────────────────────────────────────────────────
pub fn render_midi_to_fd(
    project: &Project,
    fd: i32,
    song_mode: bool,
) -> Result<Stats, String> {
    let dup = dup_fd(fd)?;
    let mut out = BufWriter::new(dup);

    log::info!("render MIDI: song_mode={song_mode}");
    let midi_bytes = crate::midi::render_to_midi(project, song_mode)?;
    let size = midi_bytes.len() as u64;

    out.write_all(&midi_bytes).map_err(|e| format!("midi write: {e}"))?;
    out.flush().map_err(|e| format!("midi flush: {e}"))?;

    log::info!("MIDI done: {} bytes", size);
    Ok(Stats {
        frames: size,
        rate: 0,
        seconds: 0.0,
        peak: 1.0,
    })
}
