// SPDX-License-Identifier: GPL-3.0-or-later
//! Note buffer wire format between Kotlin and Rust.
//!
//! Header (12 bytes):
//!   ppq:            u32 LE
//!   pattern_length: u32 LE   (ticks)
//!   count:          u32 LE
//!
//! Body (56 bytes per note), 14 u32 LE fields:
//!   position, length, channel, key, velocity,
//!   pan, fine_pitch, release, mod_x, mod_y,
//!   flags, group, unk14, repeat
//!
//! Every Note field crosses the wire so that editing a note in the
//! piano roll cannot silently clobber pan / release / slide flags /
//! colour group / unk14 / repeat on save. Values are clamped on the
//! Rust side so a rogue Kotlin value cannot corrupt the model.

use os_model::Note;

pub const HEADER: usize = 12;
pub const NOTE:   usize = 56;

pub fn pack_notes(ppq: u16, pattern_length: u32, notes: &[Note]) -> Vec<u8> {
    let mut out = Vec::with_capacity(HEADER + notes.len() * NOTE);
    out.extend_from_slice(&(ppq as u32).to_le_bytes());
    out.extend_from_slice(&pattern_length.to_le_bytes());
    out.extend_from_slice(&(notes.len() as u32).to_le_bytes());
    for n in notes {
        out.extend_from_slice(&n.position.to_le_bytes());
        out.extend_from_slice(&n.length.to_le_bytes());
        out.extend_from_slice(&(n.channel as u32).to_le_bytes());
        out.extend_from_slice(&(n.key as u32).to_le_bytes());
        out.extend_from_slice(&(n.velocity as u32).to_le_bytes());
        out.extend_from_slice(&(n.pan as u32).to_le_bytes());
        out.extend_from_slice(&(n.fine_pitch as u32).to_le_bytes());
        out.extend_from_slice(&(n.release as u32).to_le_bytes());
        out.extend_from_slice(&(n.mod_x as u32).to_le_bytes());
        out.extend_from_slice(&(n.mod_y as u32).to_le_bytes());
        out.extend_from_slice(&(n.flags as u32).to_le_bytes());
        out.extend_from_slice(&(n.group as u32).to_le_bytes());
        out.extend_from_slice(&(n.unk14 as u32).to_le_bytes());
        out.extend_from_slice(&(n.repeat as u32).to_le_bytes());
    }
    out
}

pub fn unpack_notes(bytes: &[u8]) -> Result<(u16, u32, Vec<Note>), String> {
    if bytes.len() < HEADER {
        return Err(format!("too short: {} < {}", bytes.len(), HEADER));
    }
    let ppq = u32::from_le_bytes(bytes[0..4].try_into().unwrap()) as u16;
    let plen = u32::from_le_bytes(bytes[4..8].try_into().unwrap());
    let cnt = u32::from_le_bytes(bytes[8..12].try_into().unwrap()) as usize;
    let expected = HEADER + cnt * NOTE;
    if bytes.len() != expected {
        return Err(format!("bad size: {} != {} (cnt={})", bytes.len(), expected, cnt));
    }
    let mut notes = Vec::with_capacity(cnt);
    for i in 0..cnt {
        let base = HEADER + i * NOTE;
        let position   = u32::from_le_bytes(bytes[base..base+4].try_into().unwrap());
        let length     = u32::from_le_bytes(bytes[base+4..base+8].try_into().unwrap());
        let channel    = u32::from_le_bytes(bytes[base+8..base+12].try_into().unwrap()) as u16;
        let key        = u32::from_le_bytes(bytes[base+12..base+16].try_into().unwrap()) as u16;
        let velocity   = u32::from_le_bytes(bytes[base+16..base+20].try_into().unwrap()).min(127) as u8;
        let pan        = u32::from_le_bytes(bytes[base+20..base+24].try_into().unwrap()).min(128) as u8;
        let fine_pitch = u32::from_le_bytes(bytes[base+24..base+28].try_into().unwrap()).min(255) as u8;
        let release    = u32::from_le_bytes(bytes[base+28..base+32].try_into().unwrap()).min(255) as u8;
        let mod_x      = u32::from_le_bytes(bytes[base+32..base+36].try_into().unwrap()).min(255) as u8;
        let mod_y      = u32::from_le_bytes(bytes[base+36..base+40].try_into().unwrap()).min(255) as u8;
        let flags      = u32::from_le_bytes(bytes[base+40..base+44].try_into().unwrap()).min(0xFFFF) as u16;
        let group      = u32::from_le_bytes(bytes[base+44..base+48].try_into().unwrap()).min(255) as u8;
        let unk14      = u32::from_le_bytes(bytes[base+48..base+52].try_into().unwrap()).min(0xFFFF) as u16;
        let repeat     = u32::from_le_bytes(bytes[base+52..base+56].try_into().unwrap()).min(14) as u8;
        notes.push(Note {
            position, length, channel, key, velocity,
            pan, fine_pitch, release, mod_x, mod_y,
            flags, group, unk14, repeat,
        });
    }
    Ok((ppq, plen, notes))
}
