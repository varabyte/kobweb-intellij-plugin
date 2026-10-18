# Worker golden files

`WorkerClassSynthesizerParityTest` checks that the `Worker` class the plugin synthesizes has the same public API as the
one Kobweb's real worker processor (KSP) generates. Rather than running KSP during tests, it compares against output
that was generated ahead of time and checked in here.

## Layout

Each folder under `cases/` is one test case:

* `src/` — the worker module's sources (usually a `WorkerFactory` implementation and its input / output types)
* `case.properties` — *(optional)* settings for the case. Currently just `fqcn`, the value of `kobweb.worker.fqcn`
* `expected/` — the worker class that Kobweb generated for this case, at a path matching its package. **Generated; do
  not edit by hand.**

Each case also needs a matching test method in `WorkerClassSynthesizerParityTest`.

## Regenerating

`generator/` is a standalone Kobweb Gradle project that treats every case as its own worker module, the same way a real
user's project would be set up. It reads the Kobweb and Kotlin versions from this repo's `gradle/libs.versions.toml`, so
golden files always come from the same Kobweb version that tests run against.

From the root of this repo, run:

```bash
./gradlew -p plugin/src/test/testData/worker/generator updateGoldenFiles
```

This runs Kobweb's processor on every case and replaces the contents of each `expected/` folder with its output. Then
review the changes with `git diff` before running the tests.

You should regenerate after:

* adding or changing a case
* bumping the `kobweb` version in `gradle/libs.versions.toml`

If the parity test fails after regenerating, the generated class changed in a way the synthesized version doesn't
match yet, so update `WorkerClassSynthesizer.kt` accordingly.

## Adding a case

1. Create a new folder under `cases/` with a `src/` folder (and a `case.properties` file, if needed).
2. Run the `updateGoldenFiles` command above.
3. Add a test method for it in `WorkerClassSynthesizerParityTest`.
