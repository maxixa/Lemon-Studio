// SPDX-License-Identifier: GPL-3.0-or-later
//! Lemon Studio engine — JNI bridge (jni 0.22 API).

use std::os::raw::c_void;
use std::sync::OnceLock;

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::{jboolean, jdouble, jfloat, jint, jlong, jstring, JNI_VERSION_1_6};
use jni::EnvUnowned;

mod audio;
mod engine;
mod demo_project;
mod piano;
mod rack;
mod playlist;
mod mixer;
mod export;
mod midi;
mod instruments;
mod effects;
mod sample_fx;

use engine::EngineHandle;

static LOG_INIT: OnceLock<()> = OnceLock::new();

fn ensure_logger() {
    LOG_INIT.get_or_init(|| {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("lemon-engine"),
        );
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn JNI_OnLoad(_vm: *mut jni::sys::JavaVM, _: *mut c_void) -> jint {
    ensure_logger();
    log::info!("liblemon_engine_ffi loaded (JNI_OnLoad)");
    JNI_VERSION_1_6
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_initContext<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    context: JObject<'caller>,
) {
    unowned_env
        .with_env(|env| -> jni::errors::Result<()> {
            ensure_logger();
            let vm = env.get_java_vm()?;
            let ctx = env.new_global_ref(context)?;
            let raw_vm = vm.get_raw() as *mut c_void;
            let raw_ctx = ctx.as_obj().as_raw() as *mut c_void;
            unsafe {
                ndk_context::initialize_android_context(raw_vm, raw_ctx);
            }
            std::mem::forget(ctx);
            log::info!("Android context initialized");
            Ok(())
        })
        .resolve::<ThrowRuntimeExAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineNew<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
) -> jlong {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<jlong> {
            ensure_logger();
            let handle = Box::into_raw(Box::new(EngineHandle::new()));
            log::info!("engineNew -> {:p}", handle);
            Ok(handle as jlong)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineLoadProject<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    path: JString<'caller>,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }
            let path_str: String = path.to_string();
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            match engine.load_project(&path_str, None) {
                Ok(()) => {
                    log::info!("loadProject OK: {path_str}");
                    Ok(true)
                }
                Err(e) => {
                    log::error!("loadProject failed: {e}");
                    Ok(false)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineLoadDemo<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            match engine.load_demo() {
                Ok(()) => {
                    log::info!("loadDemo OK");
                    Ok(true)
                }
                Err(e) => {
                    log::error!("loadDemo failed: {e}");
                    Ok(false)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_enginePosition<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jlong {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<jlong> {
            if handle == 0 { return Ok(0); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            let frames = match engine.engine.as_ref().and_then(|arc| arc.try_lock().ok()) {
                Some(guard) => guard.position() as jlong,
                None => 0,
            };
            Ok(frames)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineTrackCount<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<jint> {
            if handle == 0 { return Ok(0); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            let n = engine.project.as_ref().map(|p| p.channels.len()).unwrap_or(0) as jint;
            Ok(n)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineTrackName<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    index: jint,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jstring> {
            if handle == 0 { return env.new_string("").map(|s| s.into_raw()); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            let name = engine
                .project
                .as_ref()
                .and_then(|p| p.channels.get(index as usize))
                .map(|c| {
                    c.name
                        .clone()
                        .unwrap_or_else(|| format!("Track {}", index + 1))
                })
                .unwrap_or_default();
            env.new_string(&name).map(|s| s.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSampleRate<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<jint> {
            if handle == 0 { return Ok(48_000); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            Ok(engine.sample_rate as jint)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineLoadProjectFd<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    fd: jint,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 || fd < 0 {
                return Ok(false);
            }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            match engine.load_project_fd(fd, None) {
                Ok(()) => {
                    log::info!("loadProjectFd OK (fd={fd})");
                    Ok(true)
                }
                Err(e) => {
                    log::error!("loadProjectFd failed (fd={fd}): {e}");
                    Ok(false)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetLooping<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    on: jboolean,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            match engine.set_looping(on) {
                Ok(()) => {
                    log::info!("setLooping({on}) OK");
                    Ok(true)
                }
                Err(e) => {
                    log::error!("setLooping failed: {e}");
                    Ok(false)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSeek<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    frames: jlong,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 || frames < 0 {
                return Ok(false);
            }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            match engine.seek(frames as u64) {
                Ok(()) => Ok(true),
                Err(e) => {
                    log::error!("seek({frames}) failed: {e}");
                    Ok(false)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineNotesBytes<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> JByteArray<'caller> {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JByteArray> {
            let empty = env.byte_array_from_slice(&[])?;
            if handle == 0 { return Ok(empty); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            match engine.notes_bytes() {
                Some(bytes) => env.byte_array_from_slice(&bytes),
                None => Ok(empty),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineAllPatternNotes<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> JByteArray<'caller> {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JByteArray> {
            let empty = env.byte_array_from_slice(&[])?;
            if handle == 0 { return Ok(empty); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            match engine.all_pattern_notes_bytes() {
                Some(bytes) => env.byte_array_from_slice(&bytes),
                None => Ok(empty),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineCommitNotes<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    bytes: JByteArray<'caller>,
) -> jboolean {
    unowned_env
        .with_env(|env| -> jni::errors::Result<bool> {
            if handle == 0 { return Ok(false); }
            let data = env.convert_byte_array(&bytes)?;
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            match engine.commit_notes(&data) {
                Ok(()) => { log::info!("commitNotes OK ({} bytes)", data.len()); Ok(true) }
                Err(e) => { log::error!("commitNotes failed: {e}"); Ok(false) }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineNotesDebug<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jstring> {
            if handle == 0 { return env.new_string("").map(|s| s.into_raw()); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            let s = engine.notes_bytes()
                .map(|b| format!("{} bytes", b.len()))
                .unwrap_or_else(|| "no project".into());
            env.new_string(&s).map(|s| s.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineStepMaskBytes<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
) -> JByteArray<'caller> {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JByteArray> {
            let empty = env.byte_array_from_slice(&[])?;
            if handle == 0 || channel < 0 { return Ok(empty); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            env.byte_array_from_slice(&engine.step_mask_bytes(channel as u16))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineToggleStep<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
    step: jint,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 || channel < 0 || step < 0 { return Ok(false); }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            let steps = engine.step_count_for_current_pattern() as jint;
            if step >= steps { return Ok(false); }
            match engine.toggle_step(channel as u16, step as u32) {
                Ok(on) => { log::info!("toggleStep ch={channel} step={step} -> {on}"); Ok(on) }
                Err(e) => { log::error!("toggleStep failed: {e}"); Ok(false) }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineArrangementBytes<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> JByteArray<'caller> {
    unowned_env.with_env(|env| -> jni::errors::Result<JByteArray> {
        let empty = env.byte_array_from_slice(&[])?;
        if handle == 0 { return Ok(empty); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        match engine.arrangement_bytes() {
            Some(b) => env.byte_array_from_slice(&b),
            None => Ok(empty),
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineCommitArrangementSilent<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    bytes: JByteArray<'caller>,
) -> jboolean {
    unowned_env.with_env(|env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let data = env.convert_byte_array(&bytes)?;
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.commit_arrangement_silent(&data) {
            Ok(()) => { log::info!("commitArrangementSilent OK ({} bytes)", data.len()); Ok(true) }
            Err(e) => { log::error!("commitArrangementSilent failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineCommitArrangement<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, bytes: JByteArray<'caller>,
) -> jboolean {
    unowned_env.with_env(|env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let data = env.convert_byte_array(&bytes)?;
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.commit_arrangement(&data) {
            Ok(()) => { log::info!("commitArrangement OK ({} bytes)", data.len()); Ok(true) }
            Err(e) => { log::error!("commitArrangement failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetArrangement<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    num: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || num < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_arrangement(num as u16) {
            Ok(()) => { log::info!("setArrangement({num}) OK"); Ok(true) }
            Err(e) => { log::error!("setArrangement failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineArrangementCurrent<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(-1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.project.as_ref()
            .and_then(|p| p.current_arrangement)
            .map(|n| n as jint)
            .unwrap_or(-1))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineArrangementCount<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.project.as_ref().map(|p| p.arrangements.len() as jint).unwrap_or(0))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineArrangementNumber<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    index: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || index < 0 { return Ok(-1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.project.as_ref()
            .and_then(|p| p.arrangements.get(index as usize))
            .map(|a| a.number as jint)
            .unwrap_or(-1))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineArrangementName<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    index: jint,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        if handle == 0 || index < 0 {
            return Ok(env.new_string("")?.into_raw());
        }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let name = engine.project.as_ref()
            .and_then(|p| p.arrangements.get(index as usize))
            .map(|a| a.name.clone().unwrap_or_else(|| format!("Arrangement {}", a.number)))
            .unwrap_or_default();
        Ok(env.new_string(name)?.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineAddArrangement<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(-1); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.add_arrangement() {
            Ok(n) => { log::info!("addArrangement -> {n}"); Ok(n as jint) }
            Err(e) => { log::error!("addArrangement failed: {e}"); Ok(-1) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineRenameArrangement<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    num: jint,
    name_bytes: JByteArray<'caller>,
) -> jboolean {
    unowned_env.with_env(|env| -> jni::errors::Result<bool> {
        if handle == 0 || num < 0 { return Ok(false); }
        let raw = env.convert_byte_array(&name_bytes)?;
        let name = String::from_utf8(raw).unwrap_or_default();
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.rename_arrangement(num as u16, name) {
            Ok(()) => { log::info!("renameArrangement({num}) OK"); Ok(true) }
            Err(e) => { log::error!("renameArrangement failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineDeleteArrangement<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    num: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || num < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.delete_arrangement(num as u16) {
            Ok(()) => { log::info!("deleteArrangement({num}) OK"); Ok(true) }
            Err(e) => { log::error!("deleteArrangement failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetPlayMode<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, song: jboolean,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_play_mode(song) {
            Ok(()) => { log::info!("setPlayMode(song={song})"); Ok(true) }
            Err(e) => { log::error!("setPlayMode failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineIsSongMode<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.is_song_mode())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_enginePatternCount<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.pattern_count() as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_enginePatternName<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, index: jint,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        if handle == 0 { return env.new_string("").map(|s| s.into_raw()); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let name = engine.pattern_name(index as u32);
        env.new_string(&name).map(|s| s.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerBytes<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> JByteArray<'caller> {
    unowned_env.with_env(|env| -> jni::errors::Result<JByteArray> {
        let empty = env.byte_array_from_slice(&[])?;
        if handle == 0 { return Ok(empty); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        match engine.mixer_bytes() {
            Some(b) => env.byte_array_from_slice(&b),
            None => Ok(empty),
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerTrackCount<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.mixer_track_count() as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerSetVolume<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, track: jint, v: jdouble,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.mixer_set_volume(track as u32, v) {
            Ok(()) => { log::info!("mixerVol track={track} v={v}"); Ok(true) }
            Err(e) => { log::error!("mixerVol failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerSetPan<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, track: jint, v: jdouble,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.mixer_set_pan(track as u32, v) {
            Ok(()) => { log::info!("mixerPan track={track} v={v}"); Ok(true) }
            Err(e) => { log::error!("mixerPan failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineExportWav<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    fd: jint,
    song_mode: jboolean,
    rate: jint,
    bits: jint,
) -> jlong {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<jlong> {
            if handle == 0 || fd < 0 || rate <= 0 {
                return Ok(0);
            }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            let project_arc = match engine.project.as_ref() {
                Some(p) => p.clone(),
                None => {
                    log::error!("export: no project loaded");
                    return Ok(0);
                }
            };
            match crate::export::render_wav_to_fd(&project_arc, fd, song_mode, rate as u32, bits as u32) {
                Ok(stats) => {
                    log::info!("exportWav OK: {} frames, {:.2}s", stats.frames, stats.seconds);
                    Ok(stats.frames as jlong)
                }
                Err(e) => {
                    log::error!("exportWav failed: {e}");
                    Ok(0)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineTempo<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jdouble {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<jdouble> {
            if handle == 0 { return Ok(140.0); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            Ok(engine.tempo())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetTempo<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    bpm: jdouble,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 { return Ok(false); }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            match engine.set_tempo(bpm) {
                Ok(()) => { log::info!("setTempo({bpm:.2}) OK"); Ok(true) }
                Err(e) => { log::error!("setTempo failed: {e}"); Ok(false) }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelEnabled<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(true); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_enabled(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelMuted<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_muted(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelSoloed<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_soloed(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelMuted<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, channel: jint, on: jboolean,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_muted(channel as u16, on) {
            Ok(()) => { log::info!("setChannelMuted({channel}, {on})"); Ok(true) }
            Err(e) => { log::error!("setChannelMuted failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelSoloed<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, channel: jint, on: jboolean,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_soloed(channel as u16, on) {
            Ok(()) => { log::info!("setChannelSoloed({channel}, {on})"); Ok(true) }
            Err(e) => { log::error!("setChannelSoloed failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineFactory<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jstring> {
            if handle == 0 { return env.new_string("").map(|s| s.into_raw()); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            let s = engine.factory()
                .map(|p| p.to_string_lossy().into_owned())
                .unwrap_or_default();
            env.new_string(&s).map(|x| x.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetFactory<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    path: JString<'caller>,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 { return Ok(false); }
            let path_str = path.to_string();
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            let factory = if path_str.is_empty() { None }
                else { Some(std::path::PathBuf::from(&path_str)) };
            match engine.set_factory(factory) {
                Ok(()) => { log::info!("setFactory({path_str}) OK"); Ok(true) }
                Err(e) => { log::error!("setFactory failed: {e}"); Ok(false) }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineExportFlac<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    fd: jint,
    song_mode: jboolean,
    rate: jint,
) -> jlong {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<jlong> {
            if handle == 0 || fd < 0 || rate <= 0 {
                return Ok(0);
            }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            let project_arc = match engine.project.as_ref() {
                Some(p) => p.clone(),
                None => {
                    log::error!("exportFlac: no project loaded");
                    return Ok(0);
                }
            };
            match crate::export::render_flac_to_fd(&project_arc, fd, song_mode, rate as u32) {
                Ok(stats) => {
                    log::info!("exportFlac OK: {} frames, {:.2}s", stats.frames, stats.seconds);
                    Ok(stats.frames as jlong)
                }
                Err(e) => {
                    log::error!("exportFlac failed: {e}");
                    Ok(0)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineExportOgg<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    fd: jint,
    song_mode: jboolean,
    rate: jint,
) -> jlong {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<jlong> {
            if handle == 0 || fd < 0 || rate <= 0 { return Ok(0); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            let project_arc = match engine.project.as_ref() {
                Some(p) => p.clone(),
                None => return Ok(0),
            };
            match crate::export::render_ogg_to_fd(&project_arc, fd, song_mode, rate as u32) {
                Ok(stats) => {
                    log::info!("exportOgg -> {} frames", stats.frames);
                    Ok(stats.frames as jlong)
                }
                Err(e) => { log::error!("exportOgg failed: {e}"); Ok(0) }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}


#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetMetronome<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    on: jboolean,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 { return Ok(false); }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            match engine.set_metronome_enabled(on) {
                Ok(state) => Ok(state),
                Err(e) => { log::error!("setMetronome failed: {e}"); Ok(false) }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMetronomeEnabled<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 { return Ok(false); }
            let engine = unsafe { &*(handle as *const EngineHandle) };
            Ok(engine.metronome_enabled())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMetronomeSound<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.metronome_sound_index() as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMetronomeVolume<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.metronome_volume_db() as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetMetronomeSound<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, index: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || index < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_metronome_sound(index as u32) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setMetronomeSound failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetMetronomeVolume<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, db: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_metronome_volume(db as i8) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setMetronomeVolume failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineExportMp3<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    fd: jint,
    song_mode: jboolean,
    rate: jint,
    kbps: jint,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 || fd < 0 || rate <= 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let project = match engine.project.as_ref() {
            Some(p) => p.clone(),
            None => { log::error!("exportMp3: no project"); return Ok(0); }
        };
        match crate::export::render_mp3_to_fd(&project, fd, song_mode, rate as u32, kbps as u32) {
            Ok(s) => { log::info!("exportMp3 OK: {} frames", s.frames); Ok(s.frames as jlong) }
            Err(e) => { log::error!("exportMp3 failed: {e}"); Ok(0) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineUndo<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.undo() {
            Ok(v) => Ok(v),
            Err(e) => { log::warn!("undo: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineRedo<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.redo() {
            Ok(v) => Ok(v),
            Err(e) => { log::warn!("redo: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineCanUndo<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.can_undo())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineCanRedo<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.can_redo())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineAddSampleChannel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    path: JString<'caller>,
    position_ticks: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(-1); }
        let path_str = path.to_string();
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.add_sample_channel(&path_str, position_ticks.max(0) as u32) {
            Ok(idx) => Ok(idx as jint),
            Err(e) => { log::error!("addSampleChannel failed: {e}"); Ok(-1) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelPeaks<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
) -> JByteArray<'caller> {
    unowned_env.with_env(|env| -> jni::errors::Result<JByteArray> {
        if handle == 0 || channel < 0 {
            return env.byte_array_from_slice(&[]);
        }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let peaks = engine.channel_peaks(channel as u16);
        if peaks.is_empty() {
            return env.byte_array_from_slice(&[]);
        }
        let mut bytes = Vec::with_capacity(peaks.len() * 4);
        for p in peaks {
            bytes.extend_from_slice(&p.to_le_bytes());
        }
        env.byte_array_from_slice(&bytes)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetCountIn<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, bars: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_count_in_bars(bars.max(0) as u32) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setCountIn failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineCountIn<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.count_in_bars() as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetLoopRange<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>,
    handle: jlong, start: jlong, end: jlong,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        if start < 0 || end < 0 || end <= start {
            return match engine.clear_loop_range() {
                Ok(()) => Ok(true),
                Err(e) => { log::error!("clearLoopRange: {e}"); Ok(false) }
            };
        }
        match engine.set_loop_range_ticks(start as u64, end as u64) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setLoopRange failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineLoopRangeStart<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 { return Ok(-1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.loop_range().map(|(a, _)| a as jlong).unwrap_or(-1))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineLoopRangeEnd<'caller>(
    mut unowned_env: EnvUnowned<'caller>, _class: JClass<'caller>, handle: jlong,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 { return Ok(-1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.loop_range().map(|(_, b)| b as jlong).unwrap_or(-1))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineExportMidi<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    fd: jint,
    song_mode: jboolean,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 || fd < 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let project = match engine.project.as_ref() {
            Some(p) => p.clone(),
            None => { log::error!("exportMidi: no project"); return Ok(0); }
        };
        match crate::export::render_midi_to_fd(&project, fd, song_mode) {
            Ok(s) => { log::info!("exportMidi OK: {} bytes", s.frames); Ok(s.frames as jlong) }
            Err(e) => { log::error!("exportMidi failed: {e}"); Ok(0) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_enginePatternCurrent<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.current_pattern_num() as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetPattern<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    num: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || num <= 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_pattern_num(num as u16) {
            Ok(()) => { log::info!("setPattern({num}) OK"); Ok(true) }
            Err(e) => { log::error!("setPattern failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetPatternLength<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    length: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || length < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_pattern_length(length as u32) {
            Ok(()) => { log::info!("setPatternLength({length}) OK"); Ok(true) }
            Err(e) => { log::error!("setPatternLength failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineProjectSwing<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.project_swing() as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetProjectSwing<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    swing: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || !(0..=128).contains(&swing) { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_project_swing(swing as u8) {
            Ok(()) => { log::info!("setProjectSwing({swing}) OK"); Ok(true) }
            Err(e) => { log::error!("setProjectSwing failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineGetTimeSignature<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        match engine.time_signature() {
            Some((num, steps)) if num > 0 && steps > 0 && steps <= 16 => {
                let den = 16 / steps;
                Ok(((num as jint) << 16) | (den as jint))
            }
            _ => Ok(0),
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetTimeSignature<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    num: jint,
    den: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || num < 0 || den < 0 { return Ok(false); }
        let steps: u16 = if den <= 0 { 4 } else { (16 / den as u16).max(1) };
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_time_signature(num as u16, steps) {
            Ok(()) => { log::info!("setTimeSignature({num}/{den}) OK"); Ok(true) }
            Err(e) => { log::error!("setTimeSignature failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineRotateSteps<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
    delta: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.rotate_steps(channel as u16, delta) {
            Ok(()) => { log::info!("rotateSteps ch={channel} delta={delta} OK"); Ok(true) }
            Err(e) => { log::error!("rotateSteps failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineApplyStepMask<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
    mask: JByteArray<'caller>,
) -> jboolean {
    unowned_env.with_env(|env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let raw = env.convert_byte_array(&mask)?;
        if raw.is_empty() { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.apply_step_mask(channel as u16, &raw) {
            Ok(()) => { log::info!("applyStepMask ch={channel} steps={} OK", raw.len()); Ok(true) }
            Err(e) => { log::error!("applyStepMask failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_enginePatternLengthBars<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(-1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.pattern_length_bars())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetPatternLengthBars<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    bars: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || bars < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_pattern_length_bars(bars as u32) {
            Ok(()) => { log::info!("setPatternLengthBars({bars}) OK"); Ok(true) }
            Err(e) => { log::error!("setPatternLengthBars failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}


#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineAddPattern<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(-1); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.add_pattern() {
            Ok(n) => Ok(n as jint),
            Err(e) => { log::error!("addPattern failed: {e}"); Ok(-1) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineDuplicatePattern<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    src_num: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || src_num <= 0 { return Ok(-1); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.duplicate_pattern(src_num as u16) {
            Ok(n) => Ok(n as jint),
            Err(e) => { log::error!("duplicatePattern failed: {e}"); Ok(-1) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineRenamePattern<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    num: jint,
    name: JString<'caller>,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || num <= 0 { return Ok(false); }
        let name_str = name.to_string();
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.rename_pattern(num as u16, name_str) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("renamePattern failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineGetAutomationLfo<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
) -> JByteArray<'caller> {
    unowned_env.with_env(|env| -> jni::errors::Result<JByteArray> {
        let empty = env.byte_array_from_slice(&[])?;
        if handle == 0 || channel < 0 { return Ok(empty); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let vals = engine.get_automation_lfo(channel as u16);
        let mut bytes = Vec::with_capacity(vals.len() * 4);
        for v in vals { bytes.extend_from_slice(&v.to_le_bytes()); }
        env.byte_array_from_slice(&bytes)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetAutomationFull<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
    points: JByteArray<'caller>,
    lfo_switch: jint,
    lfo_amount: jint,
    lfo_speed: jint,
    lfo_mode: jint,
) -> jboolean {
    unowned_env.with_env(|env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let raw = env.convert_byte_array(&points)?;
        if raw.len() % 16 != 0 { return Ok(false); }
        let mut floats: Vec<f64> = Vec::with_capacity(raw.len() / 8);
        let mut i = 0;
        while i + 8 <= raw.len() {
            let mut b = [0u8; 8];
            b.copy_from_slice(&raw[i..i + 8]);
            floats.push(f64::from_le_bytes(b));
            i += 8;
        }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_automation_full(
            channel as u16, &floats,
            lfo_switch, lfo_amount, lfo_speed, lfo_mode,
        ) {
            Ok(()) => { log::info!("setAutomationFull ch={channel} OK"); Ok(true) }
            Err(e) => { log::error!("setAutomationFull failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetAutomationPoints<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
    points: JByteArray<'caller>,
) -> jboolean {
    unowned_env.with_env(|env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let raw = env.convert_byte_array(&points)?;
        if raw.len() % 16 != 0 { return Ok(false); }
        let mut floats: Vec<f64> = Vec::with_capacity(raw.len() / 8);
        let mut i = 0;
        while i + 8 <= raw.len() {
            let mut b = [0u8; 8];
            b.copy_from_slice(&raw[i..i + 8]);
            floats.push(f64::from_le_bytes(b));
            i += 8;
        }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_automation_points(channel as u16, &floats) {
            Ok(()) => { log::info!("setAutomationPoints ch={channel} pts={}", floats.len() / 2); Ok(true) }
            Err(e) => { log::error!("setAutomationPoints failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineAutomationPoints<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
) -> JByteArray<'caller> {
    unowned_env.with_env(|env| -> jni::errors::Result<JByteArray> {
        let empty = env.byte_array_from_slice(&[])?;
        if handle == 0 || channel < 0 { return Ok(empty); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let floats = engine.automation_points(channel as u16);
        if floats.is_empty() { return Ok(empty); }
        let mut bytes = Vec::with_capacity(floats.len() * 8);
        for f in floats {
            bytes.extend_from_slice(&f.to_le_bytes());
        }
        env.byte_array_from_slice(&bytes)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerGetSendsBytes<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    track: jint,
) -> JByteArray<'caller> {
    unowned_env.with_env(|env| -> jni::errors::Result<JByteArray> {
        let empty = env.byte_array_from_slice(&[])?;
        if handle == 0 || track < 0 { return Ok(empty); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let bytes = engine.get_sends_bytes(track as u16);
        if bytes.is_empty() { return Ok(empty); }
        env.byte_array_from_slice(&bytes)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerSetSendLevel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    track: jint,
    dest: jint,
    level: jdouble,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || track < 0 || dest < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_send_level(track as u16, dest as u16, level) {
            Ok(()) => { log::info!("setMixerSendLevel t={track} d={dest} l={level}"); Ok(true) }
            Err(e) => { log::error!("setMixerSendLevel failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerIsBus<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    track: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<jboolean> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.is_bus(track as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerSetBus<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    track: jint,
    is_bus: jboolean,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<jboolean> {
        if handle == 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        engine.set_bus(track as u16, is_bus);
        Ok(true)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerBusList<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> JByteArray<'caller> {
    unowned_env.with_env(|env| -> jni::errors::Result<JByteArray> {
        let empty = env.byte_array_from_slice(&[])?;
        if handle == 0 { return Ok(empty); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let buses = engine.bus_list();
        let mut bytes: Vec<u8> = Vec::with_capacity(4 + buses.len() * 2);
        bytes.extend_from_slice(&(buses.len() as u32).to_le_bytes());
        for b in buses { bytes.extend_from_slice(&b.to_le_bytes()); }
        env.byte_array_from_slice(&bytes)
    }).resolve::<ThrowRuntimeExAndDefault>()
}


#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineAddAutomationChannel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    target: jint,
    value: jdouble,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || target < 0 { return Ok(-1); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.add_automation_channel(target as u32, value) {
            Ok(n) => { log::info!("addAutomationChannel -> {n}"); Ok(n as jint) }
            Err(e) => { log::error!("addAutomationChannel failed: {e}"); Ok(-1) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineAddAudioClipChannel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    path: JString<'caller>,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(-1); }
        let path_str = path.to_string();
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.add_audio_clip_channel(&path_str) {
            Ok(n) => { log::info!("addAudioClipChannel -> {n}"); Ok(n as jint) }
            Err(e) => { log::error!("addAudioClipChannel failed: {e}"); Ok(-1) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineAddSamplerChannel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 { return Ok(-1); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.add_sampler_channel() {
            Ok(n) => Ok(n as jint),
            Err(e) => { log::error!("addSamplerChannel failed: {e}"); Ok(-1) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineRenameChannel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
    name: JString<'caller>,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let name_str = name.to_string();
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.rename_channel(channel as u16, name_str) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("renameChannel failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineDeleteChannel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.delete_channel(channel as u16) {
            Ok(()) => { log::info!("deleteChannel({channel}) OK"); Ok(true) }
            Err(e) => { log::error!("deleteChannel failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelSample<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        if handle == 0 || channel < 0 {
            return env.new_string("").map(|s| s.into_raw());
        }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let s = engine.channel_sample(channel as u16).unwrap_or_default();
        env.new_string(&s).map(|x| x.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelSample<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
    path: JString<'caller>,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let path_str = path.to_string();
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        let sample_path = if path_str.is_empty() { None } else { Some(path_str) };
        match engine.set_channel_sample(channel as u16, sample_path) {
            Ok(()) => { log::info!("setChannelSample({channel}) OK"); Ok(true) }
            Err(e) => { log::error!("setChannelSample failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSaveProject<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    fd: jint,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 || fd < 0 { return Ok(0); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.save_project_fd(fd) {
            Ok(n) => Ok(n as jlong),
            Err(e) => {
                log::error!("saveProject failed: {e}");
                Ok(0)
            }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineReorderChannel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    from: jint,
    to: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || from < 0 || to < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.reorder_channel(from as u16, to as u16) {
            Ok(()) => { log::info!("reorderChannel({from}->{to}) OK"); Ok(true) }
            Err(e) => { log::error!("reorderChannel failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineDuplicateChannel<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    channel: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || channel < 0 { return Ok(-1); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.duplicate_channel(channel as u16) {
            Ok(n) => Ok(n as jint),
            Err(e) => { log::error!("duplicateChannel failed: {e}"); Ok(-1) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetBuiltinInstrument<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, name: JString<'caller>,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let name_str = name.to_string();
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_builtin_instrument(channel as u16, &name_str) {
            Ok(()) => { log::info!("setBuiltin({channel}, {name_str}) OK"); Ok(true) }
            Err(e) => { log::error!("setBuiltin failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelInstrument<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        if handle == 0 || channel < 0 {
            return env.new_string("").map(|s| s.into_raw());
        }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let s = engine.channel_instrument(channel as u16).unwrap_or_default();
        env.new_string(&s).map(|x| x.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineInstrumentCatalog<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        let s = EngineHandle::instrument_catalog();
        env.new_string(&s).map(|x| x.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerAddEffect<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint, name: JString<'caller>,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || track < 0 || slot < 0 { return Ok(false); }
        let name_str = name.to_string();
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.mixer_add_effect(track as u16, slot as u8, &name_str) {
            Ok(()) => { log::info!("addEffect(t={track},s={slot},{name_str}) OK"); Ok(true) }
            Err(e) => { log::error!("addEffect failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerRemoveEffect<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || track < 0 || slot < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.mixer_remove_effect(track as u16, slot as u8) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("removeEffect failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerSlotEffect<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        if handle == 0 || track < 0 || slot < 0 {
            return env.new_string("").map(|s| s.into_raw());
        }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let s = engine.mixer_slot_effect(track as u16, slot as u8);
        env.new_string(&s).map(|x| x.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineEffectCatalog<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        let s = EngineHandle::effect_catalog();
        env.new_string(&s).map(|x| x.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelColor<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, r: jint, g: jint, b: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        let res = if r < 0 || g < 0 || b < 0 {
            engine.clear_channel_color(channel as u16)
        } else {
            engine.set_channel_color(channel as u16, r as u8, g as u8, b as u8)
        };
        match res {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setChannelColor failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelColor<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || channel < 0 { return Ok(-1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        match engine.channel_color(channel as u16) {
            Some((r, g, b)) => {
                let packed = ((r as i32) << 16) | ((g as i32) << 8) | (b as i32);
                Ok(packed)
            }
            None => Ok(-1),
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerBypassEffect<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint, on: jboolean,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || track < 0 || slot < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.mixer_bypass_effect(track as u16, slot as u8, on) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("bypassEffect failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerEffectBypassed<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || track < 0 || slot < 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.mixer_is_effect_bypassed(track as u16, slot as u8))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineEffectParamCount<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || track < 0 || slot < 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.effect_param_count(track as u16, slot as u8) as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineEffectParamMeta<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint, index: jint,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        if handle == 0 || track < 0 || slot < 0 || index < 0 {
            return env.new_string("").map(|s| s.into_raw());
        }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let s = engine.effect_param_meta(track as u16, slot as u8, index as u32);
        env.new_string(&s).map(|x| x.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineEffectParamValue<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint, id: jint,
) -> jdouble {
    unowned_env.with_env(|_env| -> jni::errors::Result<jdouble> {
        if handle == 0 || track < 0 || slot < 0 || id < 0 { return Ok(0.0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.effect_param_value(track as u16, slot as u8, id as u32))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineEffectParamText<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint, id: jint, value: jdouble,
) -> jstring {
    unowned_env.with_env(|env| -> jni::errors::Result<jstring> {
        if handle == 0 || track < 0 || slot < 0 || id < 0 {
            return env.new_string("").map(|s| s.into_raw());
        }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        let s = engine.effect_param_text(track as u16, slot as u8, id as u32, value);
        env.new_string(&s).map(|x| x.into_raw())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineEffectSetParam<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, slot: jint, id: jint, value: jdouble,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || track < 0 || slot < 0 || id < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.effect_set_param(track as u16, slot as u8, id as u32, value) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setParam failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelStartOffset<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, offset: jdouble,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_start_offset(channel as u16, offset) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setStartOffset failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelStartOffset<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jdouble {
    unowned_env.with_env(|_env| -> jni::errors::Result<jdouble> {
        if handle == 0 || channel < 0 { return Ok(0.0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_start_offset(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineMixerSwapEffects<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, a: jint, b: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || track < 0 || a < 0 || b < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.mixer_swap_effects(track as u16, a as u8, b as u8) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("swapEffects failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineTakeMasterPeak<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jfloat {
    unowned_env.with_env(|_env| -> jni::errors::Result<jfloat> {
        if handle == 0 { return Ok(0.0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.take_master_peak())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineClearMasterPeak<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) {
    unowned_env.with_env(|_env| -> jni::errors::Result<()> {
        if handle == 0 { return Ok(()); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        engine.clear_master_peak();
        Ok(())
    }).resolve::<ThrowRuntimeExAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineRenderStem<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, track: jint, fd: jint, song_mode: jboolean, rate: jint,
    bits: jint,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 || track <= 0 || fd < 0 || rate <= 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        match engine.render_stem_fd(track as u16, fd, song_mode, rate as u32, bits as u32) {
            Ok(n) => Ok(n as jlong),
            Err(e) => { log::error!("renderStem failed: {e}"); Ok(0) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelPitch<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || channel < 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_pitch_cents(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelPitch<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, cents: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_pitch(channel as u16, cents) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setPitch failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelLoopStart<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 || channel < 0 { return Ok(-1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_loop_start(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelLoopEnd<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 || channel < 0 { return Ok(-1); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_loop_end(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelLoopPoints<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, start: jlong, end: jlong,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_loop_points(channel as u16, start, end) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setLoopPoints failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineClearChannelLoopPoints<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.clear_channel_loop_points(channel as u16) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("clearLoop failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelUseLoop<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_use_loop(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelUseLoop<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, on: jboolean,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_use_loop(channel as u16, on) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setUseLoop failed: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelSampleFrames<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jlong {
    unowned_env.with_env(|_env| -> jni::errors::Result<jlong> {
        if handle == 0 || channel < 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_sample_frames(channel as u16) as jlong)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelStretchMode<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || channel < 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_stretch_mode(channel as u16) as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelStretchMode<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, mode: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_stretch_mode(channel as u16, mode) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setStretchMode: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelStretchMultiplier<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jdouble {
    unowned_env.with_env(|_env| -> jni::errors::Result<jdouble> {
        if handle == 0 || channel < 0 { return Ok(1.0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_stretch_multiplier(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelStretchMultiplier<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, multiplier: jdouble,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_stretch_multiplier(channel as u16, multiplier) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setStretchMul: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelStretchPitch<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jint {
    unowned_env.with_env(|_env| -> jni::errors::Result<jint> {
        if handle == 0 || channel < 0 { return Ok(0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_stretch_pitch(channel as u16) as jint)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelStretchPitch<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, cents: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_stretch_pitch(channel as u16, cents) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setStretchPitch: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelFxFlag<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, flag: jint, on: jboolean,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_fx_flag(channel as u16, flag as u16, on) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setFxFlag: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelFxFlag<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, flag: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_fx_flag(channel as u16, flag as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelFxTrim<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, start: jdouble, length: jdouble,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_fx_trim(channel as u16, start, length) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setFxTrim: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelFxTrimStart<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jdouble {
    unowned_env.with_env(|_env| -> jni::errors::Result<jdouble> {
        if handle == 0 || channel < 0 { return Ok(0.0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_fx_trim_start(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelFxTrimLength<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jdouble {
    unowned_env.with_env(|_env| -> jni::errors::Result<jdouble> {
        if handle == 0 || channel < 0 { return Ok(1.0); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_fx_trim_length(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineSetChannelFxNormalize<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint, on: jboolean,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &mut *(handle as *mut EngineHandle) };
        match engine.set_channel_fx_normalize(channel as u16, on) {
            Ok(()) => Ok(true),
            Err(e) => { log::error!("setFxNorm: {e}"); Ok(false) }
        }
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineChannelFxNormalize<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong, channel: jint,
) -> jboolean {
    unowned_env.with_env(|_env| -> jni::errors::Result<bool> {
        if handle == 0 || channel < 0 { return Ok(false); }
        let engine = unsafe { &*(handle as *const EngineHandle) };
        Ok(engine.channel_fx_normalize(channel as u16))
    }).resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineClearSampleCache<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
) {
    unowned_env.with_env(|_env| -> jni::errors::Result<()> {
        crate::sample_fx::clear_cache();
        Ok(())
    }).resolve::<ThrowRuntimeExAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineStartAudio<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> jboolean {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            if engine.device.is_some() {
                log::info!("audio already running");
                return Ok(true);
            }
            if engine.engine.is_none() {
                log::warn!("engineStartAudio: no project loaded");
                return Ok(false);
            }
            let engine_arc = engine.engine.clone();
            match audio::AudioDevice::new(engine_arc, engine.master_peak.clone()) {
                Ok(dev) => {
                    engine.set_device(dev);
                    log::info!("audio device started");
                    Ok(true)
                }
                Err(e) => {
                    log::error!("audio device failed: {e}");
                    Ok(false)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineStopAudio<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<()> {
            if handle == 0 {
                return Ok(());
            }
            let engine = unsafe { &mut *(handle as *mut EngineHandle) };
            engine.device = None;
            log::info!("audio device stopped");
            Ok(())
        })
        .resolve::<ThrowRuntimeExAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineDispose<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) {
    unowned_env
        .with_env(|_env| -> jni::errors::Result<()> {
            if handle == 0 {
                return Ok(());
            }
            unsafe {
                drop(Box::from_raw(handle as *mut EngineHandle));
            }
            log::info!("engine disposed");
            Ok(())
        })
        .resolve::<ThrowRuntimeExAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_maxixa_lemon_LemonEngineBridge_engineVersion<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jstring> {
            ensure_logger();
            let s = env.new_string("0.1.0-termux")?;
            Ok(s.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
