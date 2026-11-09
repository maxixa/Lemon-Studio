// SPDX-License-Identifier: GPL-3.0-or-later
//! Handle wrapping a loaded `os_engine::Engine`, its `Project`, and a
//! running `AudioDevice`.

use std::path::PathBuf;
use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU32, Ordering};

use os_host::Plugin as HostPlugin;
use os_engine::{Engine, SharedEffect, PlayMode, SampleLibrary};
use os_flp::{project, FlpFile};
use os_model::Project;

use crate::audio::AudioDevice;

#[derive(Clone)]
pub struct Snapshot {
    pub notes: Option<Vec<u8>>,
    pub arrangement: Option<Vec<u8>>,
    pub tempo: f64,
    pub user_muted: HashSet<u16>,
    pub soloed: HashSet<u16>,
    /// Every effect slot's snapshot: (track, slot, name, plugin-state, bypassed).
    pub effects: Vec<(u16, u8, String, Vec<u8>, bool)>,
    /// Project swing preset; `None` = no swing. Captured so undo/redo
    /// round-trips swing set by `set_project_swing`.
    pub swing: Option<u8>,
    /// Per-pattern explicit length; `None` = Auto. Captured per-number
    /// so undo works across pattern switches.
    pub pattern_lengths: Vec<(u16, Option<u32>)>,
    /// Pattern number whose notes `notes` represents, captured at snapshot
    /// time. `restore_snapshot` uses this to write notes back to the right
    /// pattern after a pattern switch, instead of always pattern 1.
    pub notes_pattern: u16,
    /// Time signature (numerator, steps_per_beat); `None` = default 4/4.
    pub bar: Option<(u16, u16)>,
    /// Arrangement number current at capture time. Restored on undo/redo
    /// so set_arrangement / add_arrangement revert the switch itself,
    /// not just the clips.
    pub current_arrangement: Option<u16>,
    /// Full list of arrangements captured by value. Restoring it
    /// makes add / delete / rename / switch undoable — clips wire
    /// above only covers the current arrangement.
    pub arrangements: Option<Vec<os_model::Arrangement>>,
    /// Full channel list captured by value. Restoring it makes
    /// add_sampler_channel / add_sample_channel / delete_channel undoable.
    /// Restored before the mute/solo loop in restore_snapshot so that
    /// loop iterates the correct post-restore channel set.
    pub channels: Option<Vec<os_model::Channel>>,
    /// Notes of every pattern, keyed by pattern number. `None` when
    /// the project has only one pattern (no cross-pattern remaps can
    /// occur). Restored after the current-pattern `notes` field so
    /// reorder_channel's cross-pattern remap undoes correctly.
    pub all_pattern_notes: Option<Vec<(u16, Vec<u8>)>>,
    /// Full list of automations captured by value. Restoring it
    /// makes reorder_channel's automation.channel remap undoable.
    pub automations: Option<Vec<os_model::Automation>>,
    /// Full pattern list captured by value. Restoring it makes
    /// add_pattern / duplicate_pattern / rename_pattern / delete_pattern
    /// undoable. Redundant with `notes`/`all_pattern_notes`/`pattern_lengths`
    /// for notes content, but authoritative for the pattern LIST itself —
    /// without it, undoing add_pattern cannot remove the added pattern.
    pub patterns: Option<Vec<os_model::Pattern>>,
    /// Pattern number current at capture time. Restored after `patterns`
    /// so undoing add_pattern returns to the previous current pattern.
    pub current_pattern: Option<u16>,
}

pub struct EngineHandle {
    pub engine: Option<Arc<Mutex<Engine>>>,
    pub device: Option<AudioDevice>,
    pub project: Option<Arc<Project>>,
    pub mode: PlayMode,
    pub sample_rate: u32,
    pub factory: Option<PathBuf>,
    pub user_muted: HashSet<u16>,
    pub soloed: HashSet<u16>,
    pub metronome_switch: Option<Arc<os_engine::metronome::Switch>>,
    pub count_in_bars: u32,
    pub loop_range: Option<(u64, u64)>,
    pub original_bytes: Option<Vec<u8>>,
    pub channel_peaks: HashMap<u16, Vec<f32>>,
    pub effects: HashMap<(u16, u8), (SharedEffect, os_host::SharedPlugin)>,
    pub master_peak: Arc<AtomicU32>,
    pub sample_map: HashMap<String, String>,
    pub bypassed: HashSet<(u16, u8)>,
    pub undo_stack: Vec<Snapshot>,
    pub redo_stack: Vec<Snapshot>,
}

impl EngineHandle {
    pub fn new() -> Self {
        Self {
            engine: None,
            device: None,
            project: None,
            mode: PlayMode::Song,
            sample_rate: 48_000,
            factory: None,
            user_muted: HashSet::new(),
            soloed: HashSet::new(),
            metronome_switch: None,
            count_in_bars: 0,
            loop_range: None,
            original_bytes: None,
            channel_peaks: HashMap::new(),
            effects: HashMap::new(),
            master_peak: Arc::new(AtomicU32::new(0)),
            sample_map: HashMap::new(),
            bypassed: HashSet::new(),
            undo_stack: Vec::new(),
            redo_stack: Vec::new(),
        }
    }

    pub fn load_project(&mut self, path: &str, factory: Option<PathBuf>) -> Result<(), String> {
        let bytes = std::fs::read(path).map_err(|e| format!("{path}: {e}"))?;
        self.load_project_from_bytes(&bytes, factory)
    }

    pub fn load_project_fd(&mut self, fd: i32, factory: Option<PathBuf>) -> Result<(), String> {
        use std::io::Read;
        use std::os::unix::io::FromRawFd;
        if fd < 0 {
            return Err(format!("invalid fd: {fd}"));
        }
        // SAFETY: caller (Kotlin) keeps the fd open until this returns.
        // ManuallyDrop prevents Rust from closing the caller-owned fd.
        let mut file = std::mem::ManuallyDrop::new(unsafe { std::fs::File::from_raw_fd(fd) });
        let mut bytes = Vec::new();
        file.read_to_end(&mut bytes)
            .map_err(|e| format!("read(fd={fd}): {e}"))?;
        self.load_project_from_bytes(&bytes, factory)
    }

    fn load_project_from_bytes(
        &mut self,
        bytes: &[u8],
        factory: Option<PathBuf>,
    ) -> Result<(), String> {
        if self.device.is_some() {
            return Err("stop the audio device before loading a project".into());
        }
        let file = FlpFile::parse(bytes).map_err(|e| format!("flp: {e}"))?;
        let project_data = project::load(&file).map_err(|e| format!("project: {e}"))?;

        let rate = default_sample_rate();
        self.sample_rate = rate;

        // Derive any fx-transformed samples the loaded project needs
        self.sample_map.clear();
        let mut project_data = project_data;
        self.apply_fx_to_paths(&mut project_data);

        let mut library = SampleLibrary::new(factory.or_else(|| self.factory.clone()));
        let engine = Engine::new(&project_data, &mut library, rate, self.mode);

        let r = &engine.report;
        log::info!(
            "project loaded: rate={rate} channels={} effects_active={} sample_failures={}",
            project_data.channels.len(),
            r.effects_active,
            library.failures.len()
        );
        for (p, why) in &library.failures {
            log::warn!("missing sample: {p} ({why})");
        }

        self.engine = Some(Arc::new(Mutex::new(engine)));
        self.project = Some(Arc::new(project_data));
        self.original_bytes = Some(bytes.to_vec());
        self.effects.clear();
        self.bypassed.clear();
        self.channel_peaks.clear();
        self.populate_channel_peaks();
        Ok(())
    }

    pub fn load_demo(&mut self) -> Result<(), String> {
        if self.device.is_some() {
            return Err("stop the audio device before loading a project".into());
        }
        self.mode = PlayMode::Pattern(1);
        let project_data = crate::demo_project::build();
        let rate = default_sample_rate();
        self.sample_rate = rate;
        let mut library = SampleLibrary::new(None);
        let mut engine = Engine::new(&project_data, &mut library, rate, self.mode);
        engine.set_looping(true);

        log::info!(
            "demo project loaded: rate={rate} tempo={:?} ppq={} channels={} notes={} looping=true",
            project_data.tempo,
            project_data.ppq,
            project_data.channels.len(),
            project_data.patterns.first().map(|p| p.notes.len()).unwrap_or(0)
        );

        self.engine = Some(Arc::new(Mutex::new(engine)));
        self.project = Some(Arc::new(project_data));
        self.original_bytes = None;
        self.effects.clear();
        self.bypassed.clear();
        self.channel_peaks.clear();
        self.populate_channel_peaks();
        Ok(())
    }

    pub fn set_looping(&mut self, on: bool) -> Result<(), String> {
        let Some(arc) = self.engine.as_ref() else {
            return Err("no engine loaded".into());
        };
        let mut guard = arc.lock().map_err(|_| "engine mutex poisoned")?;
        guard.set_looping(on);
        Ok(())
    }

    pub fn seek(&mut self, frames: u64) -> Result<(), String> {
        let Some(arc) = self.engine.as_ref() else {
            return Err("no engine loaded".into());
        };
        let mut guard = arc.lock().map_err(|_| "engine mutex poisoned")?;
        guard.seek(frames);
        Ok(())
    }

    /// Pack the currently loaded project's pattern #1 (ppq, length, notes).
    pub fn notes_bytes(&self) -> Option<Vec<u8>> {
        let project = self.project.as_ref()?;
        let num = project.current_pattern.unwrap_or(1);
        let pattern = project.patterns.iter().find(|p| p.number == num)?;
        let plen = pattern.length.unwrap_or(0);
        Some(crate::piano::pack_notes(project.ppq, plen, &pattern.notes))
    }

    /// Replace pattern #1's notes with the given buffer and rebuild the Engine.
    pub fn commit_notes(&mut self, bytes: &[u8]) -> Result<(), String> {
        self.push_snapshot();
        let (_ppq, _plen, new_notes) = crate::piano::unpack_notes(bytes)?;
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();

        let num = new_project.current_pattern.unwrap_or(1);
        let idx = new_project.patterns.iter().position(|p| p.number == num)
            .ok_or_else(|| format!("pattern {num} not found"))?;
        new_project.patterns[idx].notes = new_notes;

        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);

