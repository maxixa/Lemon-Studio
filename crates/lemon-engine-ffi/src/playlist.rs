// SPDX-License-Identifier: GPL-3.0-or-later
//! Playlist wire format (v3).
//!
//! Header (32 bytes, LE u32):
//!   [0]  magic = 0x4C454D32  ("LEM3")
//!   [4]  version = 3
//!   [8]  track_count
//!   [12] pattern_count
//!   [16] clip_count
//!   [20] marker_count
//!   [24] current_arrangement
//!   [28] bar_num
//!
//! Per clip (48 bytes):
//!   [0]  track u32
//!   [4]  position u32
//!   [8]  length u32
//!   [12] source_kind u32 (0=pattern, 1=channel)
//!   [16] source_ref u32 (pattern # or channel idx)
//!   [20] muted u32
//!   [24] window_lo f32 (0 = no window)
//!   [28] window_hi f32 (0 = no window)
//!   [32] fade_in_ms f32       (0 = no manual fade)
//!   [36] fade_in_tension f32
//!   [40] fade_out_ms f32      (0 = no manual fade)
//!   [44] fade_out_tension f32
//!
//! Per marker (52 bytes):
//!   [0]  position u32
//!   [4]  kind u32
//!   [8]  numerator u32
//!   [12] denominator u32
//!   [16] scale u32
//!   [20..52] name bytes (null-padded UTF-8, <=32 bytes)
//!
//! v3 adds fade fields where v2 had reserved zeros. Wire is transient
//! (regenerated each session from the Project), so version bump causes
//! no on-disk migration. Both sides must be updated together.

use os_model::{Arrangement, Clip, ClipSource, Id, Project, TimeMarker};

pub const HEADER: usize = 32;
pub const CLIP:   usize = 48;
pub const MARKER: usize = 52;
const MAGIC: u32 = 0x4C454D32;
const VERSION: u32 = 3;

pub struct ClipWire {
    pub track: u16,
    pub position: u32,
    pub length: u32,
    pub source: ClipSource,
    pub muted: bool,
    pub window: Option<(f64, f64)>,
    pub fade_in_ms: f32,
    pub fade_in_tension: f32,
    pub fade_out_ms: f32,
    pub fade_out_tension: f32,
}

pub struct MarkerWire {
    pub position: u32,
    pub kind: u8,
    pub name: String,
    pub numerator: u8,
    pub denominator: u8,
    pub scale: u16,
}

pub struct Wire {
    pub track_count: u32,
    pub pattern_count: u32,
    pub current_arrangement: u32,
    pub bar_num: u32,
    pub clips: Vec<ClipWire>,
    pub markers: Vec<MarkerWire>,
}

