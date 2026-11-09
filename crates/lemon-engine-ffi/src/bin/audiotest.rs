// SPDX-License-Identifier: GPL-3.0-or-later
//! Smoke test: enumerate cpal devices without opening a stream.
use cpal::traits::{DeviceTrait, HostTrait};

fn main() {
    println!("cpal host probe");
    let host = cpal::default_host();
    match host.default_output_device() {
        Some(dev) => {
            println!("default output: {:?}", dev.description().ok());
            match dev.default_output_config() {
                Ok(cfg) => println!("default config: {cfg:?}"),
                Err(e) => println!("default config failed: {e}"),
            }
        }
        None => println!("no default output device"),
    }
    match host.output_devices() {
        Ok(devs) => {
            for (i, d) in devs.enumerate() {
                println!("[{}] {:?}", i, d.description().ok());
            }
        }
        Err(e) => println!("enumerate failed: {e}"),
    }
}