        // Preserve transport position from the running engine (if any).
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }

        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("notes committed: {} notes", idx_count(self.project.as_ref()));
        Ok(())
    }

    /// Bitmask of the 16 steps `channel` occupies in pattern #1.
    pub fn step_mask_bytes(&self, channel: u16) -> Vec<u8> {
        self.project.as_ref().map(|p| {
            let num = p.current_pattern.unwrap_or(1);
            crate::rack::mask_bytes(p, num, channel)
        }).unwrap_or_default()
    }

    pub fn step_count_for_current_pattern(&self) -> u32 {
        self.project.as_ref().map(|p| {
            let num = p.current_pattern.unwrap_or(1);
            crate::rack::step_count(p, num)
        }).unwrap_or(16)
    }

    /// Toggle one step of `channel`. Mutates the project copy, rebuilds
    /// the Engine, and returns the new step state (true = on).
    pub fn toggle_step(&mut self, channel: u16, step: u32) -> Result<bool, String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let num = new_project.current_pattern.unwrap_or(1);
        let new_state = crate::rack::toggle(&mut new_project, num, channel, step)
            .ok_or_else(|| format!("step {step} out of range"))?;

        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        Ok(new_state)
    }

    pub fn arrangement_bytes(&self) -> Option<Vec<u8>> {
        self.project.as_ref().map(|p| crate::playlist::pack(p))
    }

    pub fn pattern_count(&self) -> u32 {
        self.project.as_ref().map(|p| p.patterns.len() as u32).unwrap_or(0)
    }

    pub fn pattern_name(&self, i: u32) -> String {
        self.project.as_ref()
            .and_then(|p| p.patterns.get(i as usize))
            .map(|p| p.name.clone().unwrap_or_else(|| format!("Pattern {}", p.number)))
            .unwrap_or_default()
    }

    pub fn is_song_mode(&self) -> bool { matches!(self.mode, PlayMode::Song) }

    /// Commit an entire new clip list (rebuild engine, preserve position).
    pub fn commit_arrangement(&mut self, bytes: &[u8]) -> Result<(), String> {
        self.push_snapshot();
        self.commit_arrangement_inner(bytes)
    }

    /// Same as `commit_arrangement` but WITHOUT pushing a snapshot.
    /// Used by flows that already pushed a snapshot for a preceding
    /// mutation (e.g. add_audio_clip_channel) so the whole operation
    /// reverts with a single undo. If the caller is not part of a
    /// larger atomic operation, use `commit_arrangement` instead — a
    /// silent commit on its own is not undoable.
    pub fn commit_arrangement_silent(&mut self, bytes: &[u8]) -> Result<(), String> {
        self.commit_arrangement_inner(bytes)
    }

    fn commit_arrangement_inner(&mut self, bytes: &[u8]) -> Result<(), String> {
        let wire = crate::playlist::unpack(bytes)?;
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        crate::playlist::commit(&mut new_project, wire)?;

        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        Ok(())
    }

    /// Switch the current arrangement. Refuses if `num` doesn't exist.
    /// Rebuilds the engine like other project-setting methods.
    /// Create a new empty arrangement and switch to it.
    /// Returns the new arrangement's number.
    pub fn add_arrangement(&mut self) -> Result<u16, String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let new_num = new_project.arrangements.iter().map(|a| a.number).max().unwrap_or(0) + 1;
        new_project.arrangements.push(os_model::Arrangement {
            id: os_model::Id::NONE,
            number: new_num,
            name: Some(format!("Arrangement {new_num}")),
            clips: Vec::new(),
            tracks: Vec::new(),
            markers: Vec::new(),
        });
        new_project.current_arrangement = Some(new_num);
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("added arrangement {new_num}");
        Ok(new_num)
    }

    /// Rename an arrangement. Empty name clears to `None` (falls back to "Arrangement N").
    pub fn rename_arrangement(&mut self, num: u16, name: String) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let arr = new_project.arrangements.iter_mut()
            .find(|a| a.number == num)
            .ok_or_else(|| format!("arrangement {num} not found"))?;
        arr.name = if name.trim().is_empty() { None } else { Some(name.trim().to_string()) };
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("renamed arrangement {num}");
        Ok(())
    }

    /// Delete an arrangement. Refuses if it's the last one. If the current
    /// arrangement is being deleted, switches to the first remaining.
    pub fn delete_arrangement(&mut self, num: u16) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        if new_project.arrangements.len() <= 1 {
            return Err("cannot delete the last arrangement".to_string());
        }
        let idx = new_project.arrangements.iter().position(|a| a.number == num)
            .ok_or_else(|| format!("arrangement {num} not found"))?;
        new_project.arrangements.remove(idx);
        if new_project.current_arrangement == Some(num) {
            new_project.current_arrangement = new_project.arrangements.first().map(|a| a.number);
        }
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("deleted arrangement {num}");
        Ok(())
    }

    pub fn set_arrangement(&mut self, num: u16) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        if !project_arc.arrangements.iter().any(|a| a.number == num) {
            return Err(format!("arrangement {num} not found"));
        }
        let mut new_project = (**project_arc).clone();
        new_project.current_arrangement = Some(num);
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("current arrangement set to {num}");
        Ok(())
    }

    pub fn set_play_mode(&mut self, song: bool) -> Result<(), String> {
        let new_mode = if song { PlayMode::Song }
            else { PlayMode::Pattern(self.project.as_ref()
                .and_then(|p| p.current_pattern).unwrap_or(1)) };
        if self.mode == new_mode { return Ok(()); }
        self.mode = new_mode;
        let project_arc = self.project.as_ref().ok_or("no project loaded")?;
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&**project_arc);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        Ok(())
    }

    pub fn mixer_bytes(&self) -> Option<Vec<u8>> {
        self.project.as_ref().map(|p| crate::mixer::pack_tracks(p))
    }

    pub fn mixer_track_count(&self) -> u32 {
        self.project.as_ref().map(|p| crate::mixer::track_count(p)).unwrap_or(0)
    }

    pub fn mixer_set_volume(&mut self, track: u32, v: f64) -> Result<(), String> {
        let edit = os_engine::LiveEdit::MixerVolume(track as usize, v);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() { guard.apply_edit(edit); }
        }
        if let Some(project_arc) = self.project.as_mut() {
            let mut p = (**project_arc).clone();
            edit.apply_to(&mut p);
            self.project = Some(Arc::new(p));
        }
        Ok(())
    }

    pub fn mixer_set_pan(&mut self, track: u32, v: f64) -> Result<(), String> {
        let edit = os_engine::LiveEdit::MixerPan(track as usize, v);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() { guard.apply_edit(edit); }
        }
        if let Some(project_arc) = self.project.as_mut() {
            let mut p = (**project_arc).clone();
            edit.apply_to(&mut p);
            self.project = Some(Arc::new(p));
        }
        Ok(())
    }

    pub fn tempo(&self) -> f64 {
        self.project.as_ref()
            .and_then(|p| p.tempo)
            .unwrap_or(140.0)
    }

    /// Change the project's tempo. Rebuilds the engine to apply it live
    /// (position preserved via `continue_from`). Rejects out-of-range.
    pub fn set_tempo(&mut self, bpm: f64) -> Result<(), String> {
        self.push_snapshot();
        if !(20.0..=999.0).contains(&bpm) {
            return Err(format!("tempo {bpm:.2} out of range 20..=999"));
        }
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        new_project.tempo = Some(bpm);

        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("tempo set to {bpm:.2}");
        Ok(())
    }

    /// Effective enable state (post user_mute & solo).
    pub fn channel_enabled(&self, ch: u16) -> bool {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.enabled)
            .unwrap_or(true)
    }

    pub fn channel_muted(&self, ch: u16) -> bool {
        self.user_muted.contains(&ch)
    }

    pub fn channel_soloed(&self, ch: u16) -> bool {
        self.soloed.contains(&ch)
    }

    pub fn set_channel_muted(&mut self, ch: u16, on: bool) -> Result<(), String> {
        self.push_snapshot();
        if on { self.user_muted.insert(ch); } else { self.user_muted.remove(&ch); }
        self.apply_mute_state()
    }

    pub fn set_channel_soloed(&mut self, ch: u16, on: bool) -> Result<(), String> {
        self.push_snapshot();
        if on { self.soloed.insert(ch); } else { self.soloed.remove(&ch); }
        self.apply_mute_state()
    }

    /// Recompute enabled from (user_muted, soloed) and rebuild the engine.
    fn apply_mute_state(&mut self) -> Result<(), String> {
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let any_solo = !self.soloed.is_empty();
        for (i, ch) in new_project.channels.iter_mut().enumerate() {
            let idx = i as u16;
            let user_on = !self.user_muted.contains(&idx);
            let solo_ok = !any_solo || self.soloed.contains(&idx);
            ch.enabled = user_on && solo_ok;
        }
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        Ok(())
    }

    /// Current factory folder (samples for sampler channels).
    pub fn factory(&self) -> Option<&PathBuf> {
        self.factory.as_ref()
    }

    /// Set the factory folder. If a project is loaded, rebuild the engine
    /// against the new folder and preserve transport position.
    pub fn set_factory(&mut self, path: Option<PathBuf>) -> Result<(), String> {
        self.factory = path;
        if self.project.is_some() {
            self.reload_with_factory()?;
        }
        Ok(())
    }

    /// Rebuild the current engine with the current factory setting.
    pub fn reload_with_factory(&mut self) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let rate = self.sample_rate;
        let mut library = SampleLibrary::new(self.factory.clone());
        let mut new_engine = Engine::new(&project_arc, &mut library, rate, self.mode);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.channel_peaks.clear();
        self.populate_channel_peaks();
        let missing = library.failures.len();
        log::info!(
            "factory reload: factory={:?} missing_samples={missing}",
            self.factory
        );
        for (p, why) in &library.failures {
            log::warn!("missing sample: {p} ({why})");
        }
        Ok(())
    }

    /// Build a fresh engine and re-attach the metronome switch if present.
    /// Every rebuild path must go through this so toggling metronome survives
    /// note/arrangement/tempo/mute edits.
    fn new_engine(&self, project: &os_model::Project) -> os_engine::Engine {
        let mut library = os_engine::SampleLibrary::new(self.factory.clone());
        let mut e = os_engine::Engine::new(project, &mut library, self.sample_rate, self.mode);
        if let Some(sw) = self.metronome_switch.as_ref() {
            e.set_metronome(sw.clone());
        }
        e.set_count_in(self.count_in_bars);
        if let Some((a, b)) = self.loop_range {
            e.set_loop_range(Some((a, b)));
        }
        // Re-attach active effects (skipping bypassed slots)
        for ((t, s), (fx, _plugin)) in &self.effects {
            if !self.bypassed.contains(&(*t, *s)) {
                e.set_effect(*t as usize, *s as usize, fx.clone());
            }
        }
    // Re-attach built-in instruments (Project.channels[ch].plugin is the source of truth).
    // Every rebuild — commit_notes, set_pattern_num, set_pattern_length, etc. — lands here;
    // without this loop a channel reattached as Plugin goes silent after its first edit,
    // because instruments live only inside the live Engine, not in Project (samples do, so
    // the built-in tone survives rebuilds without this code).
    for (ch_idx, c) in project.channels.iter().enumerate() {
        if matches!(c.kind, os_model::ChannelKind::Plugin) {
            if let Some(name) = c.plugin.as_deref() {
                if let Some(inst) = crate::instruments::create(name, self.sample_rate) {
                    e.set_instrument(ch_idx, inst);
                } else {
                    log::warn!("new_engine: unknown instrument {name} on channel {ch_idx}");
                }
            }
        }
    }

        e
    }

    /// Attach a freshly created metronome switch on first call.
    pub fn ensure_metronome(&mut self) {
        if self.metronome_switch.is_none() {
            let sw = Arc::new(os_engine::metronome::Switch::default());
            if let Some(arc) = self.engine.as_ref() {
                if let Ok(mut guard) = arc.lock() {
                    guard.set_metronome(sw.clone());
                }
            }
            self.metronome_switch = Some(sw);
        }
    }

    /// Toggle metronome. Returns new state or None if no switch exists yet.
    pub fn set_metronome_enabled(&mut self, on: bool) -> Result<bool, String> {
        self.ensure_metronome();
        let sw = self.metronome_switch.as_ref().ok_or("metronome init failed")?;
        sw.set_on(on);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                guard.set_metronome(sw.clone());
            }
        }
        log::info!("metronome = {on}");
        Ok(on)
    }

    /// Metronome sound as an index into `Sound::ALL` (0..=4).
    pub fn metronome_sound_index(&self) -> u32 {
        let Some(sw) = self.metronome_switch.as_ref() else { return 1; };
        let s = sw.sound();
        os_engine::metronome::Sound::ALL.iter().position(|&x| x == s).unwrap_or(1) as u32
    }

    pub fn metronome_volume_db(&self) -> i8 {
        self.metronome_switch.as_ref().map(|s| s.volume()).unwrap_or(0)
    }

    /// Set sound by index into `Sound::ALL`. Creates switch if missing.
    pub fn set_metronome_sound(&mut self, idx: u32) -> Result<(), String> {
        self.ensure_metronome();
        let sw = self.metronome_switch.as_ref().ok_or("metronome init failed")?;
        let s = os_engine::metronome::Sound::ALL
            .get(idx as usize).copied()
            .ok_or_else(|| format!("sound index {idx} out of range"))?;
        sw.set_sound(s);
        log::info!("metronome sound = {:?}", s);
        Ok(())
    }

    /// Set volume in dB. Range -24..=12 per `VOLUME_MIN`/`VOLUME_MAX`.
    pub fn set_metronome_volume(&mut self, db: i8) -> Result<(), String> {
        self.ensure_metronome();
        let sw = self.metronome_switch.as_ref().ok_or("metronome init failed")?;
        let clamped = db.clamp(
            os_engine::metronome::VOLUME_MIN,
            os_engine::metronome::VOLUME_MAX,
        );
        sw.set_volume(clamped);
        log::info!("metronome volume = {clamped} dB");
        Ok(())
    }

    pub fn metronome_enabled(&self) -> bool {
        self.metronome_switch.as_ref().map(|s| s.on()).unwrap_or(false)
    }

    // ─── Undo/redo ──────────────────────────────────────────────
    const UNDO_LIMIT: usize = 50;

    /// Capture the effect-slot state for the current project:
    /// one entry per populated mixer slot.
    fn capture_swing(&self) -> Option<u8> {
        self.project.as_ref().and_then(|p| p.swing)
    }

    fn capture_pattern_lengths(&self) -> Vec<(u16, Option<u32>)> {
        self.project
            .as_ref()
            .map(|p| p.patterns.iter().map(|pat| (pat.number, pat.length)).collect())
            .unwrap_or_default()
    }

    /// Notes of every pattern, packed by the same piano wire the current
    /// `notes` field uses. `None` when the project has only one pattern —
    /// single-pattern projects pay no cost and their snapshots stay byte-
    /// identical to previous versions.
    fn capture_all_pattern_notes(&self) -> Option<Vec<(u16, Vec<u8>)>> {
        let project = self.project.as_ref()?;
        if project.patterns.len() <= 1 {
            return None;
        }
        let ppq = project.ppq;
        Some(project.patterns.iter().map(|p| {
            let plen = p.length.unwrap_or(0);
            (p.number, crate::piano::pack_notes(ppq, plen, &p.notes))
        }).collect())
    }

    fn capture_effects(&self) -> Vec<(u16, u8, String, Vec<u8>, bool)> {
        let mut out = Vec::new();
        let Some(project) = self.project.as_ref() else { return out; };
        for (t, track) in project.mixer.iter().enumerate() {
            for (s, slot) in track.slots.iter().enumerate() {
                let Some(sp) = slot.as_ref() else { continue; };
                let key = (t as u16, s as u8);
                let state = self.effects.get(&key)
                    .and_then(|(_, p)| crate::effects::snapshot_state(p))
                    .unwrap_or_else(|| sp.state.clone());
                let bypassed = self.bypassed.contains(&key);
                out.push((key.0, key.1, sp.name.clone(), state, bypassed));
            }
        }
        out
    }


    
    /// Capture current editable state. Called before every mutation.
    pub fn push_snapshot(&mut self) {
        if self.project.is_none() {
            return;
        }
        let effects = self.capture_effects();
        self.undo_stack.push(Snapshot {
            notes: self.notes_bytes(),
            arrangement: self.arrangement_bytes(),
            tempo: self.tempo(),
            user_muted: self.user_muted.clone(),
            soloed: self.soloed.clone(),
            effects,
            swing: self.capture_swing(),
            pattern_lengths: self.capture_pattern_lengths(),
            bar: self.project.as_ref().and_then(|p| p.bar),
            current_arrangement: self.project.as_ref().and_then(|p| p.current_arrangement),
            arrangements: self.project.as_ref().map(|p| p.arrangements.clone()),
            notes_pattern: self.project.as_ref()
                .and_then(|p| p.current_pattern)
                .unwrap_or(1),
            channels: self.project.as_ref().map(|p| p.channels.clone()),
            all_pattern_notes: self.capture_all_pattern_notes(),
            automations: self.project.as_ref().map(|p| p.automations.clone()),
            patterns: self.project.as_ref().map(|p| p.patterns.clone()),
            current_pattern: self.project.as_ref().and_then(|p| p.current_pattern),
        });
        self.redo_stack.clear();
        if self.undo_stack.len() > Self::UNDO_LIMIT {
            self.undo_stack.remove(0);
        }
    }

    pub fn can_undo(&self) -> bool { !self.undo_stack.is_empty() }
    pub fn can_redo(&self) -> bool { !self.redo_stack.is_empty() }

    pub fn undo(&mut self) -> Result<bool, String> {
        let snap = self.undo_stack.pop().ok_or("nothing to undo")?;
        let current = Snapshot {
            notes: self.notes_bytes(),
            arrangement: self.arrangement_bytes(),
            tempo: self.tempo(),
            user_muted: self.user_muted.clone(),
            soloed: self.soloed.clone(),
            effects: self.capture_effects(),
            swing: self.capture_swing(),
            pattern_lengths: self.capture_pattern_lengths(),
            bar: self.project.as_ref().and_then(|p| p.bar),
            current_arrangement: self.project.as_ref().and_then(|p| p.current_arrangement),
            arrangements: self.project.as_ref().map(|p| p.arrangements.clone()),
            notes_pattern: self.project.as_ref()
                .and_then(|p| p.current_pattern)
                .unwrap_or(1),
            channels: self.project.as_ref().map(|p| p.channels.clone()),
            all_pattern_notes: self.capture_all_pattern_notes(),
            automations: self.project.as_ref().map(|p| p.automations.clone()),
            patterns: self.project.as_ref().map(|p| p.patterns.clone()),
            current_pattern: self.project.as_ref().and_then(|p| p.current_pattern),
        };
        self.redo_stack.push(current);
        self.restore_snapshot(snap)?;
        Ok(true)
    }

    pub fn redo(&mut self) -> Result<bool, String> {
        let snap = self.redo_stack.pop().ok_or("nothing to redo")?;
        let current = Snapshot {
            notes: self.notes_bytes(),
            arrangement: self.arrangement_bytes(),
            tempo: self.tempo(),
            user_muted: self.user_muted.clone(),
            soloed: self.soloed.clone(),
            effects: self.capture_effects(),
            swing: self.capture_swing(),
            pattern_lengths: self.capture_pattern_lengths(),
            bar: self.project.as_ref().and_then(|p| p.bar),
            current_arrangement: self.project.as_ref().and_then(|p| p.current_arrangement),
            arrangements: self.project.as_ref().map(|p| p.arrangements.clone()),
            notes_pattern: self.project.as_ref()
                .and_then(|p| p.current_pattern)
                .unwrap_or(1),
            channels: self.project.as_ref().map(|p| p.channels.clone()),
            all_pattern_notes: self.capture_all_pattern_notes(),
            automations: self.project.as_ref().map(|p| p.automations.clone()),
            patterns: self.project.as_ref().map(|p| p.patterns.clone()),
            current_pattern: self.project.as_ref().and_then(|p| p.current_pattern),
        };
        self.undo_stack.push(current);
        self.restore_snapshot(snap)?;
        Ok(true)
    }

    /// Apply a snapshot: mutate a Project clone with all captured state,
    /// rebuild the Engine ONCE, preserving transport position.
    fn restore_snapshot(&mut self, snap: Snapshot) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut new_project = (*project_arc).clone();
        // Full pattern list restore — authoritative for add/duplicate/
        // delete/rename_pattern undo. Applied BEFORE notes/pattern_lengths
        // so the base is correct; those blocks then write over it (redundant
        // but harmless — they hold the same data).
        if let Some(pats) = &snap.patterns {
            new_project.patterns = pats.clone();
        }
        if let Some(cp) = snap.current_pattern {
            new_project.current_pattern = Some(cp);
        }


        // Prefer the pattern that was current at snapshot time; if it's gone,
        // fall back to the current pattern, then to pattern 0. This keeps undo
        // correct after pattern switches without corrupting pattern 1.
        let target_num = snap.notes_pattern;
        if let Some(bytes) = snap.notes {
            let (_ppq, _plen, notes) = crate::piano::unpack_notes(&bytes)?;
            let idx = new_project.patterns.iter().position(|p| p.number == target_num)
                .or_else(|| {
                    let cur = new_project.current_pattern.unwrap_or(1);
                    new_project.patterns.iter().position(|p| p.number == cur)
                })
                .or(if new_project.patterns.is_empty() { None } else { Some(0) });
            if let Some(i) = idx {
                new_project.patterns[i].notes = notes;
            }
        }

        // Multi-pattern notes restore: overwrites the single-pattern
        // `notes` restore above so reorder_channel's cross-pattern
        // remap undoes correctly. Only populated when len(patterns) > 1.
        if let Some(ref all) = snap.all_pattern_notes {
            for (num, bytes) in all {
                if let Ok((_ppq, _plen, notes)) = crate::piano::unpack_notes(bytes) {
                    if let Some(p) = new_project.patterns.iter_mut().find(|p| p.number == *num) {
                        p.notes = notes;
                    }
                }
            }
        }

        if let Some(bytes) = snap.arrangement {
            let wire = crate::playlist::unpack(&bytes)?;
            crate::playlist::commit(&mut new_project, wire)?;
        }

        new_project.tempo = Some(snap.tempo);
        new_project.swing = snap.swing;
        new_project.bar = snap.bar;
        new_project.current_arrangement = snap.current_arrangement;
        if let Some(arrs) = &snap.arrangements {
            new_project.arrangements = arrs.clone();
        }
        // Channels restored BEFORE the mute/solo loop below, so
        // that loop iterates the correct post-restore channel set.
        if let Some(autos) = &snap.automations {
            new_project.automations = autos.clone();
        }
        if let Some(chs) = &snap.channels {
            new_project.channels = chs.clone();
        }
        for (num, len) in &snap.pattern_lengths {
            if let Some(p) = new_project.patterns.iter_mut().find(|pat| pat.number == *num) {
                p.length = *len;
            }
        }

        let any_solo = !snap.soloed.is_empty();
        for (i, ch) in new_project.channels.iter_mut().enumerate() {
            let idx = i as u16;
            let user_on = !snap.user_muted.contains(&idx);
            let solo_ok = !any_solo || snap.soloed.contains(&idx);
            ch.enabled = user_on && solo_ok;
        }

        self.user_muted = snap.user_muted;
        self.soloed = snap.soloed;

        // Restore effects from snapshot
        self.effects.clear();
        self.bypassed.clear();
        let rate = self.sample_rate;
        for (track, slot, name, state, bypassed) in &snap.effects {
            // Update the model slot
            if let Some(t) = new_project.mixer.get_mut(*track as usize) {
                if let Some(sp) = t.slots.get_mut(*slot as usize) {
                    *sp = Some(os_model::SlotPlugin {
                        id: os_model::Id::NONE,
                        name: name.clone(),
                        state: state.clone(),
                    });
                }
            }
            // Re-instantiate the effect
            if let Some((fx, plugin)) = crate::effects::create_with_plugin(name, rate) {
                crate::effects::restore_state(&plugin, state);
                self.effects.insert((*track, *slot), (fx, plugin));
                if *bypassed {
                    self.bypassed.insert((*track, *slot));
                }
            } else {
                log::warn!("restore: unknown effect '{name}' at t={track} s={slot}");
            }
        }
        // Clear model slots that snapshot didn't have
        for (t, track) in new_project.mixer.iter_mut().enumerate() {
            for (s, slot) in track.slots.iter_mut().enumerate() {
                let key = (t as u16, s as u8);
                if slot.is_some() && !self.effects.contains_key(&key) {
                    *slot = None;
                }
            }
        }

        // Build engine (new_engine re-attaches all non-bypassed effects)
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        self.channel_peaks.clear();
        self.populate_channel_peaks();
        log::info!("undo/redo: restored snapshot");
        Ok(())
    }

    /// Append a new sampler channel with `path` as sample and one note
    /// at `position` (ticks) in pattern #1. Rebuilds the Engine.
    /// Returns the new channel's 0-based index.
    pub fn add_sample_channel(&mut self, path: &str, position: u32) -> Result<u32, String> {
        self.push_snapshot();
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut new_project = (*project_arc).clone();

        let idx = new_project.channels.len() as u16;
        let name = std::path::Path::new(path)
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("Recording")
            .to_string();

        let channel = os_model::Channel {
            id: os_model::Id::NONE,
            index: idx,
            kind: os_model::ChannelKind::Sampler,
            name: Some(name),
            plugin: None,
            plugin_state: None,
            color: None,
            enabled: true,
            volume: 0.78125,
            pan: 0.0,
            pitch_cents: 0,
            mixer_track: idx,
            sample_path: Some(path.to_string()),
            layer_children: vec![],
            envelopes: Default::default(),
            rack: Default::default(),
            misc: Default::default(),
            sound: Default::default(),
        };
        new_project.channels.push(channel);

        let pat_num = new_project.current_pattern.unwrap_or(1);
        let pat_idx = new_project.patterns.iter().position(|p| p.number == pat_num);
        if let Some(i) = pat_idx {
            let note = os_model::Note {
                channel: idx,
                position,
                length: 0,
                ..os_model::Note::step(idx, position)
            };
            new_project.patterns[i].notes.push(note);
            new_project.patterns[i].notes.sort_by_key(|n| n.position);
        }

        let rate = self.sample_rate;
        let mut library = os_engine::SampleLibrary::new(self.factory.clone());
        let mut new_engine = os_engine::Engine::new(&new_project, &mut library, rate, self.mode);
        if let Some(sw) = self.metronome_switch.as_ref() {
            new_engine.set_metronome(sw.clone());
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }

        let missing = library.failures.len();
        for (p, why) in &library.failures {
            log::warn!("missing sample: {p} ({why})");
        }
        log::info!("addSampleChannel idx={} path={} pos={} missing={}",
            idx, path, position, missing);

        self.project = Some(Arc::new(new_project));
        self.populate_channel_peaks();
        Ok(idx as u32)
    }

    /// Number of peak samples stored per channel (for waveform display).
    pub const PEAK_COUNT: usize = 128;

    /// Downsample a decoded `Sample` to `PEAK_COUNT` positive amplitude
    /// buckets (0.0 = silence, 1.0 = full scale). Combines L and R peaks.
    fn downsample_peaks(sample: &os_engine::sample::Sample) -> Vec<f32> {
        let total = sample.left.len().min(sample.right.len());
        let n = Self::PEAK_COUNT;
        if total == 0 { return vec![0.0; n]; }
        let bucket = (total + n - 1) / n;
        let mut peaks = Vec::with_capacity(n);
        for i in 0..n {
            let start = i * bucket;
            let end = (start + bucket).min(total);
            let mut peak = 0.0f32;
            for j in start..end {
                peak = peak.max(sample.left[j].abs()).max(sample.right[j].abs());
            }
            peaks.push(peak.min(1.0));
        }
        peaks
    }

    /// Populate peaks for any channel that has a `sample_path` and isn't
    /// already cached. Silent on decode failures — the UI falls back to a
    /// synthetic shape in that case.
    fn populate_channel_peaks(&mut self) {
        let Some(project) = self.project.as_ref() else { return; };
        for (i, ch) in project.channels.iter().enumerate() {
            let idx = i as u16;
            if self.channel_peaks.contains_key(&idx) { continue; }
            let Some(path) = ch.sample_path.as_deref() else { continue; };
            match os_engine::sample::load(std::path::Path::new(path)) {
                Ok(sample) => {
                    let peaks = Self::downsample_peaks(&sample);
                    self.channel_peaks.insert(idx, peaks);
                }
                Err(e) => {
                    log::warn!("peak decode failed for ch={idx} path={path}: {e}");
                }
            }
        }
    }

    /// Get cached peaks for a channel (empty vec if none).
    pub fn channel_peaks(&self, ch: u16) -> &[f32] {
        self.channel_peaks.get(&ch).map(|v| v.as_slice()).unwrap_or(&[])
    }

    // ─── Transport extras ────────────────────────────────────────
    pub fn set_count_in_bars(&mut self, bars: u32) -> Result<(), String> {
        self.count_in_bars = bars.min(16);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                guard.set_count_in(self.count_in_bars);
            }
        }
        log::info!("count-in = {} bars", self.count_in_bars);
        Ok(())
    }

    pub fn count_in_bars(&self) -> u32 { self.count_in_bars }

    /// Set custom loop range [start_tick, end_tick). Pass start==end or
    /// (0,0) to clear.
    pub fn set_loop_range_ticks(&mut self, start: u64, end: u64) -> Result<(), String> {
        if end <= start {
            return self.clear_loop_range();
        }
        self.loop_range = Some((start, end));
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                guard.set_loop_range(Some((start, end)));
            }
        }
        log::info!("loop range = [{start}, {end}) ticks");
        Ok(())
    }

    pub fn clear_loop_range(&mut self) -> Result<(), String> {
        self.loop_range = None;
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                guard.set_loop_range(None);
            }
        }
        log::info!("loop range cleared");
        Ok(())
    }

    pub fn loop_range(&self) -> Option<(u64, u64)> { self.loop_range }

    pub fn current_pattern_num(&self) -> u16 {
        self.project.as_ref()
            .and_then(|p| p.current_pattern)
            .unwrap_or(1)
    }

    pub fn set_pattern_num(&mut self, num: u16) -> Result<(), String> {
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        if !project_arc.patterns.iter().any(|p| p.number == num) {
            return Err(format!("pattern {num} not found"));
        }
        let mut new_project = (**project_arc).clone();
        new_project.current_pattern = Some(num);

        let in_pattern_mode = matches!(self.mode, PlayMode::Pattern(_));
        if in_pattern_mode {
            self.mode = PlayMode::Pattern(num);
            let mut inst = self.new_engine(&new_project);
            if let Some(arc) = self.engine.as_ref() {
                if let Ok(mut guard) = arc.lock() {
                    inst.continue_from(&mut guard);
                    *guard = inst;
                    self.project = Some(Arc::new(new_project));
                    log::info!("set pattern = {num} (Pattern mode, rebuilt)");
                    return Ok(());
                }
            }
            self.engine = Some(Arc::new(Mutex::new(inst)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("set pattern = {num} (Song mode, no rebuild)");
        Ok(())
    }

    // ─── Pattern management ─────────────────────────────────────
    /// Set the current pattern's length in ticks.
    /// `plen == 0` clears to Auto (`None` — loop over whole bars past last note).
    /// Rebuilds the engine like commit_notes so the change is audible immediately.
    pub fn set_pattern_length(&mut self, plen: u32) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let num = new_project.current_pattern.unwrap_or(1);
        let idx = new_project.patterns.iter().position(|p| p.number == num)
            .ok_or_else(|| format!("pattern {num} not found"))?;
        new_project.patterns[idx].length = if plen == 0 { None } else { Some(plen) };
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("pattern {num} length set to {}", if plen == 0 { "Auto".to_string() } else { plen.to_string() });
        Ok(())
    }

    /// Current project swing, 0..=128 (`0` = no swing).
    pub fn project_swing(&self) -> u8 {
        self.project.as_ref()
            .and_then(|p| p.swing)
            .unwrap_or(0)
            .min(128)
    }

    /// Set the project swing (0..=128). `0` clears to `None` (no swing preset).
    /// Rebuilds the engine like `set_pattern_length` so the change is audible
    /// immediately. Note: `push_snapshot()` does not capture `Project.swing`,
    /// so this change is not undoable yet — same debt as `set_pattern_length`.
    pub fn set_project_swing(&mut self, swing: u8) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        new_project.swing = if swing == 0 { None } else { Some(swing.min(128)) };
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("project swing set to {}", if swing == 0 { "off".to_string() } else { swing.to_string() });
        Ok(())
    }

    /// Current time signature `(numerator, steps_per_beat)`. `None` = 4/4.
    pub fn time_signature(&self) -> Option<(u16, u16)> {
        self.project.as_ref().and_then(|p| p.bar)
    }

    /// Set the project time signature. `num=0` or `steps=0` clears to `None`
    /// (default 4/4). Rebuilds the engine like `set_project_swing`.
    pub fn set_time_signature(&mut self, num: u16, steps: u16) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        new_project.bar = if num == 0 || steps == 0 { None } else { Some((num, steps)) };
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("time signature set to {}/{}", num, if steps > 0 { 16 / steps } else { 4 });
        Ok(())
    }

    /// Current pattern's explicit length in bars: -1 = no project,
    /// 0 = Auto (Pattern.length = None), N > 0 = explicit N bars.
    pub fn pattern_length_bars(&self) -> i32 {
        let Some(project) = self.project.as_ref() else { return -1; };
        let num = project.current_pattern.unwrap_or(1);
        match project.patterns.iter().find(|p| p.number == num) {
            Some(p) => match p.length {
                None => 0,
                Some(t) if t > 0 => {
                    let bar_ticks = (project.ppq as u32) * 4;
                    if bar_ticks == 0 { 0 }
                    else { (t.div_ceil(bar_ticks)).max(1) as i32 }
                }
                _ => 0,
            },
            None => -1,
        }
    }

    /// Set the current pattern's length in bars. 0 = Auto (Pattern.length = None).
    pub fn set_pattern_length_bars(&mut self, bars: u32) -> Result<(), String> {
        let ppq = self.project.as_ref().ok_or("no project loaded")?.ppq as u32;
        let ticks = if bars == 0 { 0 } else { bars * ppq * 4 };
        self.set_pattern_length(ticks)
    }

    /// Rotate step positions of `channel` by `delta`. Positive = right, negative = left.
    /// One snapshot, one rebuild — same pattern as `toggle_step`.
    pub fn rotate_steps(&mut self, channel: u16, delta: i32) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let num = new_project.current_pattern.unwrap_or(1);
        if !crate::rack::rotate_steps(&mut new_project, num, channel, delta) {
            return Err(format!("rotate failed ch={channel} delta={delta}"));
        }
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("rotate steps ch={channel} delta={delta}");
        Ok(())
    }

    /// Replace all step notes of `channel` with the given mask (one byte per step).
    /// Single snapshot + rebuild. Used by fill tools and any batch step write.
    pub fn apply_step_mask(&mut self, channel: u16, mask: &[u8]) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let num = new_project.current_pattern.unwrap_or(1);
        if !crate::rack::apply_step_mask(&mut new_project, num, channel, mask) {
            return Err(format!("applyStepMask failed ch={channel}"));
        }
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
            }
        }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("applyStepMask ch={channel} steps={}", mask.len());
        Ok(())
    }

    pub fn add_pattern(&mut self) -> Result<u16, String> {
        self.push_snapshot();
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut new_project = (*project_arc).clone();
        let new_num = new_project.patterns.iter().map(|p| p.number).max().unwrap_or(0) + 1;
        new_project.patterns.push(os_model::Pattern {
            number: new_num,
            name: Some(format!("Pattern {new_num}")),
            ..os_model::Pattern::default()
        });
        new_project.current_pattern = Some(new_num);

        let mut e = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("add_pattern -> {new_num}");
        Ok(new_num)
    }

    pub fn duplicate_pattern(&mut self, src_num: u16) -> Result<u16, String> {
        self.push_snapshot();
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut new_project = (*project_arc).clone();
        let src_pat = new_project.patterns.iter().find(|p| p.number == src_num)
            .ok_or_else(|| format!("pattern {src_num} not found"))?.clone();
        let new_num = new_project.patterns.iter().map(|p| p.number).max().unwrap_or(0) + 1;
        let src_name = src_pat.name.clone().unwrap_or_else(|| format!("Pattern {src_num}"));
        new_project.patterns.push(os_model::Pattern {
            number: new_num,
            name: Some(format!("{src_name} copy")),
            notes: src_pat.notes.clone(),
            controls: src_pat.controls.clone(),
            ..os_model::Pattern::default()
        });
        new_project.current_pattern = Some(new_num);

        let mut e = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("duplicate_pattern({src_num}) -> {new_num}");
        Ok(new_num)
    }

    pub fn rename_pattern(&mut self, num: u16, name: String) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let pat = new_project.patterns.iter_mut().find(|p| p.number == num)
            .ok_or_else(|| format!("pattern {num} not found"))?;
        pat.name = if name.trim().is_empty() { None } else { Some(name.trim().to_string()) };
        // No rebuild — name isn't read by the audio path.
        self.project = Some(Arc::new(new_project));
        log::info!("rename_pattern({num}) -> {:?}", name);
        Ok(())
    }

    // ─── Channel management ─────────────────────────────────────
    /// Append an empty Sampler channel (built-in tone, no sample).
    /// Routes to master (mixer track 0). Returns the new 0-based index.
    /// Add a new channel intended for playlist audio clips:
    /// `ChannelKind::AudioClip`, sample loaded, no note inserted.
    /// Returns the new channel's index.
    pub fn add_audio_clip_channel(&mut self, path: &str) -> Result<u32, String> {
        self.push_snapshot();
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut new_project = (*project_arc).clone();

        let idx = new_project.channels.len() as u16;
        let name = std::path::Path::new(path)
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("Audio clip")
            .to_string();

        let channel = os_model::Channel {
            id: os_model::Id::NONE,
            index: idx,
            kind: os_model::ChannelKind::AudioClip,
            name: Some(name),
            plugin: None,
            plugin_state: None,
            color: None,
            enabled: true,
            volume: 0.78125,
            pan: 0.0,
            pitch_cents: 0,
            mixer_track: idx,
            sample_path: Some(path.to_string()),
            layer_children: vec![],
            envelopes: Default::default(),
            rack: Default::default(),
            misc: Default::default(),
            sound: Default::default(),
        };
        new_project.channels.push(channel);

        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        self.channel_peaks.clear();
        self.populate_channel_peaks();
        log::info!("add_audio_clip_channel idx={idx} path={path}");
        Ok(idx as u32)
    }

    /// Add an automation channel linked to a single `target` (u32 encoded
    /// per `os_model::target`). Returns the new channel's index.
    ///
    /// Creates one bar (4.0 beats) of a flat line at `value` (0.0..1.0).
    /// Users edit the curve on the playlist; engine plays it via
    /// `Project.automations` (see `os-engine::engine::run_automation`).
    /// Set send level from `track` to `dest`. Adds dest to `routes` if not
    /// present, updates or inserts `(dest, level)` in `send_levels`. Level is
    /// clamped 0..=1. Rebuilds engine.
    pub fn set_send_level(&mut self, track: u16, dest: u16, level: f64) -> Result<(), String> {
        if track == dest { return Err("send to self".into()); }
        self.push_snapshot();
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut new_project = (*project_arc).clone();
        if (track as usize) >= new_project.mixer.len() {
            return Err(format!("track {track} not found"));
        }
        if (dest as usize) >= new_project.mixer.len() {
            return Err(format!("dest {dest} not found"));
        }
        let t = &mut new_project.mixer[track as usize];
        if !t.routes.contains(&dest) { t.routes.push(dest); }
        let lv = level.clamp(0.0, 1.0);
        if let Some(e) = t.send_levels.iter_mut().find(|(d, _)| *d == dest) {
            e.1 = lv;
        } else {
            t.send_levels.push((dest, lv));
        }
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("set_send_level t={track} d={dest} l={lv}");
        Ok(())
    }

    /// Encode all routes of `track` as `(dest u16 LE, level f64 LE)` pairs.
    /// Routes without explicit level use 0.8 (unity). Empty for missing track.
    pub fn get_sends_bytes(&self, track: u16) -> Vec<u8> {
        let Some(p) = self.project.as_ref() else { return Vec::new(); };
        let Some(t) = p.mixer.get(track as usize) else { return Vec::new(); };
        let mut out = Vec::with_capacity(t.routes.len() * 10);
        for dest in &t.routes {
            let lv = t.send_levels.iter()
                .find(|(d, _)| d == dest)
                .map(|&(_, l)| l)
                .unwrap_or(0.8);
            out.extend_from_slice(&dest.to_le_bytes());
            out.extend_from_slice(&lv.to_le_bytes());
        }
        out
    }

    pub fn add_automation_channel(&mut self, target: u32, value: f64) -> Result<u32, String> {
        self.push_snapshot();
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut new_project = (*project_arc).clone();

        let idx = new_project.channels.len() as u16;
        let channel = os_model::Channel {
            id: os_model::Id::NONE,
            index: idx,
            kind: os_model::ChannelKind::Automation,
            name: Some("Automation".into()),
            plugin: None,
            plugin_state: None,
            color: None,
            enabled: true,
            volume: 0.78125,
            pan: 0.0,
            pitch_cents: 0,
            mixer_track: idx,
            sample_path: None,
            layer_children: vec![],
            envelopes: Default::default(),
            rack: Default::default(),
            misc: Default::default(),
            sound: Default::default(),
        };
        new_project.channels.push(channel);

        // os-flp builds the correct event-234 header/tail for us; don't
        // hand-roll them (opaque FL records that vary by version).
        let beats = 4.0;
        let auto = os_flp::project::new_automation(idx, target, value.clamp(0.0, 1.0), beats);
        new_project.automations.push(auto);

        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        self.channel_peaks.clear();
        self.populate_channel_peaks();
        log::info!("add_automation_channel idx={idx} target=0x{target:08X} value={value:.3}");
        Ok(idx as u32)
    }

    /// Automation points for `ch` as flat `[beat0, value0, beat1, value1, ...]`.
    /// Empty if the channel has no automation entry.
    /// `beat` is relative to the clip's window start (from_beat); `value` is 0..1.
    pub fn automation_points(&self, ch: u16) -> Vec<f64> {
        let Some(project) = self.project.as_ref() else { return Vec::new(); };
        let Some(auto) = project.automations.iter().find(|a| a.channel == ch) else {
            return Vec::new();
        };
        let mut out = Vec::with_capacity(auto.points.len() * 2);
        for p in &auto.points {
            out.push(p.beat);
            out.push(p.value);
        }
        out
    }

    /// Replace all points of the automation on `ch` with the flat
    /// `[beat0, value0, beat1, value1, ...]` doubles in `points`.
    /// Sorts by beat, clamps value to 0..1. Empty input is allowed —
    /// engine treats a point-less curve as a no-op.
    pub fn set_automation_points(&mut self, ch: u16, points: &[f64]) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let idx = new_project.automations.iter().position(|a| a.channel == ch)
            .ok_or_else(|| format!("no automation for channel {ch}"))?;
        let mut pts: Vec<os_model::AutoPoint> = Vec::with_capacity(points.len() / 2);
        let mut i = 0;
        while i + 1 < points.len() {
            let beat = points[i].max(0.0);
            let value = points[i + 1].clamp(0.0, 1.0);
            pts.push(os_model::AutoPoint::new(beat, value));
            i += 2;
        }
        pts.sort_by(|a, b| a.beat.partial_cmp(&b.beat).unwrap_or(std::cmp::Ordering::Equal));
        new_project.automations[idx].points = pts;
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("set_automation_points ch={ch} count={}", points.len() / 2);
        Ok(())
    }

    /// Return `[switch, amount, speed, mode]` for automation channel `ch`.
    /// `switch` is 0 (off) or -1 (on); `amount` is 0..=128; `speed` maps to
    /// cycles_per_beat via `ClipLfo::cycles_per_beat`; `mode` is 0 (multiply)
    /// or 1 (add). Returns `[0, 128, 45819, 0]` if no automation exists.
    pub fn get_automation_lfo(&self, ch: u16) -> Vec<i32> {
        let default = vec![0, 128, os_model::clip_lfo::ClipLfo::SPEED_DEFAULT, 0];
        let Some(project) = self.project.as_ref() else { return default; };
        let Some(auto) = project.automations.iter().find(|a| a.channel == ch) else {
            return default;
        };
        let lfo = &auto.lfo;
        vec![lfo.switch, lfo.amount, lfo.speed, i32::from(lfo.mode)]
    }

    /// Set points AND the four LFO fields on automation `ch` in one snapshot
    /// so the whole editor Apply is one undo entry. Preserves tension, skew,
    /// pulse_width, phase, flags, envelope.
    pub fn set_automation_full(
        &mut self,
        ch: u16,
        points: &[f64],
        lfo_switch: i32,
        lfo_amount: i32,
        lfo_speed: i32,
        lfo_mode: i32,
    ) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let idx = new_project.automations.iter().position(|a| a.channel == ch)
            .ok_or_else(|| format!("no automation for channel {ch}"))?;
        // Points
        let mut pts: Vec<os_model::AutoPoint> = Vec::with_capacity(points.len() / 2);
        let mut i = 0;
        while i + 1 < points.len() {
            let beat = points[i].max(0.0);
            let value = points[i + 1].clamp(0.0, 1.0);
            pts.push(os_model::AutoPoint::new(beat, value));
            i += 2;
        }
        pts.sort_by(|a, b| a.beat.partial_cmp(&b.beat).unwrap_or(std::cmp::Ordering::Equal));
        new_project.automations[idx].points = pts;
        // LFO — direct field mutation on the cloned ClipLfo struct
        new_project.automations[idx].lfo.switch = if lfo_switch < 0 { -1 } else { 0 };
        new_project.automations[idx].lfo.amount = lfo_amount.clamp(0, 128);
        new_project.automations[idx].lfo.speed = lfo_speed.clamp(0, 131_071);
        new_project.automations[idx].lfo.mode = if lfo_mode == 1 { 1 } else { 0 };
        let _rate = self.sample_rate;
        let mut new_engine = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                new_engine.continue_from(&mut guard);
                *guard = new_engine;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(new_engine)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("set_automation_full ch={ch} pts={} lfo_sw={} amt={} spd={} mode={}",
            points.len()/2, lfo_switch, lfo_amount, lfo_speed, lfo_mode);
        Ok(())
    }




    pub fn add_sampler_channel(&mut self) -> Result<u32, String> {
        self.push_snapshot();
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut new_project = (*project_arc).clone();
        let idx = new_project.channels.len() as u16;
        let name = format!("Channel {}", idx + 1);

        let channel = os_model::Channel {
            id: os_model::Id::NONE,
            index: idx,
            kind: os_model::ChannelKind::Sampler,
            name: Some(name),
            plugin: None,
            plugin_state: None,
            color: None,
            enabled: true,
            volume: 0.78125,
            pan: 0.0,
            pitch_cents: 0,
            mixer_track: 0,       // master
            sample_path: None,    // built-in tone
            layer_children: vec![],
            envelopes: Default::default(),
            rack: Default::default(),
            misc: Default::default(),
            sound: Default::default(),
        };
        new_project.channels.push(channel);

        let mut e = self.new_engine(&new_project);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(new_project));
        log::info!("add_sampler_channel idx={idx}");
        Ok(idx as u32)
    }

    /// Rename a channel. No engine rebuild — the name isn't in the audio path.
    pub fn rename_channel(&mut self, ch: u16, name: String) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut new_project = (**project_arc).clone();
        let channel = new_project.channels.get_mut(ch as usize)
            .ok_or_else(|| format!("channel {ch} not found"))?;
        channel.name = if name.trim().is_empty() { None }
            else { Some(name.trim().to_string()) };
        self.project = Some(Arc::new(new_project));
        log::info!("rename_channel({ch}) -> {name:?}");
        Ok(())
    }

    // ─── Channel deletion ───────────────────────────────────────
    /// Remove channel `ch` and renumber every reference to channels
    /// above it (notes, channel.index, layer_children, arrangement
    /// clips, mute/solo sets). Undoable — pushes a snapshot first.
    pub fn delete_channel(&mut self, ch: u16) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();

        let mut np = (*project_arc).clone();
        np.channels.remove(ch as usize);

        // Renumber channel.index
        for (i, c) in np.channels.iter_mut().enumerate() {
            c.index = i as u16;
        }

        // Notes: drop those on the deleted channel, shift the rest
        for pat in np.patterns.iter_mut() {
            pat.notes.retain(|n| n.channel != ch);
            for n in pat.notes.iter_mut() {
                if n.channel > ch { n.channel -= 1; }
            }
        }

        // Layer children: remove ref to deleted, shift higher
        for c in np.channels.iter_mut() {
            c.layer_children.retain(|&x| x != ch);
            for x in c.layer_children.iter_mut() {
                if *x > ch { *x -= 1; }
            }
        }

        // Arrangement clips: drop those sourcing the deleted channel,
        // remap higher references
        for arr in np.arrangements.iter_mut() {
            arr.clips.retain(|c| !matches!(c.source, os_model::ClipSource::Channel(n) if n == ch));
            for c in arr.clips.iter_mut() {
                if let os_model::ClipSource::Channel(n) = c.source {
                    if n > ch {
                        c.source = os_model::ClipSource::Channel(n - 1);
                    }
                }
            }
        }

        // Mute/solo sets: drop deleted, shift higher
        self.user_muted.remove(&ch);
        self.soloed.remove(&ch);
        self.user_muted = self.user_muted.iter()
            .map(|&n| if n > ch { n - 1 } else { n }).collect();
        self.soloed = self.soloed.iter()
            .map(|&n| if n > ch { n - 1 } else { n }).collect();

        // Rebuild engine with new project
        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));

        // Peaks cache: keys shifted, so clear and re-populate
        self.channel_peaks.clear();
        self.populate_channel_peaks();

        log::info!("delete_channel({ch}) ok ({} channels remain)",
            self.project.as_ref().map(|p| p.channels.len()).unwrap_or(0));
        Ok(())
    }

    // ─── Channel sample ─────────────────────────────────────────
    pub fn channel_sample(&self, ch: u16) -> Option<String> {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .and_then(|c| c.sample_path.clone())
    }

    /// Set (or clear) a channel's sample path. Rebuilds the engine and
    /// re-decodes peaks for the affected channel only. Undoable.
    pub fn set_channel_sample(&mut self, ch: u16, path: Option<String>) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();

        let mut np = (*project_arc).clone();
        let channel = &mut np.channels[ch as usize];
        channel.sample_path = path.clone();
        // If loading a sample, ensure the channel is a Sampler
        if path.is_some() && channel.kind != os_model::ChannelKind::Sampler {
            channel.kind = os_model::ChannelKind::Sampler;
        }

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));

        // Refresh peaks for this channel
        self.channel_peaks.remove(&ch);
        self.populate_channel_peaks();

        log::info!("set_channel_sample({ch}) -> {:?}", path);
        Ok(())
    }

    // ─── Save ───────────────────────────────────────────────────
    /// Serialize the current project to FLP bytes over `fd`. Reuses the
    /// original file's chunks when available (preserving unmodeled events
    /// such as plugin state blobs, markers, unknown meta), or builds a
    /// fresh FLP via `project::create` when there's no origin. Returns
    /// bytes written.
    pub fn save_project_fd(&mut self, fd: i32) -> Result<u64, String> {
        if fd < 0 {
            return Err(format!("invalid fd {fd}"));
        }
        use std::fs::File;
        use std::io::Write;
        use std::os::unix::io::FromRawFd;

        let project = self.project.as_ref()
            .ok_or("no project loaded")?
            .clone();

        // Ensure minimally viable project for FLP serialization:
        // create() expects at least one mixer track and one arrangement.
        let mut proj = (*project).clone();
        if proj.mixer.is_empty() {
            proj.mixer.push(os_model::MixerTrack::new(0));
        }
        if proj.arrangements.is_empty() {
            proj.arrangements.push(os_flp::project::default_arrangement());
        }

        // Restore original sample paths before save — the .flp must
        // reference the user's originals, not our derived temp files.
        for ch in proj.channels.iter_mut() {
            if let Some(p) = ch.sample_path.as_deref() {
                if let Some(orig) = self.sample_map.get(p) {
                    ch.sample_path = Some(orig.clone());
                }
            }
        }

        // Snapshot effect states into SlotPlugin.state so they round-trip via os-flp
        for (i, m) in proj.mixer.iter_mut().enumerate() {
            let track = i as u16;
            for (s, slot) in m.slots.iter_mut().enumerate() {
                let slot_u8 = s as u8;
                if let Some((_, plugin)) = self.effects.get(&(track, slot_u8)) {
                    if let Some(bytes) = crate::effects::snapshot_state(plugin) {
                        if let Some(sp) = slot {
                            sp.state = bytes;
                        }
                    }
                }
            }
        }

        let from_original = self.original_bytes.is_some();
        let mut flp = if let Some(bytes) = self.original_bytes.as_ref() {
            os_flp::FlpFile::parse(bytes)
                .map_err(|e| format!("re-parse: {e:?}"))?
        } else {
            os_flp::project::create(&proj)
                .map_err(|e| format!("create FLP: {e:?}"))?
        };

        // Diagnostic — log what won't be written
        let lost = os_flp::project::unsaved_parts(&proj, &flp);
        if !lost.is_empty() {
            log::warn!("save: {} parts will not be written ({:?})", lost.len(), lost);
        }

        os_flp::project::apply(&proj, &mut flp)
            .map_err(|e| format!("apply: {e:?}"))?;

        let bytes = flp.to_bytes()
            .map_err(|e| format!("serialize: {e:?}"))?;
        let size = bytes.len() as u64;

        // Write to fd — ManuallyDrop prevents closing caller's fd
        let owned = std::mem::ManuallyDrop::new(unsafe { File::from_raw_fd(fd) });
        let dup = owned.try_clone().map_err(|e| format!("dup fd: {e}"))?;
        let mut out = std::io::BufWriter::new(dup);
        out.write_all(&bytes).map_err(|e| format!("write: {e}"))?;
        out.flush().map_err(|e| format!("flush: {e}"))?;

        log::info!("save_project: {} bytes (from_original={})", size, from_original);
        Ok(size)
    }

    // ─── Channel reorder / duplicate ────────────────────────────
    /// Move channel from index `from` to index `to`. Remaps every
    /// reference (notes, layers, clips, mute/solo). Undoable.
    pub fn reorder_channel(&mut self, from: u16, to: u16) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let n = project_arc.channels.len() as u16;
        if from >= n || to >= n {
            return Err(format!("reorder range: from={from} to={to} n={n}"));
        }
        if from == to {
            return Ok(());
        }
        self.push_snapshot();

        let mut np = (*project_arc).clone();

        // Build old-index → new-index mapping
        let map: Vec<u16> = (0..n)
            .map(|i| {
                if i == from { to }
                else if from < to && i > from && i <= to { i - 1 }
                else if from > to && i >= to && i < from { i + 1 }
                else { i }
            })
            .collect();

        // Move channel in vec
        let ch = np.channels.remove(from as usize);
        np.channels.insert(to as usize, ch);

        // Rewrite channel.index
        for (i, c) in np.channels.iter_mut().enumerate() {
            c.index = i as u16;
        }
        // Remap notes
        for pat in np.patterns.iter_mut() {
            for note in pat.notes.iter_mut() {
                let old = note.channel as usize;
                if old < map.len() {
                    note.channel = map[old];
                }
            }
        }
        // Remap layer_children
        for c in np.channels.iter_mut() {
            for child in c.layer_children.iter_mut() {
                let old = *child as usize;
                if old < map.len() {
                    *child = map[old];
                }
            }
        }
        // Remap arrangement clips
        for arr in np.arrangements.iter_mut() {
            for clip in arr.clips.iter_mut() {
                if let os_model::ClipSource::Channel(n2) = clip.source {
                    let old = n2 as usize;
                    if old < map.len() {
                        clip.source = os_model::ClipSource::Channel(map[old]);
                    }
                }
            }
        }
        // Remap automation channel refs (Project.automations[].channel)
        for automation in np.automations.iter_mut() {
            let old = automation.channel as usize;
            if old < map.len() {
                automation.channel = map[old];
            }
        }

        // Remap mute/solo sets
        self.user_muted = self.user_muted.iter()
            .filter_map(|&m| map.get(m as usize).copied()).collect();
        self.soloed = self.soloed.iter()
            .filter_map(|&m| map.get(m as usize).copied()).collect();

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        self.channel_peaks.clear();
        self.populate_channel_peaks();
        log::info!("reorder_channel({from} -> {to})");
        Ok(())
    }

    /// Insert a copy of channel `idx` at position `idx+1`. Notes are
    /// NOT copied — the duplicate starts empty, ready for new content.
    /// Returns the new channel's index. Undoable.
    pub fn duplicate_channel(&mut self, idx: u16) -> Result<u16, String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if idx as usize >= project_arc.channels.len() {
            return Err(format!("channel {idx} not found"));
        }
        self.push_snapshot();

        let mut np = (*project_arc).clone();
        let mut copy = np.channels[idx as usize].clone();
        let base_name = copy.name.clone().unwrap_or_else(|| format!("Channel {}", idx + 1));
        copy.name = Some(format!("{base_name} copy"));
        let new_idx = idx + 1;
        np.channels.insert(new_idx as usize, copy);

        // Renumber channel.index
        for (i, c) in np.channels.iter_mut().enumerate() {
            c.index = i as u16;
        }
        // Shift references above idx up by 1
        for pat in np.patterns.iter_mut() {
            for note in pat.notes.iter_mut() {
                if note.channel > idx { note.channel += 1; }
            }
        }
        for c in np.channels.iter_mut() {
            for child in c.layer_children.iter_mut() {
                if *child > idx { *child += 1; }
            }
        }
        for arr in np.arrangements.iter_mut() {
            for clip in arr.clips.iter_mut() {
                if let os_model::ClipSource::Channel(n) = clip.source {
                    if n > idx {
                        clip.source = os_model::ClipSource::Channel(n + 1);
                    }
                }
            }
        }
        self.user_muted = self.user_muted.iter().map(|&m| if m > idx { m + 1 } else { m }).collect();
        self.soloed = self.soloed.iter().map(|&m| if m > idx { m + 1 } else { m }).collect();

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        self.channel_peaks.clear();
        self.populate_channel_peaks();
        log::info!("duplicate_channel({idx}) -> {new_idx}");
        Ok(new_idx)
    }

    pub fn set_builtin_instrument(&mut self, ch: u16, name: &str) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        let inst = crate::instruments::create(name, self.sample_rate)
            .ok_or_else(|| format!("unknown instrument: {name}"))?;
        self.push_snapshot();

        let mut np = (*project_arc).clone();
        let c = &mut np.channels[ch as usize];
        c.kind = os_model::ChannelKind::Plugin;
        c.plugin = Some(name.to_string());

        let mut e = self.new_engine(&np);
        e.set_instrument(ch as usize, inst);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_builtin_instrument({ch}, {name})");
        Ok(())
    }

    pub fn channel_instrument(&self, ch: u16) -> Option<String> {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .filter(|c| matches!(c.kind, os_model::ChannelKind::Plugin))
            .and_then(|c| c.plugin.clone())
    }

    pub fn instrument_catalog() -> String {
        crate::instruments::CATALOG.iter()
            .map(|(k, v)| format!("{}\t{}", k, v))
            .collect::<Vec<_>>().join("\n")
    }

    // ─── Mixer effects ──────────────────────────────────────────
    pub fn mixer_add_effect(&mut self, track: u16, slot: u8, name: &str) -> Result<(), String> {
        let (inst, _plugin) = crate::effects::create_with_plugin(name, self.sample_rate)
            .ok_or_else(|| format!("unknown effect: {name}"))?;
        self.push_snapshot();
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut np = (*project_arc).clone();
        let t = np.mixer.get_mut(track as usize)
            .ok_or_else(|| format!("track {track} not found"))?;
        if slot as usize >= t.slots.len() {
            return Err(format!("slot {slot} out of range (max {})", t.slots.len()));
        }
        t.slots[slot as usize] = Some(os_model::SlotPlugin {
            id: os_model::Id::NONE,
            name: name.to_string(),
            state: Vec::new(),
        });
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                guard.set_effect(track as usize, slot as usize, inst);
            }
        }
        self.project = Some(Arc::new(np));
        log::info!("mixer_add_effect(track={track}, slot={slot}, {name})");
        Ok(())
    }

    pub fn mixer_remove_effect(&mut self, track: u16, slot: u8) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut np = (**project_arc).clone();
        let t = np.mixer.get_mut(track as usize)
            .ok_or_else(|| format!("track {track} not found"))?;
        if slot as usize >= t.slots.len() {
            return Err(format!("slot {slot} out of range"));
        }
        t.slots[slot as usize] = None;
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                guard.set_effect(
                    track as usize,
                    slot as usize,
                    Arc::new(Mutex::new(crate::effects::NoOpEffect)),
                );
            }
        }
        self.project = Some(Arc::new(np));
        log::info!("mixer_remove_effect(track={track}, slot={slot})");
        Ok(())
    }

    pub fn mixer_slot_effect(&self, track: u16, slot: u8) -> String {
        self.project.as_ref()
            .and_then(|p| p.mixer.get(track as usize))
            .and_then(|t| t.slots.get(slot as usize))
            .and_then(|s| s.as_ref())
            .map(|s| s.name.clone())
            .unwrap_or_default()
    }

    pub fn effect_catalog() -> String {
        crate::effects::CATALOG.iter()
            .map(|(k, v)| format!("{}\t{}", k, v))
            .collect::<Vec<_>>().join("\n")
    }

    // ─── Channel color ──────────────────────────────────────────
    pub fn set_channel_color(&mut self, ch: u16, r: u8, g: u8, b: u8) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut np = (**project_arc).clone();
        let c = np.channels.get_mut(ch as usize)
            .ok_or_else(|| format!("channel {ch} not found"))?;
        c.color = Some(os_model::Rgb::new(r, g, b));
        self.project = Some(Arc::new(np));
        log::info!("set_channel_color({ch}, {r},{g},{b})");
        Ok(())
    }

    pub fn clear_channel_color(&mut self, ch: u16) -> Result<(), String> {
        self.push_snapshot();
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut np = (**project_arc).clone();
        let c = np.channels.get_mut(ch as usize)
            .ok_or_else(|| format!("channel {ch} not found"))?;
        c.color = None;
        self.project = Some(Arc::new(np));
        Ok(())
    }

    pub fn channel_color(&self, ch: u16) -> Option<(u8, u8, u8)> {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .and_then(|c| c.color)
            .map(|rgb| (rgb.r, rgb.g, rgb.b))
    }

    // ─── Effect bypass ──────────────────────────────────────────
    pub fn mixer_bypass_effect(&mut self, track: u16, slot: u8, on: bool) -> Result<(), String> {
        let key = (track, slot);
        if !self.effects.contains_key(&key) {
            return Err(format!("no effect on track={track}, slot={slot}"));
        }
        self.push_snapshot();
        if on { self.bypassed.insert(key); } else { self.bypassed.remove(&key); }
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                if on {
                    guard.set_effect(track as usize, slot as usize,
                        Arc::new(Mutex::new(crate::effects::NoOpEffect)));
                } else if let Some((fx, _)) = self.effects.get(&key) {
                    guard.set_effect(track as usize, slot as usize, fx.clone());
                }
            }
        }
        log::info!("mixer_bypass_effect({track},{slot},{on})");
        Ok(())
    }

    pub fn mixer_is_effect_bypassed(&self, track: u16, slot: u8) -> bool {
        self.bypassed.contains(&(track, slot))
    }

    // ─── Effect parameters ──────────────────────────────────────
    pub fn effect_param_count(&self, track: u16, slot: u8) -> u32 {
        let Some((_, plugin)) = self.effects.get(&(track, slot)) else { return 0 };
        plugin.lock().map(|p| p.param_count() as u32).unwrap_or(0)
    }

    /// Format: id\tname\tunit\tgroup\tmin\tmax\tdefault\tsteps
    pub fn effect_param_meta(&self, track: u16, slot: u8, index: u32) -> String {
        let Some((_, plugin)) = self.effects.get(&(track, slot)) else { return String::new() };
        let Ok(guard) = plugin.lock() else { return String::new() };
        let Some(info) = guard.param_info(index as usize) else { return String::new() };
        format!("{}\t{}\t{}\t{}\t{}\t{}\t{}\t{}",
            info.id, info.name, info.unit, info.group,
            info.min, info.max, info.default, info.steps)
    }

    pub fn effect_param_value(&self, track: u16, slot: u8, id: u32) -> f64 {
        let Some((_, plugin)) = self.effects.get(&(track, slot)) else { return 0.0 };
        plugin.lock().ok().and_then(|p| p.param_value(id)).unwrap_or(0.0)
    }

    pub fn effect_param_text(&self, track: u16, slot: u8, id: u32, value: f64) -> String {
        let Some((_, plugin)) = self.effects.get(&(track, slot)) else { return String::new() };
        plugin.lock().ok().and_then(|p| p.param_text(id, value)).unwrap_or_default()
    }

    pub fn effect_set_param(&mut self, track: u16, slot: u8, id: u32, value: f64) -> Result<(), String> {
        let Some((_, plugin)) = self.effects.get(&(track, slot)) else {
            return Err("no effect on this slot".into());
        };
        let mut guard = plugin.lock().map_err(|_| "lock failed")?;
        if !guard.set_param(id, value) {
            return Err("set_param rejected".into());
        }
        Ok(())
    }

    // ─── Sample start offset ────────────────────────────────────
    pub fn set_channel_start_offset(&mut self, ch: u16, offset: f64) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err("channel not found".into());
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        np.channels[ch as usize].sound.start_offset_bits = offset.clamp(0.0, 0.999).to_bits();
        self.apply_fx_to_paths(&mut np);
        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_start_offset({ch}, {offset:.3})");
        Ok(())
    }

    pub fn channel_start_offset(&self, ch: u16) -> f64 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.start_offset())
            .unwrap_or(0.0)
    }

    // ─── Effect reorder (swap slots) ────────────────────────────
    pub fn mixer_swap_effects(&mut self, track: u16, a: u8, b: u8) -> Result<(), String> {
        if a == b { return Ok(()); }
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        let mut np = (*project_arc).clone();
        let t = np.mixer.get_mut(track as usize)
            .ok_or_else(|| format!("track {track} not found"))?;
        if a as usize >= t.slots.len() || b as usize >= t.slots.len() {
            return Err("slot out of range".into());
        }
        self.push_snapshot();
        t.slots.swap(a as usize, b as usize);

        let ka = (track, a); let kb = (track, b);
        let va = self.effects.remove(&ka);
        let vb = self.effects.remove(&kb);
        if let Some(v) = vb { self.effects.insert(ka, v); }
        if let Some(v) = va { self.effects.insert(kb, v); }

        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                match self.effects.get(&(track, a)) {
                    Some((fx, _)) => guard.set_effect(track as usize, a as usize, fx.clone()),
                    None => guard.set_effect(track as usize, a as usize,
                        Arc::new(Mutex::new(crate::effects::NoOpEffect))),
                }
                match self.effects.get(&(track, b)) {
                    Some((fx, _)) => guard.set_effect(track as usize, b as usize, fx.clone()),
                    None => guard.set_effect(track as usize, b as usize,
                        Arc::new(Mutex::new(crate::effects::NoOpEffect))),
                }
            }
        }
        self.project = Some(Arc::new(np));
        log::info!("mixer_swap_effects(t={track}, {a}<->{b})");
        Ok(())
    }

    pub fn take_master_peak(&self) -> f32 {
        let bits = self.master_peak.swap(0, Ordering::Relaxed);
        f32::from_bits(bits).clamp(0.0, 1.0)
    }

    pub fn clear_master_peak(&self) {
        self.master_peak.store(0, Ordering::Relaxed);
    }

    pub fn render_stem_fd(
        &self,
        track: u16,
        fd: i32,
        song_mode: bool,
        rate: u32,
    ) -> Result<u64, String> {
        if track == 0 {
            return Err("master (track 0) cannot be a stem".into());
        }
        let project = self.project.as_ref().ok_or("no project loaded")?.clone();
        if track as usize >= project.mixer.len() {
            return Err(format!("track {track} not found"));
        }
        let mut stem = (*project).clone();
        for (i, t) in stem.mixer.iter_mut().enumerate() {
            if i == 0 || i == track as usize { continue; }
            t.volume = Some(0.0);
        }
        let stats = crate::export::render_wav_to_fd(&stem, fd, song_mode, rate)?;
        log::info!(
            "stem render OK: track={track} {} frames ({:.2}s)",
            stats.frames, stats.seconds
        );
        Ok(stats.frames)
    }

    // ─── Sample editor: pitch (live) ────────────────────────────
    pub fn channel_pitch_cents(&self, ch: u16) -> i32 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.pitch_cents)
            .unwrap_or(0)
    }

    /// Change pitch (cents). Live — no engine rebuild.
    pub fn set_channel_pitch(&mut self, ch: u16, cents: i32) -> Result<(), String> {
        let clamped = cents.clamp(-4800, 4800);
        // Live edit — update engine channels in place
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                guard.apply_edit(os_engine::LiveEdit::ChannelPitch(ch as usize, clamped));
            }
        }
        // Also update model so rebuilds and saves preserve the value
        let project_arc = self.project.as_mut().ok_or("no project loaded")?;
        let mut np = (**project_arc).clone();
        if let Some(c) = np.channels.get_mut(ch as usize) {
            c.pitch_cents = clamped;
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_pitch({ch}, {clamped})");
        Ok(())
    }

    // ─── Sample editor: loop points (rebuild) ───────────────────
    pub fn channel_loop_start(&self, ch: u16) -> i64 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .and_then(|c| c.sound.loop_points)
            .map(|[a, _]| a).unwrap_or(-1)
    }

    pub fn channel_loop_end(&self, ch: u16) -> i64 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .and_then(|c| c.sound.loop_points)
            .map(|[_, b]| b).unwrap_or(-1)
    }

    /// Set loop points (frame indices). Also enables USE_LOOP flag.
    pub fn set_channel_loop_points(&mut self, ch: u16, start: i64, end: i64) -> Result<(), String> {
        if start < 0 || end <= start {
            return Err(format!("invalid loop [{start}, {end}]"));
        }
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        let c = &mut np.channels[ch as usize];
        c.sound.loop_points = Some([start, end]);
        // Ensure USE_LOOP is on
        let flags = c.sound.sampler_flags
            .unwrap_or(os_model::ChannelSound::DEFAULT_FLAGS);
        c.sound.sampler_flags = Some(flags | os_model::ChannelSound::USE_LOOP);

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_loop_points({ch}, {start}..{end})");
        Ok(())
    }

    pub fn clear_channel_loop_points(&mut self, ch: u16) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        np.channels[ch as usize].sound.loop_points = None;

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("clear_channel_loop_points({ch})");
        Ok(())
    }

    pub fn channel_use_loop(&self, ch: u16) -> bool {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.sampler_flags.map_or(
                c.kind == os_model::ChannelKind::Sampler,
                |f| f & os_model::ChannelSound::USE_LOOP != 0,
            ))
            .unwrap_or(false)
    }

    pub fn set_channel_use_loop(&mut self, ch: u16, on: bool) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        let c = &mut np.channels[ch as usize];
        let flags = c.sound.sampler_flags
            .unwrap_or(os_model::ChannelSound::DEFAULT_FLAGS);
        c.sound.sampler_flags = Some(if on {
            flags | os_model::ChannelSound::USE_LOOP
        } else {
            flags & !os_model::ChannelSound::USE_LOOP
        });

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_use_loop({ch}, {on})");
        Ok(())
    }

    /// Total frames in this channel's sample file (0 if no sample).
    pub fn channel_sample_frames(&self, ch: u16) -> u64 {
        let Some(path) = self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .and_then(|c| c.sample_path.as_deref())
        else { return 0; };
        os_engine::sample::length(std::path::Path::new(path))
            .map(|(frames, _rate)| frames)
            .unwrap_or(0)
    }

    // ─── Stretch (mode / multiplier / pitch shift) ──────────────
    pub fn channel_stretch_mode(&self, ch: u16) -> i32 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.stretch_mode())
            .unwrap_or(0)
    }

    pub fn set_channel_stretch_mode(&mut self, ch: u16, mode: i32) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        np.channels[ch as usize].sound.stretch[3] = mode as u32;

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_stretch_mode({ch}, {mode})");
        Ok(())
    }

    pub fn channel_stretch_multiplier(&self, ch: u16) -> f64 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.stretch_multiplier())
            .unwrap_or(1.0)
    }

    pub fn set_channel_stretch_multiplier(&mut self, ch: u16, multiplier: f64) -> Result<(), String> {
        if !(0.25..=4.0).contains(&multiplier) {
            return Err(format!("multiplier {multiplier:.3} out of range 0.25..=4.0"));
        }
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        // Encoding: 2^(stretch[2] as i32 / 10000) — stored as signed i32
        let encoded = (multiplier.log2() * 10000.0).round() as i32;
        np.channels[ch as usize].sound.stretch[2] = encoded as u32;

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_stretch_multiplier({ch}, {multiplier:.3})");
        Ok(())
    }

    pub fn channel_stretch_pitch(&self, ch: u16) -> i32 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.stretch_cents() as i32)
            .unwrap_or(0)
    }

    pub fn set_channel_stretch_pitch(&mut self, ch: u16, cents: i32) -> Result<(), String> {
        let clamped = cents.clamp(-1200, 1200);
        let project_arc = self.project.as_ref().ok_or("no project loaded")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        np.channels[ch as usize].sound.stretch[1] = clamped as u32;

        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_stretch_pitch({ch}, {clamped})");
        Ok(())
    }

    // ─── Sample FX: derived-sample pipeline ─────────────────────
    /// Rewrite every channel's sample_path to point at a derived
    /// (transformed) WAV when the sound has any fx enabled. Original
    /// paths are preserved in `sample_map` and restored at save time.
    fn apply_fx_to_paths(&mut self, project: &mut os_model::Project) {
        for ch in project.channels.iter_mut() {
            let Some(orig) = ch.sample_path.clone() else { continue; };
            // Restore original if never derived
            let base = self.sample_map.get(&orig).cloned().unwrap_or(orig.clone());
            if !crate::sample_fx::needs_transform(&ch.sound) {
                ch.sample_path = Some(base);
                continue;
            }
            match crate::sample_fx::derive(&base, &ch.sound) {
                Ok(Some(derived)) => {
                    self.sample_map.insert(derived.clone(), base);
                    ch.sample_path = Some(derived);
                }
                Ok(None) => { ch.sample_path = Some(base); }
                Err(e) => log::warn!("sample_fx derive failed: {e}"),
            }
        }
    }

    /// Enable or disable one `SampleFx` flag (REVERSE, SWAP, CLIP).
    pub fn set_channel_fx_flag(&mut self, ch: u16, flag: u16, on: bool) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        let s = &mut np.channels[ch as usize].sound;
        if on { s.fx.flags |= flag; } else { s.fx.flags &= !flag; }
        self.apply_fx_to_paths(&mut np);
        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_fx_flag({ch}, {flag:#x}, {on})");
        Ok(())
    }

    pub fn channel_fx_flag(&self, ch: u16, flag: u16) -> bool {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.fx.flags & flag != 0)
            .unwrap_or(false)
    }

    /// Set trim start (0..1) and length (0..1 of remainder).
    pub fn set_channel_fx_trim(&mut self, ch: u16, start: f64, length: f64) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        let s = &mut np.channels[ch as usize].sound;
        s.fx.start_bits = start.clamp(0.0, 0.99).to_bits();
        s.fx.length_bits = length.clamp(0.01, 1.0).to_bits();
        self.apply_fx_to_paths(&mut np);
        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        log::info!("set_channel_fx_trim({ch}, {start:.3}, {length:.3})");
        Ok(())
    }

    pub fn channel_fx_trim_start(&self, ch: u16) -> f64 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.fx.start())
            .unwrap_or(0.0)
    }

    pub fn channel_fx_trim_length(&self, ch: u16) -> f64 {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.fx.length())
            .unwrap_or(1.0)
    }

    pub fn set_channel_fx_normalize(&mut self, ch: u16, on: bool) -> Result<(), String> {
        let project_arc = self.project.as_ref().ok_or("no project")?.clone();
        if ch as usize >= project_arc.channels.len() {
            return Err(format!("channel {ch} not found"));
        }
        self.push_snapshot();
        let mut np = (*project_arc).clone();
        np.channels[ch as usize].sound.fx.normalize = on;
        self.apply_fx_to_paths(&mut np);
        let mut e = self.new_engine(&np);
        if let Some(arc) = self.engine.as_ref() {
            if let Ok(mut guard) = arc.lock() {
                e.continue_from(&mut guard);
                *guard = e;
            }
        } else {
            self.engine = Some(Arc::new(Mutex::new(e)));
        }
        self.project = Some(Arc::new(np));
        Ok(())
    }

    pub fn channel_fx_normalize(&self, ch: u16) -> bool {
        self.project.as_ref()
            .and_then(|p| p.channels.get(ch as usize))
            .map(|c| c.sound.fx.normalize)
            .unwrap_or(false)
    }

    pub fn set_device(&mut self, device: AudioDevice) {
        self.device = Some(device);
    }
}

fn default_sample_rate() -> u32 {
    use cpal::traits::{DeviceTrait, HostTrait};
    cpal::default_host()
        .default_output_device()
        .and_then(|d| d.default_output_config().ok())
        .map(|c| c.sample_rate())
        .unwrap_or(48_000)
}

fn idx_count(p: Option<&Arc<Project>>) -> usize {
    p.and_then(|x| x.patterns.first())
     .map(|p| p.notes.len())
     .unwrap_or(0)
}
