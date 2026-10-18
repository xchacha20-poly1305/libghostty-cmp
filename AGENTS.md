# Overview

Kotlin Multiplatform bindings for libghostty-vt (the terminal emulation core of Ghostty) for Android and the desktop
JVM (Linux, macOS, Windows), published to Maven Central as
`io.github.xchacha20-poly1305:libghostty[-extras|-compose]`. Kotlin package: `io.github.xchacha20_poly1305.libghostty`.
All library modules use `explicitApi()`.

# Commands

Requires `zig` (version in `ZIG_VERSION` of `.github/workflows/build.yml`; must satisfy `ghostty/build.zig.zon`'s
`minimum_zig_version`), `tic` (ncurses), JDK 17, and for Android the NDK version pinned in `gradle/libs.versions.toml`
(`ndk`). Clone with `--recurse-submodules` (`ghostty/` is a submodule).

- Build everything: `./gradlew assemble`
- Desktop tests (JVM, no device): `./gradlew :library:desktopTest :library-extras:desktopTest`
- Android tests (need a device/emulator): `./gradlew :library:connectedAndroidDeviceTest :library-extras:connectedAndroidDeviceTest`
- Single test class: `./gradlew :library:desktopTest --tests '*GhosttyVtBridgeTest'`
- Bridge only: `cd native && zig build -Dtarget=x86_64-linux-gnu` (`ANDROID_NDK_HOME` for Android targets);
  `zig build gen-kotlin` writes the generated Kotlin enums
- Try it by hand: `./gradlew :demo-desktop:run` (shell through pty4j), `:demo-android:installDebug` (echo session)
- Publish snapshot: `./gradlew publishToMavenCentral` (release: `publishAndReleaseToMavenCentral -PVERSION_NAME=x.y.z`)

The tests in `commonTest` run both on the desktop JVM and, through `androidDeviceTest`, on Android.

# Architecture

Modules (`settings.gradle.kts`): `:library` (core), `:library-extras` (themes, terminfo, fonts), `:library-compose`
(Compose Multiplatform terminal, minSdk 23; others minSdk 21), plus the unpublished `:demo-desktop` and `:demo-android`.
Each library module has the targets `android` (AGP's KMP plugin) and `jvm("desktop")`. With only JVM targets,
`commonMain` uses JDK APIs directly.

**Native pipeline (the non-obvious part).** The JNI library is written in Zig (`native/`) and built by `buildSrc`
plugins before the Kotlin compile:
- `GhosttySource.kt` (`GhosttyEnv`, `GhosttySourceTask`): checks out the pinned submodule commit into
  `build/ghostty/source` and applies `patches/ghostty/*.patch`. The task is up to date only if HEAD matches the commit
  and the patches are already applied, so edit patches rather than the submodule tree.
- `native/build.zig` depends on that checkout by path and imports its `ghostty-vt` **Zig module**, not the C API. The
  Zig API is unstable, so ghostty updates may need bridge changes.
- `GhosttyNativePlugin.kt` (applied by `:library`): `ghosttyAndroidJni` builds `libghostty_jni.so` for the four ABIs
  (API 21, linked against bionic from the NDK sysroot via `native/android.zig`, 16 KB pages) into jniLibs;
  `ghosttyDesktopJni` cross-compiles Linux/macOS/Windows x86_64/aarch64 into jar resources under
  `io/github/xchacha20_poly1305/libghostty/native/<os>-<arch>/`, which `Platform.desktop.kt` extracts to the user
  cache directory and loads; `ghosttyGenKotlin` runs `native/src/gen_kotlin.zig` to generate `GhosttyKey` and
  `GhosttyMods` from ghostty's input types.
- `GhosttyAssetsPlugin` (applied by `:library-extras`): fetches the iTerm2 themes referenced in `ghostty/build.zig.zon`
  via `zig fetch` and compiles `xterm-ghostty` terminfo with `tic`, as Java resources.

**JNI registration.** `JNI_OnLoad` (`native/src/main.zig`) registers every public function of the `natives` struct as
the `GhosttyVt` native method of the same name; `jni.zig` derives the JNI signature from the Zig parameter types
(`jni.Ref("L...;")` for objects). Renaming a native method means renaming the Zig function; renaming the package means
updating `package` in `main.zig` and `library/consumer-rules.pro`. ELF builds export only `JNI_OnLoad` (`native/jni.map`).

**Kotlin layer.** In `:library`, `GhosttyVt` is the internal JNI surface; `GhosttyTerminalSession` owns a terminal,
exposes state flows and an `EventListener`, talks to the host through a `Transport` (`feedOutput`/`finish` are
thread safe; everything else is main thread only), reaches the system clipboard through a `GhosttyClipboard`, and
notifies the UI showing it through `GhosttyTerminalHost`. `session.withTerminal { }` lends a typed `GhosttyTerminal`
(encoding, selection, scrolling), and `GhosttyRenderState` turns terminal snapshots (a little-endian buffer format
shared with `native/src/snapshot.zig`) into the grid a renderer draws.
In `:library-compose`, `GhosttyTerminal` (composable) draws a `GhosttyRenderState` in `commonMain`
(`TerminalController`, `TerminalDrawing`, `TerminalPointerInput`); text drawing (`GlyphPainter`) and the input method
(`Modifier.terminalInput`) are per platform. `GhosttyTerminalState` carries settings and the prompts `GhosttyDialogs`
shows; strings are Compose resources (`composeResources/values*`).

# Build configuration

- Versions live in `gradle/libs.versions.toml` (including `ndk`, `kotlin`, `composeMultiplatform`); the root
  `build.gradle.kts` applies `explicitApi()`, JVM 17, compileSdk and minSdk to every KMP module.
- `:demo-android` is the only module on AGP's built-in Kotlin; its build file raises that Kotlin to the catalog version.
- Maven group/version/POM metadata come from `gradle.properties` (`GROUP`, `VERSION_NAME`, `POM_*`) and each module's
  `gradle.properties` (`POM_ARTIFACT_ID`).
- CI (`.github/workflows/build.yml`, composite action `.github/actions/setup`) builds everything on Linux, runs the
  desktop tests on Linux, macOS and Windows and the Android tests on an emulator, and publishes on `v*` tags.
