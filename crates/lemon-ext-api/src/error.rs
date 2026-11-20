//! Failure modes any extension may return. The host maps each variant to a
//! user-visible message and never crashes on extension failure.

use std::fmt;

#[non_exhaustive]
#[derive(Debug)]
pub enum ExtError {
    /// A parameter value is out of range or malformed. Show as toast.
    InvalidParams { field: &'static str, reason: String },
    /// Inputs (selection, references, held notes) are unsuitable for this extension.
    InvalidInput { reason: String },
    /// Output violates `SongSketch` / `NoteGeneratorOutput` constraints. Extension bug.
    InvalidOutput { reason: String },
    /// Output exceeds `max_output_notes()`.
    TooManyNotes { limit: usize, got: usize },
    /// Host-enforced timeout exceeded (5s default).
    Timeout,
    /// Unexpected internal error. Logged, not shown to user.
    Internal { reason: String },
    /// Extension does not support the requested feature.
    Unsupported { feature: &'static str },
}

impl fmt::Display for ExtError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidParams { field, reason } => write!(f, "param `{field}`: {reason}"),
            Self::InvalidInput { reason } => write!(f, "invalid input: {reason}"),
            Self::InvalidOutput { reason } => write!(f, "invalid output: {reason}"),
            Self::TooManyNotes { limit, got } => write!(f, "too many notes: {got} > {limit}"),
            Self::Timeout => write!(f, "extension timed out"),
            Self::Internal { reason } => write!(f, "internal: {reason}"),
            Self::Unsupported { feature } => write!(f, "unsupported: {feature}"),
        }
    }
}

impl std::error::Error for ExtError {}

pub type ExtResult<T> = Result<T, ExtError>;
