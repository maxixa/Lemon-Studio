// SPDX-License-Identifier: GPL-3.0-or-later
//! Built-in synth instruments — direct DSP-engine adapters.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use os_engine::{Instrument, SharedInstrument};

pub const CATALOG: &[(&str, &str)] = &[
    ("trio",    "Lemon Trio"),
    ("citrus",  "Citrus"),
    ("sampler", "Lemon Sampler"),
    ("drum",    "Lemon Drum Sampler"),
];

// ─── Trio ────────────────────────────────────────────────────────────
struct TrioAdapter {
    engine: lemon_3x_osc::dsp::Engine,
    params: lemon_3x_osc::params::Osc3Params,
}

impl TrioAdapter {
    fn new(rate: u32) -> Self {
        Self {
            engine: lemon_3x_osc::dsp::Engine::new(rate as f32),
            params: lemon_3x_osc::params::Osc3Params::default(),
        }
    }
    fn blk(&self) -> lemon_3x_osc::dsp::Block {
        lemon_3x_osc::block(&self.params, 0.0, 0.0)
    }
}

impl Instrument for TrioAdapter {
    fn note_on(&mut self, _offset: u32, key: u8, velocity: f32) {
        let b = self.blk();
        self.engine.note_on(key, velocity, None, &b);
    }
    fn note_off(&mut self, _offset: u32, key: u8) { self.engine.note_off(key); }
    fn process(&mut self, _rate: u32, left: &mut [f32], right: &mut [f32]) {
        let b = self.blk();
        self.engine.render(left, right, &b);
    }
}

// ─── Citrus ──────────────────────────────────────────────────────────
struct CitrusAdapter {
    engine: citrus::engine::Citrus,
    ids: HashMap<u8, u64>,
}

impl CitrusAdapter {
    fn new(rate: u32) -> Self {
        Self { engine: citrus::engine::Citrus::new(rate), ids: HashMap::new() }
    }
}

impl Instrument for CitrusAdapter {
    fn note_on(&mut self, _offset: u32, key: u8, velocity: f32) {
        let n = citrus::engine::NoteOn {
            levels: Default::default(),
            velocity,
            release: 0.5,
        };
        let id = self.engine.note_on(n);
        self.ids.insert(key, id);
    }
    fn note_off(&mut self, _offset: u32, key: u8) {
        if let Some(id) = self.ids.remove(&key) { self.engine.note_off(id); }
    }
    fn process(&mut self, _rate: u32, left: &mut [f32], right: &mut [f32]) {
        self.engine.render(left, right);
    }
}

// ─── Sampler ─────────────────────────────────────────────────────────
struct SamplerAdapter {
    engine: lemon_sampler::dsp::Engine,
    params: lemon_sampler::params::SamplerParams,
}

impl SamplerAdapter {
    fn new(rate: u32) -> Self {
        Self {
            engine: lemon_sampler::dsp::Engine::new(rate as f32),
            params: lemon_sampler::params::SamplerParams::default(),
        }
    }
    fn blk(&self) -> lemon_sampler::dsp::Block {
        lemon_sampler::block(&self.params, 120.0, 0.0)
    }
}

impl Instrument for SamplerAdapter {
    fn note_on(&mut self, _offset: u32, key: u8, velocity: f32) {
        let b = self.blk();
        self.engine.note_on(key, velocity, &b);
    }
    fn note_off(&mut self, _offset: u32, key: u8) {
        let b = self.blk();
        self.engine.note_off(key, &b);
    }
    fn process(&mut self, _rate: u32, left: &mut [f32], right: &mut [f32]) {
        let b = self.blk();
        self.engine.render(left, right, &b);
    }
}

// ─── Drum Sampler ────────────────────────────────────────────────────
struct DrumAdapter {
    engine: lemon_drum_sampler::dsp::Engine,
}

impl DrumAdapter {
    fn new(rate: u32) -> Self {
        Self { engine: lemon_drum_sampler::dsp::Engine::new(rate as f32) }
    }
}

impl Instrument for DrumAdapter {
    fn note_on(&mut self, _offset: u32, key: u8, velocity: f32) {
        self.engine.note_on(key, velocity, None);
    }
    fn note_off(&mut self, _offset: u32, _key: u8) {}
    fn process(&mut self, _rate: u32, left: &mut [f32], right: &mut [f32]) {
        self.engine.render(left, right);
    }
}

// ─── Factory ─────────────────────────────────────────────────────────
pub fn create(name: &str, rate: u32) -> Option<SharedInstrument> {
    let inst: SharedInstrument = match name {
        "trio"    => Arc::new(Mutex::new(TrioAdapter::new(rate))),
        "citrus"  => Arc::new(Mutex::new(CitrusAdapter::new(rate))),
        "sampler" => Arc::new(Mutex::new(SamplerAdapter::new(rate))),
        "drum"    => Arc::new(Mutex::new(DrumAdapter::new(rate))),
        _ => return None,
    };
    Some(inst)
}
