
Custom UI — the escape hatch

95% of extensions need only declarative UI. For the other 5% — drum grid designers, scale visualizers, waveform previews — the custom UI escape hatch exists.

When to use

Use custom UI if… Use declarative if…
Layout is a spatial grid (drum machine) Parameters fit in a form
Interactive visual editor (scale lock painter) User adjusts sliders and hits Apply
Preview needs full Compose (waveform, spectrogram) Notes / Timeline previews suffice

Start declarative. Only add custom when you have a specific user need. Every custom screen is Kotlin + Rust you must maintain.

Contract

Rust side:

```rust
fn ui(&self) -> UiSpec {
    UiSpec::empty()
        .with_preview(PreviewKind::Custom(CustomUiId("my-ext.drum-grid-v1")))
        // ... sections ...
}
```

Kotlin side — one screen class:

```kotlin
package com.maxixa.lemon.ext.screens

import androidx.compose.runtime.*
import com.maxixa.lemon.ext.CustomExtScreen
import com.maxixa.lemon.ext.CustomExtResult
import com.maxixa.lemon.ext.ParamState

class MyDrumGridScreen : CustomExtScreen {
    override val id = "my-ext.drum-grid-v1"

    @Composable
    override fun Render(
        params: ParamState,
        onResult: (CustomExtResult) -> Unit,
    ) {
        // Your Compose UI here. Call onResult(SongSketch | Notes) when done.
    }
}
```

Register in android-ui/ext-api/src/main/kotlin/.../CustomExtScreens.kt:

```kotlin
val ALL = listOf<CustomExtScreen>(
    MyDrumGridScreen(),
    // add more here as you build them
)
```

Static list — no ServiceLoader, no reflection. Broken linkage = build error, not runtime crash.

Registration rule

CustomUiId must be namespaced by extension id:

· ✅ "my-ext.drum-grid-v1"
· ❌ "drum-grid" (collision risk)

The host looks up by exact string. If no CustomExtScreen is registered for the id, host falls back to PreviewKind::Notes — extension keeps working, just with a less rich preview. Custom UI is additive, never required.

Bidirectional data flow

The custom screen receives ParamState (a MutableState<Map<String, ParamValue>>) mirroring the extension's params. When user finishes interacting, screen calls onResult(...) with:

· SongSketch — for song generators
· Vec<Note> — for note editors/generators

Host applies the result identically to the declarative path. Undo, validation, error handling all identical. Custom UI is purely visual.

Versioning

v1 suffix is a habit — when you change the Kotlin screen signature incompatibly, bump to v2. Old Rust extension still requests v1; the old screen still exists; both coexist. Over time, deprecate v1.
