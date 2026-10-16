# Overview

Android bindings for libghostty-vt (the terminal emulation core of Ghostty), published to Maven Central as
`io.github.xchacha20-poly1305:libghostty-android[-extras|-compose]`. Kotlin package:
`io.github.xchacha20_poly1305.libghostty`. All modules use `explicitApi()`.

# Commands

Requires `zig` (version in `ZIG_VERSION` of `.github/workflows/build.yml`; must satisfy `ghostty/build.zig.zon`'s
`minimum_zig_version`), `tic` (ncurses), JDK 17, and the NDK version pinned in `gradle/libs.versions.toml`
(`ndk`). Clone with `--recurse-submodules` (`ghostty/` is a submodule).

- Build everything: `./gradlew assembleRelease`
- Compile only: `./gradlew :library:assembleDebug :library-extras:compileDebugKotlin :library-compose:compileDebugKotlin`
- Instrumented tests (need a device/emulator): `./gradlew :library:connectedDebugAndroidTest :library-extras:connectedDebugAndroidTest`
- Single test class: append `-Pandroid.testInstrumentationRunnerArguments.class=io.github.xchacha20_poly1305.libghostty.GhosttyVtSmokeTest`
- Publish snapshot: `./gradlew publishToMavenCentral` (release: `publishAndReleaseToMavenCentral -PVERSION_NAME=x.y.z`)

There are no JVM unit tests or lint tasks configured.

# Architecture

Modules (`settings.gradle.kts`): `:library` (core), `:library-extras` (themes/fonts/terminfo assets),
`:library-compose` (Compose wrappers, minSdk 23; others minSdk 21). Extras and compose depend on `:library` via `api`.

**Native pipeline (the non-obvious part).** `buildSrc` holds Gradle plugins that build Ghostty from the `ghostty/`
submodule, with zig, before the normal Android build:
- `GhosttySource.kt` (`GhosttyEnv`, `GhosttySourceTask`): checks out the pinned submodule commit and applies
  `patches/ghostty/*.patch` (Android-specific fixes and lib-vt additions). The task is up to date only if HEAD
  matches the commit and the patches are already applied, so edit patches rather than the submodule tree.
- `GhosttyNativePlugin.kt` (applied by `:library`): `zig build -Demit-lib-vt` for the four ABIs
  (arm64-v8a, armeabi-v7a, x86_64, x86, API 21) into `library/build/ghostty-vt/<abi>/libghostty-vt.a` plus headers;
  hooked into `preBuild`.
- `library/src/main/cpp/CMakeLists.txt` links that static lib into `libghostty_android.so` together with
  `ghostty_jni.cpp` (single JNI file; 16 KB page size alignment enabled).
- `GhosttyAssetsPlugin` (applied by `:library-extras`): fetches the iTerm2 themes referenced in
  `ghostty/build.zig.zon` via `zig fetch` and compiles `xterm-ghostty` terminfo with `tic`, shipped as AAR assets.

**JNI naming.** `ghostty_jni.cpp` function names encode the package (`Java_io_github_xchacha20_1poly1305_libghostty_...`;
`_` in the package becomes `_1`), and `FindClass` uses the slash form. Renaming the package or a native method
requires updating `ghostty_jni.cpp` and `library/consumer-rules.pro` too.

**Kotlin layer in `:library`.** `GhosttyVt` is the thin JNI wrapper over a native terminal handle;
`GhosttyTerminalSession` owns a terminal, exposes state flows and an `EventListener`, and talks to the host through a
`Transport` (`feedOutput`/`finish` are thread safe; everything else is main thread only);
`GhosttyTerminalView` is the Android `View` that renders snapshots and handles input/IME/selection.
`GhosttyUiHandler` / `GhosttyDialogUiHandler` are the callbacks the host app implements. `:library-compose`
wraps these in composables (`GhosttyTerminal`, `GhosttyExtraKeys`, dialogs).

# Build configuration

- Versions live in `gradle/libs.versions.toml` (including `ndk` and `kotlin`); the root `build.gradle.kts` reads
  `libs.versions.kotlin` to force Kotlin versions on AGP's built-in Kotlin configurations (needs
  `kotlin.compiler.runViaBuildToolsApi=true` in `gradle.properties`).
- Maven group/version/POM metadata come from `gradle.properties` (`GROUP`, `VERSION_NAME`, `POM_*`).
- CI (`.github/workflows/build.yml`, composite action `.github/actions/setup`) installs the NDK via sdkmanager
  from the toml version, caches the zig objects, runs instrumented tests on an emulator, and publishes on `v*` tags.
