
Versioning contract

lemon-ext-api promises semver 1.x source compatibility.

Rules

Change Version Allowed?
New trait MINOR ✅
New method with default impl MINOR ✅
New field on #[non_exhaustive] struct (via builder) MINOR ✅
New variant on #[non_exhaustive] enum MINOR ✅
New widget kind MINOR ✅
Bug fix in default impls PATCH ✅
Remove trait / method / field MAJOR ❌ (2.0)
Change method signature MAJOR ❌ (2.0)
Change struct field type MAJOR ❌ (2.0)
Change default return value MAJOR ❌ (2.0)
Remove enum variant MAJOR ❌ (2.0)
Rename MAJOR ❌ (2.0)

Extension author rules

· Pin lemon-ext-api = "1.0" (caret, allows all 1.x). Not "1.0.0" (would lock to a specific patch).
· cargo update inside 1.x should always be safe.
· If you need a feature added in 1.3, set lemon-ext-api = "1.3" — minimum version constraint.
· Never pin to exact version unless you have a reason (usually not).

When 2.0 happens

· 6-month deprecation window: 1.x stays maintained, gets bug fixes, no new features.
· 2.0 ships with both old and new APIs concurrently where possible.
· Migration guide published.
· Extensions can stay on 1.x forever — old versions don't break.

Host evolution

Lemon core can evolve freely (new engine features, new plugin formats) as long as lemon-ext-api stays 1.x. Extensions never see engine internals. Adding a new trait family (e.g., ArrangementEditor) in 1.x is fine — new extension families can appear without breaking old ones.