pub fn pack(project: &Project) -> Vec<u8> {
    let arr = project.current_arrangement
        .and_then(|n| project.arrangements.iter().find(|a| a.number == n))
        .or_else(|| project.arrangements.first());
    let track_count = arr.map(|a| a.tracks.len()).unwrap_or(0) as u32;
    let pattern_count = project.patterns.len() as u32;
    let clips: &[Clip] = arr.map(|a| a.clips.as_slice()).unwrap_or(&[]);
    let markers: &[TimeMarker] = arr.map(|a| a.markers.as_slice()).unwrap_or(&[]);
    let current_arrangement = project.current_arrangement
        .map(|n| n as u32)
        .or_else(|| arr.map(|a| a.number as u32))
        .unwrap_or(0);
    let bar_num = project.bar.map(|(n, _)| n as u32).unwrap_or(0);

    let mut out = Vec::with_capacity(HEADER + clips.len() * CLIP + markers.len() * MARKER);
    out.extend_from_slice(&MAGIC.to_le_bytes());
    out.extend_from_slice(&VERSION.to_le_bytes());
    out.extend_from_slice(&track_count.to_le_bytes());
    out.extend_from_slice(&pattern_count.to_le_bytes());
    out.extend_from_slice(&(clips.len() as u32).to_le_bytes());
    out.extend_from_slice(&(markers.len() as u32).to_le_bytes());
    out.extend_from_slice(&current_arrangement.to_le_bytes());
    out.extend_from_slice(&bar_num.to_le_bytes());

    for c in clips {
        let (kind, val) = match c.source {
            ClipSource::Pattern(n) => (0u32, n as u32),
            ClipSource::Channel(n) => (1u32, n as u32),
        };
        let (wlo, whi) = c.window.unwrap_or((0.0, 0.0));
        let (fade_in_ms, fade_in_tension) = c.fade_in().unwrap_or((0.0, 0.0));
        let (fade_out_ms, fade_out_tension) = c.fade_out().unwrap_or((0.0, 0.0));
        out.extend_from_slice(&(c.track as u32).to_le_bytes());
        out.extend_from_slice(&c.position.to_le_bytes());
        out.extend_from_slice(&c.length.to_le_bytes());
        out.extend_from_slice(&kind.to_le_bytes());
        out.extend_from_slice(&val.to_le_bytes());
        out.extend_from_slice(&(c.muted as u32).to_le_bytes());
        out.extend_from_slice(&(wlo as f32).to_le_bytes());
        out.extend_from_slice(&(whi as f32).to_le_bytes());
        out.extend_from_slice(&fade_in_ms.to_le_bytes());
        out.extend_from_slice(&fade_in_tension.to_le_bytes());
        out.extend_from_slice(&fade_out_ms.to_le_bytes());
        out.extend_from_slice(&fade_out_tension.to_le_bytes());
    }

    for m in markers {
        out.extend_from_slice(&m.position.to_le_bytes());
        out.extend_from_slice(&(m.kind as u32).to_le_bytes());
        out.extend_from_slice(&(m.numerator as u32).to_le_bytes());
        out.extend_from_slice(&(m.denominator as u32).to_le_bytes());
        out.extend_from_slice(&(m.scale as u32).to_le_bytes());
        out.extend_from_slice(&name_to_buf(&m.name));
    }

    out
}

pub fn unpack(bytes: &[u8]) -> Result<Wire, String> {
    if bytes.len() < HEADER {
        return Err(format!("short: {}<{}", bytes.len(), HEADER));
    }
    let magic = u32::from_le_bytes(bytes[0..4].try_into().unwrap());
    if magic != MAGIC {
        return Err(format!("bad magic 0x{:08X} (expected 0x{:08X})", magic, MAGIC));
    }
    let version = u32::from_le_bytes(bytes[4..8].try_into().unwrap());
    if version != VERSION {
        return Err(format!("bad version {} (expected {})", version, VERSION));
    }
    let track_count = u32::from_le_bytes(bytes[8..12].try_into().unwrap());
    let pattern_count = u32::from_le_bytes(bytes[12..16].try_into().unwrap());
    let clip_count = u32::from_le_bytes(bytes[16..20].try_into().unwrap()) as usize;
    let marker_count = u32::from_le_bytes(bytes[20..24].try_into().unwrap()) as usize;
    let current_arrangement = u32::from_le_bytes(bytes[24..28].try_into().unwrap());
    let bar_num = u32::from_le_bytes(bytes[28..32].try_into().unwrap());

    let expected = HEADER + clip_count * CLIP + marker_count * MARKER;
    if bytes.len() != expected {
        return Err(format!("size {}!={}", bytes.len(), expected));
    }

    let mut clips = Vec::with_capacity(clip_count);
    let mut base = HEADER;
    for _ in 0..clip_count {
        let track = u32::from_le_bytes(bytes[base..base+4].try_into().unwrap()) as u16;
        let position = u32::from_le_bytes(bytes[base+4..base+8].try_into().unwrap());
        let length = u32::from_le_bytes(bytes[base+8..base+12].try_into().unwrap());
        let kind = u32::from_le_bytes(bytes[base+12..base+16].try_into().unwrap());
        let val = u32::from_le_bytes(bytes[base+16..base+20].try_into().unwrap()) as u16;
        let muted = u32::from_le_bytes(bytes[base+20..base+24].try_into().unwrap()) != 0;
        let wlo = f32::from_le_bytes(bytes[base+24..base+28].try_into().unwrap()) as f64;
        let whi = f32::from_le_bytes(bytes[base+28..base+32].try_into().unwrap()) as f64;
        let fade_in_ms = f32::from_le_bytes(bytes[base+32..base+36].try_into().unwrap());
        let fade_in_tension = f32::from_le_bytes(bytes[base+36..base+40].try_into().unwrap());
        let fade_out_ms = f32::from_le_bytes(bytes[base+40..base+44].try_into().unwrap());
        let fade_out_tension = f32::from_le_bytes(bytes[base+44..base+48].try_into().unwrap());
        let source = match kind {
            1 => ClipSource::Channel(val),
            _ => ClipSource::Pattern(val),
        };
        let window = if wlo == 0.0 && whi == 0.0 { None } else { Some((wlo, whi)) };
        clips.push(ClipWire {
            track, position, length, source, muted, window,
            fade_in_ms, fade_in_tension, fade_out_ms, fade_out_tension,
        });
        base += CLIP;
    }

    let mut markers = Vec::with_capacity(marker_count);
    for _ in 0..marker_count {
        let position = u32::from_le_bytes(bytes[base..base+4].try_into().unwrap());
        let kind = u32::from_le_bytes(bytes[base+4..base+8].try_into().unwrap()) as u8;
        let numerator = u32::from_le_bytes(bytes[base+8..base+12].try_into().unwrap()) as u8;
        let denominator = u32::from_le_bytes(bytes[base+12..base+16].try_into().unwrap()) as u8;
        let scale = u32::from_le_bytes(bytes[base+16..base+20].try_into().unwrap()) as u16;
        let name = buf_to_name(&bytes[base+20..base+52]);
        markers.push(MarkerWire { position, kind, name, numerator, denominator, scale });
        base += MARKER;
    }

    Ok(Wire { track_count, pattern_count, current_arrangement, bar_num, clips, markers })
}

