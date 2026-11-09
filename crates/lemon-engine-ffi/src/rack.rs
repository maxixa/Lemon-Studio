// SPDX-License-Identifier: GPL-3.0-or-later
//! Step-sequencer helpers: step count + step bytes.
//!
//! A "step note" is a `Note` with `length == 0`. Step size = `ppq / 4`
//! ticks (24 at 96 PPQ). Step count derives from the pattern's length,
//! rounded up to a full multiple of 16 steps (one 4/4 bar). Wire format
//! is one byte per step (0 = off, 1 = on) so patterns of any length fit.

use os_model::{Note, Project};

/// One 16th-note step in ticks.
pub fn step_ticks(project: &Project) -> u32 {
    (project.ppq as u32 / 4).max(1)
}

/// Number of steps the pattern occupies, always a multiple of 16, min 16.
pub fn step_count(project: &Project, pattern_num: u16) -> u32 {
    let st = step_ticks(project);
    let plen = project.pattern_length(pattern_num);
    let raw = plen.div_ceil(st);
    let rounded = ((raw + 15) / 16) * 16;
    rounded.max(16)
}

/// One byte per step for `channel` in `pattern_num`: 0 = off, 1 = on.
pub fn mask_bytes(project: &Project, pattern_num: u16, channel: u16) -> Vec<u8> {
    let steps = step_count(project, pattern_num);
    let st = step_ticks(project);
    let mut buf = vec![0u8; steps as usize];
    let Some(pattern) = project.patterns.iter().find(|p| p.number == pattern_num) else {
        return buf;
    };
    for n in &pattern.notes {
        if n.channel == channel && n.length == 0 {
            let idx = (n.position / st) as usize;
            if idx < buf.len() {
                buf[idx] = 1;
            }
        }
    }
    buf
}

/// Toggle `step` of `channel` in `pattern_num`. Returns new state
/// (true = on), None if pattern not found or step out of range.
pub fn toggle(project: &mut Project, pattern_num: u16, channel: u16, step: u32) -> Option<bool> {
    let steps = step_count(project, pattern_num);
    if step >= steps { return None; }
    let st = step_ticks(project);
    let position = step * st;
    let pattern = project.patterns.iter_mut().find(|p| p.number == pattern_num)?;
    if let Some(i) = pattern.notes.iter().position(|n|
        n.channel == channel && n.length == 0 && n.position == position)
    {
        pattern.notes.remove(i);
        Some(false)
    } else {
        pattern.notes.push(Note::step(channel, position));
        Some(true)
    }
}

/// Rotate step positions of `channel` in `pattern_num` by `delta`.
/// Positive = right (later), negative = left (earlier). Wraps around
/// the pattern's step count. Returns false if pattern not found or
/// step count is zero.
pub fn rotate_steps(project: &mut Project, pattern_num: u16, channel: u16, delta: i32) -> bool {
    let steps = step_count(project, pattern_num);
    let st = step_ticks(project);
    if steps == 0 || st == 0 { return false; }

    let Some(pattern) = project.patterns.iter_mut().find(|p| p.number == pattern_num) else {
        return false;
    };

    let mut on_steps: Vec<u32> = Vec::new();
    for n in &pattern.notes {
        if n.channel == channel && n.length == 0 {
            let idx = n.position / st;
            if idx < steps {
                on_steps.push(idx);
            }
        }
    }

    pattern.notes.retain(|n| !(n.channel == channel && n.length == 0));

    if on_steps.is_empty() {
        return true;
    }

    let n_i = steps as i64;
    for i in on_steps {
        let new_i = ((i as i64 + delta as i64).rem_euclid(n_i)) as u32;
        pattern.notes.push(Note::step(channel, new_i * st));
    }
    true
}

/// Replace all step notes of `channel` in `pattern_num` with the given
/// mask. `mask[i] != 0` means step `i` is on. Returns false if pattern
/// not found or `mask` is empty.
pub fn apply_step_mask(project: &mut Project, pattern_num: u16, channel: u16, mask: &[u8]) -> bool {
    if mask.is_empty() { return false; }
    let steps = step_count(project, pattern_num);
    if steps == 0 { return false; }
    let st = step_ticks(project);
    let Some(pattern) = project.patterns.iter_mut().find(|p| p.number == pattern_num) else {
        return false;
    };
    pattern.notes.retain(|n| !(n.channel == channel && n.length == 0));
    let n = (mask.len() as u32).min(steps);
    for i in 0..n {
        if mask[i as usize] != 0 {
            pattern.notes.push(Note::step(channel, i * st));
        }
    }
    true
}
