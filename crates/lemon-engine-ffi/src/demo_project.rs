// SPDX-License-Identifier: GPL-3.0-or-later
//! Demo project: six sampler channels playing a C major chord over
//! two octaves (C3-E3-G3-C4-E4-G4). One note per channel, whole bar
//! long, looping at 130 BPM / 96 PPQ. No sample files needed — each
//! `ChannelKind::Sampler` channel has `sample_path: None`, which the
//! engine renders through its built-in tone.

use os_model::{Channel, ChannelKind, Note, Pattern, Project};

pub fn build() -> Project {
    Project {
        ppq: 96,
        tempo: Some(130.0),
        channels: channels(),
        patterns: vec![Pattern {
            number: 1,
            notes: notes(),
            controls: vec![],
            ..Pattern::default()
        }],
        mixer: (0..6).map(|i| os_model::MixerTrack::new(i)).collect(),
        ..Project::default()
    }
}

fn channels() -> Vec<Channel> {
    const NAMES: [&str; 6] = ["Bass", "Low", "Mid", "Chord", "High", "Bell"];
    NAMES
        .iter()
        .enumerate()
        .map(|(i, name)| {
            let mut c = channel();
            c.index = i as u16;
            c.name = Some((*name).to_string());
            c.mixer_track = i as u16;
            c
        })
        .collect()
}

fn channel() -> Channel {
    Channel {
        id: os_model::Id::NONE,
        index: 0,
        kind: ChannelKind::Sampler,
        name: None,
        plugin: None,
        plugin_state: None,
        color: None,
        enabled: true,
        volume: 0.78125,
        pan: 0.0,
        pitch_cents: 0,
        mixer_track: 0,
        sample_path: None,
        layer_children: vec![],
        envelopes: Default::default(),
        rack: Default::default(),
        misc: Default::default(),
        sound: Default::default(),
    }
}

fn notes() -> Vec<Note> {
    // C major chord, two octaves.
    const KEYS: [u16; 6] = [48, 52, 55, 60, 64, 67];
    KEYS.iter()
        .enumerate()
        .map(|(i, &k)| Note {
            channel: i as u16,
            key: k,
            length: 96,
            ..Note::step(i as u16, 0)
        })
        .collect()
}
