
Instrument hints

Extensions declare a category, not a specific instrument. Lemon maps to whatever ships today — and to whatever ships next.

Categories

Category Meaning Lemon default
Drums Kit — kick/snare/hats lemon-sampler drum kit
Bass Bass line, low register lemon-3x-osc bass preset
Lead Lead melody, mid-high lemon-3x-osc lead
Pad Sustained background citrus pad
Keys Piano, EP, organ lemon-sampler keys
Guitar Plucked/strummed lemon-sampler guitar
Strings Bowed (violin, cello) citrus strings
Brass Trumpet, horn lemon-3x-osc brass
Woodwind Flute, clarinet lemon-3x-osc reed
Vocal Synth voice, choir citrus vocal
Percussion Orchestral perc (not kit) lemon-sampler perc
Fx Risers, impacts, textures lemon-sampler fx
Other Anything else lemon-3x-osc default

Extensions never know what Lemon ships. Just pick a category.

Preferred (escape hatch)

```rust
// Portable — works with any Lemon version
InstrumentHint::of(InstrumentCategory::Bass)

// Specific — only if you really need a particular sound
InstrumentHint::specific(InstrumentCategory::Lead, "lemon-3x-osc:fat-saw")
```

Use preferred sparingly. It's a "make it sound exactly like this" escape hatch, not a default. Categories are portable; preferred is brittle.

Resolution algorithm (host side)

1. If preferred set → try to load preset lemon-3x-osc:fat-saw. If found → use it.
2. Else, use Lemon's default preset for the category.
3. Else, use lemon-sampler with the category's built-in preset.
4. Else, fall back to any instrument.

Step 4 never fails. Extension output always produces a playable sketch, even if all instrument hints are unresolvable. Host degrades gracefully.

Version pinning for preferred

If you use preferred, pin a note in your extension's README: "expects Lemon X.Y or later". Category-only extensions work everywhere.

Why categories

· Portable: same extension works on Lemons that ship different instruments
· Composable: user can remap categories to their favourite instruments
· Forward-compatible: new instruments get slotted into existing categories
