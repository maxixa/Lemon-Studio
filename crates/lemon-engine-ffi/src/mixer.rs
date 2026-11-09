// SPDX-License-Identifier: GPL-3.0-or-later
//! Mixer strip helpers: read track state, apply live volume/pan edits.

use os_model::Project;

pub fn track_count(project: &Project) -> u32 {
    if project.mixer.is_empty() {
        project.channels.len().max(1) as u32
    } else {
        project.mixer.len() as u32
    }
}

pub fn track_name(project: &Project, i: u32) -> String {
    project.mixer.get(i as usize)
        .and_then(|m| m.name.clone())
        .unwrap_or_else(|| format!("Track {}", i + 1))
}

pub fn track_volume(project: &Project, i: u32) -> f64 {
    project.mixer.get(i as usize)
        .and_then(|m| m.volume)
        .unwrap_or(0.75)
}

pub fn track_pan(project: &Project, i: u32) -> f64 {
    project.mixer.get(i as usize)
        .and_then(|m| m.pan)
        .unwrap_or(0.0)
}

/// Pack: count(u32) + for each track: name_len(u32), name_utf8, volume(f64), pan(f64)
pub fn pack_tracks(project: &Project) -> Vec<u8> {
    let mut out = Vec::new();
    let n = project.mixer.len() as u32;
    out.extend_from_slice(&n.to_le_bytes());
    for i in 0..n {
        let name = track_name(project, i);
        let bytes = name.as_bytes();
        out.extend_from_slice(&(bytes.len() as u32).to_le_bytes());
        out.extend_from_slice(bytes);
        out.extend_from_slice(&track_volume(project, i).to_le_bytes());
        out.extend_from_slice(&track_pan(project, i).to_le_bytes());
    }
    out
}
