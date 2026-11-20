//! Deterministic SplitMix64 RNG. Use this instead of `rand` so that
//! extensions built against `lemon-ext-api = "1"` always produce the same
//! output for the same seed — enables "Regenerate" UX that rerolls and
//! "Reroll same seed" that reproduces.

pub struct ExtRng { state: u64 }

impl ExtRng {
    pub fn new(seed: u64) -> Self {
        Self { state: if seed == 0 { 0x9E3779B97F4A7C15 } else { seed } }
    }
    pub fn next_u64(&mut self) -> u64 {
        self.state = self.state.wrapping_add(0x9E3779B97F4A7C15);
        let mut z = self.state;
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58476D1CE4E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D049BB133111EB);
        z ^ (z >> 31)
    }
    pub fn next_u32(&mut self) -> u32 { self.next_u64() as u32 }
    pub fn next_f32(&mut self) -> f32 {
        ((self.next_u64() >> 40) as f32) / ((1u64 << 24) as f32)
    }
    pub fn range_f32(&mut self, lo: f32, hi: f32) -> f32 {
        if hi <= lo { return lo; }
        lo + self.next_f32() * (hi - lo)
    }
    pub fn range_i32(&mut self, lo: i32, hi_inclusive: i32) -> i32 {
        if hi_inclusive <= lo { return lo; }
        let span = (hi_inclusive - lo + 1) as u64;
        lo + (self.next_u64() % span) as i32
    }
    pub fn choose<'a, T>(&mut self, items: &'a [T]) -> Option<&'a T> {
        if items.is_empty() { return None; }
        Some(&items[(self.next_u64() as usize) % items.len()])
    }
}