pub fn commit(project: &mut Project, wire: Wire) -> Result<(), String> {
    if project.arrangements.is_empty() {
        project.arrangements.push(Arrangement {
            id: Id::NONE,
            number: 0,
            name: Some("Arrangement 1".into()),
            clips: Vec::new(),
            tracks: Vec::new(),
            markers: Vec::new(),
        });
    }
    let target_num = wire.current_arrangement as u16;
    let idx = project.arrangements.iter().position(|a| a.number == target_num)
        .unwrap_or_else(|| {
            log::warn!(
                "playlist commit: arrangement {} not found, falling back to first",
                target_num
            );
            0
        });
    let arr = &mut project.arrangements[idx];

    arr.clips.clear();
    for c in wire.clips {
        if let ClipSource::Pattern(n) = c.source {
            if n == 0 { continue; }
        }
        let mut clip = Clip::new(c.position, c.length, c.track, c.source);
        clip.muted = c.muted;
        if let Some((lo, hi)) = c.window {
            clip.window = Some((lo, hi));
        }
        // Fades: only set when wire carries non-zero finite ms. Derived
        // auto-crossfade fades (bit clear in raw[56]) aren't touched here —
        // they're re-derived at engine build time from clip overlap.
        if c.fade_in_ms > 0.0 && c.fade_in_ms.is_finite() {
            clip.set_fade_in(Some((c.fade_in_ms, c.fade_in_tension)));
        }
        if c.fade_out_ms > 0.0 && c.fade_out_ms.is_finite() {
            clip.set_fade_out(Some((c.fade_out_ms, c.fade_out_tension)));
        }
        arr.clips.push(clip);
    }
    arr.clips.sort_by_key(|c| c.position);

    arr.markers.clear();
    for m in wire.markers {
                arr.markers.push(TimeMarker {
            position: m.position,
            kind: m.kind,
            name: m.name,
            numerator: m.numerator,
            denominator: m.denominator,
            scale: m.scale,
            root: 0,
            flag: 0,
            scale_index: -1,
        });;
    }
    arr.markers.sort_by_key(|m| m.position);

    Ok(())
}

fn name_to_buf(name: &str) -> [u8; 32] {
    let mut end = name.len().min(32);
    while end > 0 && !name.is_char_boundary(end) {
        end -= 1;
    }
    let mut buf = [0u8; 32];
    buf[..end].copy_from_slice(&name.as_bytes()[..end]);
    buf
}

fn buf_to_name(buf: &[u8]) -> String {
    let mut end = buf.len();
    while end > 0 && buf[end - 1] == 0 {
        end -= 1;
    }
    String::from_utf8_lossy(&buf[..end]).into_owned()
}
