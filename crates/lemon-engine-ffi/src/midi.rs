// SPDX-License-Identifier: GPL-3.0-or-later
//! Minimal Standard MIDI File (Format 1) writer.
//!
//! One track. Notes emitted at project ppq. Meta events at t=0:
//! tempo, time signature (4/4), track name.
//!
//! Pattern mode: exports `current_pattern` (or 1). Song mode: expands
//! the first arrangement's pattern clips onto the timeline.

use os_model::{ClipSource, Project};

/// 1-based pattern to export in pattern mode.
pub fn render_to_midi(project: &Project, song_mode: bool) -> Result<Vec<u8>, String> {
    let ppq = project.ppq.max(1) as u16;
    let tempo = project.tempo.unwrap_or(140.0);
    let ticks_per_qn = ppq as u32;

    // (tick, sort_order, event_bytes)
    // sort_order: 0=meta, 1=note_off, 2=note_on — MIDI-canonical order at equal ticks
    let mut events: Vec<(u32, u8, Vec<u8>)> = Vec::new();

    // Track name
    let name = if song_mode { "Song" } else { "Pattern" };
    let mut name_ev = vec![0xFF, 0x03, name.len() as u8];
    name_ev.extend_from_slice(name.as_bytes());
    events.push((0, 0, name_ev));

    // Tempo (μs per quarter note)
    let us_per_qn = (60_000_000.0 / tempo.clamp(20.0, 999.0)) as u32;
    events.push((0, 0, vec![
        0xFF, 0x51, 0x03,
        ((us_per_qn >> 16) & 0xFF) as u8,
        ((us_per_qn >> 8) & 0xFF) as u8,
        (us_per_qn & 0xFF) as u8,
    ]));

    // Time signature: 4/4
    // 0xFF 0x58 0x04 numerator denominator(dd=2 means /4) clocks_per_click thirty_seconds_per_quarter
    events.push((0, 0, vec![0xFF, 0x58, 0x04, 4, 2, 24, 8]));

    let min_len = (ticks_per_qn / 4).max(1); // 1/16 note minimum, prevents 0-length notes

    if song_mode {
        let clips = project.arrangements.first()
            .map(|a| a.clips.as_slice())
            .unwrap_or(&[]);
        if !clips.is_empty() {
            for clip in clips {
                if clip.muted { continue; }
                let ClipSource::Pattern(num) = clip.source else { continue; };
                let Some(pattern) = project.patterns.iter().find(|p| p.number == num) else { continue; };
                for note in &pattern.notes {
                    if note.muted() { continue; }
                    if note.flags & os_model::Note::SLIDE != 0 { continue; }
                    let start = clip.position + note.position;
                    let len = note.length.max(min_len);
                    let end = start + len;
                    emit_note(&mut events, start, end, note.key, note.velocity);
                }
            }
        } else {
            // Empty arrangement falls back to pattern
            return render_pattern_events(project, events, ticks_per_qn, min_len);
        }
    } else {
        return render_pattern_events(project, events, ticks_per_qn, min_len);
    }

    Ok(build_file(ppq, events))
}

fn render_pattern_events(
    project: &Project,
    mut events: Vec<(u32, u8, Vec<u8>)>,
    _ticks_per_qn: u32,
    min_len: u32,
) -> Result<Vec<u8>, String> {
    let num = project.current_pattern.unwrap_or(1);
    let Some(pattern) = project.patterns.iter().find(|p| p.number == num) else {
        return Ok(build_file(project.ppq.max(1) as u16, events));
    };
    for note in &pattern.notes {
        if note.muted() { continue; }
        if note.flags & os_model::Note::SLIDE != 0 { continue; }
        let start = note.position;
        let len = note.length.max(min_len);
        let end = start + len;
        emit_note(&mut events, start, end, note.key, note.velocity);
    }
    Ok(build_file(project.ppq.max(1) as u16, events))
}

fn emit_note(events: &mut Vec<(u32, u8, Vec<u8>)>, start: u32, end: u32, key: u16, vel: u8) {
    let k = key.min(127) as u8;
    let v = vel.clamp(1, 127);
    events.push((start, 2, vec![0x90, k, v]));         // note on, channel 0
    events.push((end.max(start + 1), 1, vec![0x80, k, 0])); // note off
}

fn build_file(ppq: u16, mut events: Vec<(u32, u8, Vec<u8>)>) -> Vec<u8> {
    // Sort by (tick, order)
    events.sort_by_key(|(t, o, _)| (*t, *o));

    let mut track = Vec::new();
    let mut last_tick = 0u32;
    for (tick, _order, event) in events {
        let delta = tick.saturating_sub(last_tick);
        write_vlq(&mut track, delta);
        track.extend_from_slice(&event);
        last_tick = tick;
    }
    // End of track
    write_vlq(&mut track, 0);
    track.extend_from_slice(&[0xFF, 0x2F, 0x00]);

    let mut out = Vec::new();
    // MThd
    out.extend_from_slice(b"MThd");
    out.extend_from_slice(&6u32.to_be_bytes());
    out.extend_from_slice(&1u16.to_be_bytes());  // format 1
    out.extend_from_slice(&1u16.to_be_bytes());  // ntrks
    out.extend_from_slice(&ppq.to_be_bytes());   // division
    // MTrk
    out.extend_from_slice(b"MTrk");
    out.extend_from_slice(&(track.len() as u32).to_be_bytes());
    out.extend_from_slice(&track);
    out
}

fn write_vlq(out: &mut Vec<u8>, mut value: u32) {
    if value == 0 { out.push(0); return; }
    let mut bytes = vec![(value & 0x7F) as u8];
    value >>= 7;
    while value > 0 {
        bytes.push(((value & 0x7F) | 0x80) as u8);
        value >>= 7;
    }
    bytes.reverse();
    out.extend_from_slice(&bytes);
}
