// SPDX-License-Identifier: GPL-3.0-or-later
//! Built-in effect adapters via CLAP entries linked into this binary.

use std::path::PathBuf;
use std::sync::{Arc, Mutex, OnceLock};

use os_engine::{HostedEffect, SharedEffect};
use os_host::Library as _;
use os_host::Plugin as _;
use os_host::SharedPlugin;

/// Catalog of effects exposed to the picker.
///
/// Original 5 kept in shipped order for picker consistency. Seven newer
/// effects appended alphabetically: Balance, Chorus, Fast Distortion,
/// Soft Clipper, Stereo Enhancer, Stereo Shaper, Transient Processor.
pub const CATALOG: &[(&str, &str)] = &[
    ("reverb",          "Lemon Reverb"),
    ("delay",           "Lemon Delay"),
    ("limiter",         "Lemon Limiter"),
    ("eq",              "Lemon Parametric EQ"),
    ("filter",          "Lemon Filter"),
    ("balance",         "Lemon Balance"),
    ("chorus",          "Lemon Chorus"),
    ("fast_distortion", "Lemon Fast Distortion"),
    ("soft_clipper",    "Lemon Soft Clipper"),
    ("stereo_enhancer", "Lemon Stereo Enhancer"),
    ("stereo_shaper",   "Lemon Stereo Shaper"),
    ("transient",       "Lemon Transient Processor"),
];

static REVERB:     OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static DELAY:      OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static LIMITER:    OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static EQ:         OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static FILTER:     OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static BALANCE:    OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static CHORUS:     OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static FAST_DIST:  OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static SOFT_CLIP:  OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static STEREO_ENH: OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static STEREO_SHP: OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();
static TRANSIENT:  OnceLock<Option<Arc<os_clap::Library>>> = OnceLock::new();

fn lib_for(name: &str) -> Option<Arc<os_clap::Library>> {
    let path = PathBuf::from("linked-effect");
    let (cell, entry): (&OnceLock<Option<Arc<os_clap::Library>>>, *const std::ffi::c_void) = match name {
        "reverb"          => (&REVERB,     &lemon_reverb::LINKED_CLAP_ENTRY as *const _ as *const _),
        "delay"           => (&DELAY,      &lemon_delay::LINKED_CLAP_ENTRY as *const _ as *const _),
        "limiter"         => (&LIMITER,    &lemon_limiter::LINKED_CLAP_ENTRY as *const _ as *const _),
        "eq"              => (&EQ,         &lemon_parametric_eq_2::LINKED_CLAP_ENTRY as *const _ as *const _),
        "filter"          => (&FILTER,     &lemon_filter::LINKED_CLAP_ENTRY as *const _ as *const _),
        "balance"         => (&BALANCE,    &lemon_balance::LINKED_CLAP_ENTRY as *const _ as *const _),
        "chorus"          => (&CHORUS,     &lemon_chorus::LINKED_CLAP_ENTRY as *const _ as *const _),
        "fast_distortion" => (&FAST_DIST,  &lemon_fast_distortion::LINKED_CLAP_ENTRY as *const _ as *const _),
        "soft_clipper"    => (&SOFT_CLIP,  &lemon_soft_clipper::LINKED_CLAP_ENTRY as *const _ as *const _),
        "stereo_enhancer" => (&STEREO_ENH, &lemon_stereo_enhancer::LINKED_CLAP_ENTRY as *const _ as *const _),
        "stereo_shaper"   => (&STEREO_SHP, &lemon_stereo_shaper::LINKED_CLAP_ENTRY as *const _ as *const _),
        "transient"       => (&TRANSIENT,  &lemon_transient_processor::LINKED_CLAP_ENTRY as *const _ as *const _),
        _ => return None,
    };
    cell.get_or_init(|| unsafe {
        match os_clap::Library::from_entry(entry, &path) {
            Ok(l) => Some(l),
            Err(e) => {
                log::error!("linked effect {name}: {e}");
                None
            }
        }
    }).clone()
}

/// Wraps `SharedPlugin` into `HostedEffect` for `Engine::set_effect`.
struct PluginEffect(SharedPlugin);

impl HostedEffect for PluginEffect {
    fn process_fx(&mut self, rate: u32, left: &mut [f32], right: &mut [f32]) {
        if let Ok(mut p) = self.0.lock() {
            p.process_fx(rate, left, right);
        }
    }
    fn process_fx_keyed(&mut self, rate: u32, left: &mut [f32], right: &mut [f32],
                        key: (&[f32], &[f32])) {
        if let Ok(mut p) = self.0.lock() {
            p.process_fx_keyed(rate, left, right, key);
        }
    }
    fn reset_fx(&mut self) {
        if let Ok(mut p) = self.0.lock() { p.reset_fx(); }
    }
    fn reported_delay_fx(&self) -> u32 {
        self.0.lock().map(|p| p.reported_delay_fx()).unwrap_or(0)
    }
    fn param_fx(&mut self, offset: u32, index: u32, value: f64) {
        if let Ok(mut p) = self.0.lock() { p.param_fx(offset, index, value); }
    }
}

/// Passthrough used to clear a slot.
pub struct NoOpEffect;
impl HostedEffect for NoOpEffect {
    fn process_fx(&mut self, _rate: u32, _left: &mut [f32], _right: &mut [f32]) {}
}

pub fn create(name: &str, rate: u32) -> Option<SharedEffect> {
    let lib = lib_for(name)?;
    let desc = lib.plugins.first()?.clone();
    let plugin = lib.create(&desc.id).ok()?;
    if let Ok(mut p) = plugin.lock() {
        if !p.activate(rate) {
            log::warn!("effect {name}: activate({rate}) returned false");
        }
    }
    Some(Arc::new(Mutex::new(PluginEffect(plugin))))
}

// ─── State helpers ────────────────────────────────────────────────

/// Snapshot the current effect state (for save).
pub fn snapshot_state(plugin: &SharedPlugin) -> Option<Vec<u8>> {
    let guard = plugin.lock().ok()?;
    guard.save_state()
}

/// Restore state into a live plugin instance (for load).
pub fn restore_state(plugin: &SharedPlugin, data: &[u8]) -> bool {
    plugin.lock().map(|mut p| p.load_state(data)).unwrap_or(false)
}

/// Same as `create` but also returns the underlying `SharedPlugin`
/// so callers can call `save_state` / `load_state` on it.
pub fn create_with_plugin(name: &str, rate: u32) -> Option<(SharedEffect, SharedPlugin)> {
    let lib = lib_for(name)?;
    let desc = lib.plugins.first()?.clone();
    let plugin = lib.create(&desc.id).ok()?;
    if let Ok(mut p) = plugin.lock() {
        if !p.activate(rate) {
            log::warn!("effect {name}: activate({rate}) returned false");
        }
    }
    let fx: SharedEffect = Arc::new(Mutex::new(PluginEffect(plugin.clone())));
    Some((fx, plugin))
}
