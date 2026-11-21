//! JNI bridge for the extension API.
//!
//! Two entry points:
//! - `nativeExtList()` → JSON array of all registered extensions
//! - `nativeExtRun(id, kind, json)` → runs one extension, returns JSON
//!
//! All data crosses as JSON strings. No complex JNI types.
//! Errors never cross JNI — every failure is logged and marshalled into JSON.

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::EnvUnowned;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use lemon_ext_api::{
    all_note_editors, all_note_generators, all_song_generators,
    ExtError, InputNeeds, Note, NoteFlags,
    ParamCurve, ParamSpec, ParamValue, ParamValues,
    UiSpec, WidgetKind, PreviewKind,
};

// ── Force-link extension crates so inventory::submit! fires at build time ──
// Adding a new extension: add one `use` line here + one line in Cargo.toml.
use ext_example_quantize as _;
use ext_song_gen_lofi as _;

// ═══════════════════════════════════════════════════════════════
// Wire types — exchange format for JNI. Snake_case in Rust, camelCase in JSON.
// ═══════════════════════════════════════════════════════════════

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WireNote {
    pub start_tick: u32,
    pub length_ticks: u32,
    pub pitch: u8,
    pub velocity: u8,
    #[serde(default)]
    pub channel: u8,
    #[serde(default)]
    pub flags: u8,
}

impl From<&WireNote> for Note {
    fn from(w: &WireNote) -> Self {
        Note {
            start_tick: w.start_tick,
            length_ticks: w.length_ticks,
            pitch: w.pitch.min(127),
            velocity: w.velocity.min(127),
            channel: w.channel,
            flags: NoteFlags::from_bits_truncate(w.flags),
        }
    }
}

