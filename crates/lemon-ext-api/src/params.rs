//! Parameter definitions and values. Self-contained (no dependency on the
//! audio-effect param system) so extensions ship against a stable surface.

use std::collections::BTreeMap;

#[non_exhaustive]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ParamCurve {
    Linear,
    Logarithmic,
    Discrete,
}

#[derive(Debug, Clone)]
pub struct ParamSpec {
    pub id: &'static str,
    pub name: &'static str,
    pub min: f32,
    pub max: f32,
    pub default: f32,
    pub unit: &'static str,
    pub curve: ParamCurve,
}

impl ParamSpec {
    pub const fn new(id: &'static str, name: &'static str, min: f32, max: f32, default: f32) -> Self {
        Self { id, name, min, max, default, unit: "", curve: ParamCurve::Linear }
    }
    pub const fn unit(mut self, u: &'static str) -> Self { self.unit = u; self }
    pub const fn curve(mut self, c: ParamCurve) -> Self { self.curve = c; self }
}

#[non_exhaustive]
#[derive(Debug, Clone)]
pub enum ParamValue {
    Float(f32),
    Int(i32),
    Bool(bool),
    Text(String),
}

impl ParamValue {
    pub fn as_f32(&self) -> Option<f32> {
        match self {
            Self::Float(f) => Some(*f),
            Self::Int(i) => Some(*i as f32),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct ParamValues {
    values: BTreeMap<&'static str, ParamValue>,
}

impl ParamValues {
    pub fn new() -> Self { Self::default() }
    pub fn set(&mut self, id: &'static str, v: ParamValue) { self.values.insert(id, v); }
    pub fn get(&self, id: &str) -> Option<&ParamValue> { self.values.get(id) }
    pub fn f32(&self, id: &str) -> Option<f32> { self.get(id)?.as_f32() }
    pub fn i32(&self, id: &str) -> Option<i32> {
        match self.get(id)? {
            ParamValue::Int(i) => Some(*i),
            ParamValue::Float(f) => Some(*f as i32),
            _ => None,
        }
    }
    pub fn bool(&self, id: &str) -> Option<bool> {
        match self.get(id)? {
            ParamValue::Bool(b) => Some(*b),
            _ => None,
        }
    }
    pub fn str(&self, id: &str) -> Option<&str> {
        match self.get(id)? {
            ParamValue::Text(t) => Some(t),
            _ => None,
        }
    }
    pub fn f32_or(&self, id: &str, default: f32) -> f32 { self.f32(id).unwrap_or(default) }
    pub fn i32_or(&self, id: &str, default: i32) -> i32 { self.i32(id).unwrap_or(default) }
    pub fn bool_or(&self, id: &str, default: bool) -> bool { self.bool(id).unwrap_or(default) }
}