impl From<Note> for WireNote {
    fn from(n: Note) -> Self {
        Self {
            start_tick: n.start_tick,
            length_ticks: n.length_ticks,
            pitch: n.pitch,
            velocity: n.velocity,
            channel: n.channel,
            flags: n.flags.bits(),
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RunInput {
    #[serde(default)]
    notes: Vec<WireNote>,
    #[serde(default)]
    params: serde_json::Map<String, Value>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct RunOutput {
    ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    notes: Option<Vec<WireNote>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<String>,
}

// ═══════════════════════════════════════════════════════════════
// JSON encoders for the registry list
// ═══════════════════════════════════════════════════════════════

fn param_spec_json(p: &ParamSpec) -> Value {
    json!({
        "id": p.id,
        "name": p.name,
        "min": p.min,
        "max": p.max,
        "default": p.default,
        "unit": p.unit,
        "curve": match p.curve {
            ParamCurve::Linear => "linear",
            ParamCurve::Logarithmic => "log",
            ParamCurve::Discrete => "discrete",
            _ => "linear",
        },
    })
}

fn ui_spec_json(u: &UiSpec) -> Value {
    let sections: Vec<Value> = u.sections.iter().map(|s| {
        let fields: Vec<Value> = s.fields.iter().map(|f| {
            let widget = match &f.widget {
                WidgetKind::Slider { min, max, step, default, unit } => json!({
                    "kind": "slider", "min": min, "max": max,
                    "step": step, "default": default, "unit": unit,
                }),
                WidgetKind::Spinner { min, max, default } => json!({
                    "kind": "spinner", "min": min, "max": max, "default": default,
                }),
                WidgetKind::Checkbox { default } => json!({
                    "kind": "checkbox", "default": default,
                }),
                _ => json!({ "kind": "unsupported" }),
            };
            json!({
                "id": f.id,
                "label": f.label,
                "tooltip": f.tooltip,
                "widget": widget,
            })
        }).collect();
        json!({
            "title": s.title,
            "fields": fields,
            "collapsedByDefault": s.collapsed_by_default,
        })
    }).collect();

    let preview = match &u.preview {
        PreviewKind::None => json!({"kind": "none"}),
        PreviewKind::Notes => json!({"kind": "notes"}),
        PreviewKind::Timeline => json!({"kind": "timeline"}),
        PreviewKind::Custom(id) => json!({"kind": "custom", "id": id.0}),
        _ => json!({"kind": "unknown"}),
    };

    json!({
        "sections": sections,
        "preview": preview,
        "primaryAction": u.primary_action,
        "estimatedMs": u.estimated_ms,
    })
}

fn song_sketch_json(s: &lemon_ext_api::SongSketch) -> serde_json::Value {
    let tracks: Vec<serde_json::Value> = s.tracks.iter().map(|t| {
        let patterns: Vec<serde_json::Value> = t.patterns.iter().map(|p| {
            let notes: Vec<serde_json::Value> = p.notes.iter().map(|n| json!({
                "startTick": n.start_tick,
                "lengthTicks": n.length_ticks,
                "pitch": n.pitch,
                "velocity": n.velocity,
                "channel": n.channel,
                "flags": n.flags.bits(),
            })).collect();
            json!({
                "name": p.name,
                "lengthTicks": p.length_ticks,
                "notes": notes,
            })
        }).collect();
        json!({
            "name": t.name,
            "instrument": {
                "category": format!("{:?}", t.instrument.category),
                "preferred": t.instrument.preferred,
            },
            "colorHint": t.color_hint,
            "volume": t.volume,
            "pan": t.pan,
            "muted": t.muted,
            "patterns": patterns,
        })
    }).collect();

    let arrangement = s.arrangement.as_ref().map(|a| {
        let clips: Vec<serde_json::Value> = a.clips.iter().map(|c| json!({
            "trackIdx": c.track_idx,
            "patternIdx": c.pattern_idx,
            "startBar": c.start_bar,
            "lengthBars": c.length_bars,
        })).collect();
        let sections: Vec<serde_json::Value> = a.sections.iter().map(|sec| json!({
            "name": sec.name,
            "startBar": sec.start_bar,
            "lengthBars": sec.length_bars,
            "colorHint": sec.color_hint,
        })).collect();
        json!({ "clips": clips, "sections": sections })
    });

    json!({
        "tempo": s.tempo,
        "timeSig": { "numerator": s.time_sig.numerator, "denominator": s.time_sig.denominator },
        "ppq": s.ppq,
        "tracks": tracks,
        "arrangement": arrangement,
        "summary": s.summary,
    })
}

fn ext_list_json() -> String {
    let mut entries = Vec::new();

    for ed in all_note_editors() {
        entries.push(json!({
            "id": ed.id(),
            "name": ed.name(),
            "description": ed.description(),
            "version": ed.version(),
            "kind": "editor",
            "params": ed.params().iter().map(param_spec_json).collect::<Vec<_>>(),
            "ui": ui_spec_json(&ed.ui()),
            "needs": ed.needs().bits(),
            "capabilities": 0u8,
        }));
    }

    for g in all_note_generators() {
        entries.push(json!({
            "id": g.id(),
            "name": g.name(),
            "description": g.description(),
            "version": g.version(),
            "kind": "generator",
            "params": g.params().iter().map(param_spec_json).collect::<Vec<_>>(),
            "ui": ui_spec_json(&g.ui()),
            "needs": g.needs().bits(),
            "capabilities": 0u8,
        }));
    }

    for s in all_song_generators() {
        entries.push(json!({
            "id": s.id(),
            "name": s.name(),
            "description": s.description(),
            "version": s.version(),
            "kind": "song-generator",
            "params": s.params().iter().map(param_spec_json).collect::<Vec<_>>(),
            "ui": ui_spec_json(&s.ui()),
            "needs": 0u8,
            "capabilities": s.capabilities().bits(),
        }));
    }

    serde_json::to_string(&entries).unwrap_or_else(|_| "[]".into())
}

// ═══════════════════════════════════════════════════════════════
// Run one extension
// ═══════════════════════════════════════════════════════════════

fn build_param_values(map: &serde_json::Map<String, Value>) -> ParamValues {
    let mut pv = ParamValues::new();
    for (k, v) in map {
        // Leak the key as &'static str — bounded by extension lifetime, harmless.
        let key: &'static str = Box::leak(k.clone().into_boxed_str());
        let val = match v {
            Value::Number(n) => {
                if let Some(f) = n.as_f64() { ParamValue::Float(f as f32) }
                else if let Some(i) = n.as_i64() { ParamValue::Int(i as i32) }
                else { continue; }
            }
            Value::Bool(b) => ParamValue::Bool(*b),
            Value::String(s) => ParamValue::Text(s.clone()),
            _ => continue,
        };
        pv.set(key, val);
    }
    pv
}

fn run_note_editor(id: &str, input: &RunInput) -> Result<Vec<WireNote>, ExtError> {
    let ed = all_note_editors()
        .find(|e| e.id() == id)
        .ok_or_else(|| ExtError::InvalidInput { reason: format!("no editor with id `{id}`") })?;
    let notes: Vec<Note> = input.notes.iter().map(Note::from).collect();
    let params = build_param_values(&input.params);
    let out = ed.edit(&notes, &params)?;
    Ok(out.into_iter().map(WireNote::from).collect())
}

fn run_song_generator(id: &str, input: &RunInput) -> Result<lemon_ext_api::SongSketch, ExtError> {
    let sg = all_song_generators()
        .find(|s| s.id() == id)
        .ok_or_else(|| ExtError::InvalidInput { reason: format!("no song generator with id `{id}`") })?;

    let ctx = lemon_ext_api::SongContext::new(96, 42)
        .with_length_bars(16)
        .with_max_tracks(16);
    let params = build_param_values(&input.params);
    sg.generate(&ctx, &params)
}

fn run_ext(id: &str, kind: &str, raw: &str) -> String {
    let input: RunInput = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(e) => return serde_json::to_string(&RunOutput {
            ok: false, notes: None,
            error: Some(format!("bad input json: {e}")),
        }).unwrap_or_else(|_| r#"{"ok":false,"error":"json encode failed"}"#.into()),
    };

    let result: Result<Vec<WireNote>, ExtError> = match kind {
        "editor" => run_note_editor(id, &input),
        "generator" => Err(ExtError::Unsupported { feature: "note-generator (Milestone 0.6)" }),
        "song-generator" => Err(ExtError::Unsupported { feature: "song-generator (Milestone 0.6)" }),
        other => Err(ExtError::Unsupported { feature: Box::leak(format!("kind={other}").into_boxed_str()) }),
    };

    let out = match result {
        Ok(notes) => RunOutput { ok: true, notes: Some(notes), error: None },
        Err(e) => RunOutput { ok: false, notes: None, error: Some(e.to_string()) },
    };
    serde_json::to_string(&out).unwrap_or_else(|_| r#"{"ok":false,"error":"json encode failed"}"#.into())
}

// ═══════════════════════════════════════════════════════════════
// JNI entry points
// ═══════════════════════════════════════════════════════════════
//
// jni 0.22 API: `EnvUnowned::with_env(|env| -> jni::errors::Result<T>)`
// captures the closure's return into an `EnvOutcome`. We use the
// accumulator pattern (mutable local + `let _ = ...` on the outcome)
// rather than unwrapping `EnvOutcome` so we don't depend on its exact
// method surface. Closure errors are not silently swallowed — jni
// handles exception propagation from the EnvUnowned boundary.

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_ExtHost_nativeExtList<'caller>(
    mut unowned: EnvUnowned<'caller>, _c: JClass<'caller>,
) -> jstring {
    let mut result: jstring = std::ptr::null_mut();
    let _ = unowned.with_env(|env| -> jni::errors::Result<()> {
        log::info!("nativeExtList() called");
        let json = std::panic::catch_unwind(|| ext_list_json())
            .unwrap_or_else(|_| { log::error!("ext_list_json panicked"); "[]".to_string() });
        let js = env.new_string(&json)?;
        result = js.into_raw();
        Ok(())
    });
    result
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_ExtHost_nativeExtRun<'caller>(
    mut unowned: EnvUnowned<'caller>, _c: JClass<'caller>,
    ext_id: JString<'caller>, kind: JString<'caller>, input: JString<'caller>,
) -> jstring {
    let mut result: jstring = std::ptr::null_mut();
    let _ = unowned.with_env(|env| -> jni::errors::Result<()> {
        let id: String = env.get_string(&ext_id).map(|s| s.into()).unwrap_or_default();
        let k: String = env.get_string(&kind).map(|s| s.into()).unwrap_or_default();
        let inp: String = env.get_string(&input).map(|s| s.into()).unwrap_or_default();
        log::info!("nativeExtRun(id={id}, kind={k}, bytes={})", inp.len());
        let json = std::panic::catch_unwind(|| run_ext(&id, &k, &inp))
            .unwrap_or_else(|_| {
                log::error!("run_ext panicked for id={id}");
                r#"{"ok":false,"error":"extension panicked"}"#.to_string()
            });
        let js = env.new_string(&json)?;
        result = js.into_raw();
        Ok(())
    });
    result
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_ExtHost_nativeExtRunSong<'caller>(
    mut unowned: EnvUnowned<'caller>, _c: JClass<'caller>,
    ext_id: JString<'caller>, input: JString<'caller>,
) -> jstring {
    let mut result: jstring = std::ptr::null_mut();
    let _ = unowned.with_env(|env| -> jni::errors::Result<()> {
        let id: String = env.get_string(&ext_id).map(|s| s.into()).unwrap_or_default();
        let inp: String = env.get_string(&input).map(|s| s.into()).unwrap_or_default();
        log::info!("nativeExtRunSong(id={id}, bytes={})", inp.len());

        let out_json = std::panic::catch_unwind(|| -> String {
            let input: RunInput = match serde_json::from_str(&inp) {
                Ok(v) => v,
                Err(e) => return format!(r#"{{"ok":false,"error":"bad input: {e}"}}"#),
            };
            match run_song_generator(&id, &input) {
                Ok(sketch) => {
                    let v = serde_json::json!({
                        "ok": true,
                        "sketch": song_sketch_json(&sketch),
                    });
                    serde_json::to_string(&v).unwrap_or_else(|_| r#"{"ok":false}"#.into())
                }
                Err(e) => format!(r#"{{"ok":false,"error":"{}"}}"#, e.to_string().replace('"', "\\\"")),
            }
        }).unwrap_or_else(|_| {
            log::error!("run_song_generator panicked for id={id}");
            r#"{"ok":false,"error":"extension panicked"}"#.to_string()
        });

        let js = env.new_string(&out_json)?;
        result = js.into_raw();
        Ok(())
    });
    result
}
