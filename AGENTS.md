# Overview

Kotlin Multiplatform bindings for libghostty-vt (the terminal emulation core of Ghostty) for Android, the desktop
JVM (Linux, macOS, Windows), and browser JavaScript/WebAssembly, published to Maven Central as
`io.github.xchacha20-poly1305:libghostty[-extras|-compose]`. Kotlin package: `io.github.xchacha20_poly1305.libghostty`.
All library modules use `explicitApi()`.

# Public API changes

Add an English KDoc `@since x.y.z` to each new public declaration in a published library, including
overloads, enum entries, and public constructor properties. Use the library version in which the
declaration first becomes public, with no `v` prefix or `-SNAPSHOT` suffix. Keep that version when
moving a declaration between source sets or making it available on more platforms. Inherited
members use the original declaration's documentation. Document generated APIs in their generator.

# Commands

Requires `zig` (version in `ZIG_VERSION` of `.github/workflows/build.yml`; must satisfy `build/ghostty/source/build.zig.zon`'s
`minimum_zig_version`), `tic` (ncurses), JDK 17, and for Android the NDK version pinned in `gradle/libs.versions.toml`
(`ndk`). Ghostty is fetched automatically at the `GHOSTTY_COMMIT` revision in `gradle.properties`. With
`-PGHOSTTY_PREBUILT=<dir>` (an export written by `./gradlew ghosttyPrebuilt` to `build/ghostty/prebuilt`), the build
copies the native outputs from there and needs none of zig, tic, the NDK or the ghostty source.

- Build everything: `./gradlew assemble`
- Desktop tests (JVM, no device): `./gradlew :library:desktopTest :library-extras:desktopTest`
- Android tests (need a device/emulator): `./gradlew :library:connectedAndroidDeviceTest :library-extras:connectedAndroidDeviceTest`
- Single test class: `./gradlew :library:desktopTest --tests '*GhosttyVtBridgeTest'`
- Keep rules: `./gradlew :shrink-check:shrinkCheck` shrinks a minimal consumer with R8 and loads the JNI library
- Bridge only: prepare sources with `./gradlew ghosttySource`, then `cd native && zig build -Dtarget=x86_64-linux-gnu` (`ANDROID_NDK_HOME` for Android targets);
  `zig build gen-kotlin` writes the generated Kotlin enums
- Try it by hand: `./gradlew :demo-desktop:run` (shell through pty4j), `:demo-android:installDebug` (echo session)
- API documentation: `make docs` runs `:dokkaGeneratePublicationHtml` and prints the HTTP preview command.
  `make pages` assembles the full site.
- Publish snapshot: `./gradlew publishToMavenCentral` (release: `publishAndReleaseToMavenCentral -PVERSION_NAME=x.y.z`)

The core bridge tests in `jvmSharedTest` run on both the desktop JVM and Android (`androidDeviceTest`).
Browser integration tests run the real Wasm backend through the web demo; keep JVM-only test APIs out of commonTest.

# Architecture

Modules (`settings.gradle.kts`): `:library` (core), `:library-extras` (themes, terminfo, fonts), `:library-compose`
(Compose Multiplatform terminal, minSdk 23; others minSdk 21), plus the unpublished `:demo-desktop`, `:demo-android`
and `:shrink-check`.
Each library module has `android` (AGP's KMP plugin) and `jvm("desktop")`; core and Compose also have
`js` and `wasmJs`. JVM utilities belong in jvmSharedMain or platform source sets, never commonMain.

**Native pipeline (the non-obvious part).** The JNI library is written in Zig (`native/`) and built by `buildSrc`
plugins before the Kotlin compile:
- `GhosttySource.kt` (`GhosttyEnv`, `GhosttySourceTask`): fetches and checks out the pinned `GHOSTTY_COMMIT` into
  `build/ghostty/source` and applies `patches/ghostty/*.patch`. The task is up to date only if HEAD matches the commit
  and the patches are already applied, so edit patches rather than the generated source tree. Keep patch filenames numbered consecutively.
- `native/build.zig` depends on that checkout by path and imports its `ghostty-vt` **Zig module**, not the C API. The
  Zig API is unstable, so ghostty updates may need bridge changes.
- `GhosttyNativePlugin.kt` (applied by `:library`): `ghosttyAndroidJni` builds `libghostty_jni.so` for the four ABIs
  (API 21, linked against bionic from the NDK sysroot via `native/android.zig`, 16 KB pages) into jniLibs;
  `ghosttyDesktopJni` cross-compiles Linux/macOS/Windows x86_64/aarch64 into jar resources under
  `io/github/xchacha20_poly1305/libghostty/native/<os>-<arch>/`, which `Platform.desktop.kt` extracts to the user
  cache directory and loads; `ghosttyGenKotlin` runs `native/src/gen_kotlin.zig` to generate `GhosttyKey` and
  `GhosttyMods` from ghostty's input types.
- `GhosttyAssetsPlugin` (applied by `:library-extras`): downloads the iTerm2 themes referenced in `build/ghostty/source/build.zig.zon`,
  checks their hash with `zig fetch` against a private cache, and compiles `xterm-ghostty` terminfo with `tic`, as Java resources.
- Every task above writes a `GhosttyOutputTask.outputDir` and is registered through `GhosttyEnv.register`, which adds
  it to the root `ghosttyPrebuilt` export or, with `GHOSTTY_PREBUILT`, replaces it by a `GhosttyPrebuiltTask` copying
  the export. The zig tasks are build-cache cacheable (inputs: native sources, patches, commit, zig version, NDK
  `source.properties`). `:library` reaches them through the `ghosttyNative` extension (`GhosttyNative`).

**JNI registration.** `JNI_OnLoad` (`native/src/main.zig`) registers every public function of the `natives` struct as
the `GhosttyJni` native method of the same name; `jni.zig` derives the JNI signature from the Zig parameter types
(`jni.Ref("L...;")` for objects). Calls from native into Kotlin go through `GhosttyVtCallbacks`, whose methods
`JNI_OnLoad` also resolves by name. The native side must reach no other Kotlin class: `library/consumer-rules.pro`
(shipped in the AAR and, under `META-INF/proguard/`, in the desktop jar) keeps exactly these two classes whole, and
`:shrink-check` fails when an app that minifies could not load the library. Renaming a native method means renaming
the Zig function; renaming the package means updating `package` in `main.zig` and `library/consumer-rules.pro`. ELF
builds export only `JNI_OnLoad` (`native/jni.map`).

**Kotlin layer.** In `:library`, `GhosttyBackend` is the shared backend contract; `GhosttyJni` is the internal JNI surface; `GhosttyTerminalSession` owns a terminal,
exposes state flows and an `EventListener`, talks to the host through a `Transport` (`feedOutput`/`finish` are
thread safe; everything else is main thread only), reaches the system clipboard through a `GhosttyClipboard`, and
notifies the UI showing it through `GhosttyTerminalHost`. `session.withTerminal { }` lends a typed `GhosttyTerminal`
(encoding, selection, scrolling), and `GhosttyRenderState` turns terminal snapshots (a little-endian buffer format
shared with `native/src/snapshot.zig`) into the grid a renderer draws.
In `:library-compose`, `GhosttyTerminal` (composable) draws a `GhosttyRenderState` in `commonMain`
(`TerminalController`, `TerminalDrawing`, `TerminalPointerInput`); text drawing (`GlyphPainter`) and the input method
(`Modifier.terminalInput`) are per platform. `GhosttyTerminalState` carries settings and the prompts `GhosttyDialogs`
shows; strings are Compose resources (`composeResources/values*`). The widgets around the terminal
(`GhosttyExtraKeysBar`, `GhosttyScrollIndicator`, `GhosttyJumpToBottomButton`, `GhosttyDialogs`) are separate composables
that use only `GhosttyTerminalState`'s public API, so apps can swap in their own; keep the terminal itself free of them.

# Build configuration

- Versions live in `gradle/libs.versions.toml` (including `ndk`, `kotlin`, `composeMultiplatform`); the root
  `build.gradle.kts` applies `explicitApi()`, JVM 17, compileSdk and minSdk to every KMP module.
- `:demo-android` is the only module on AGP's built-in Kotlin; its build file raises that Kotlin to the catalog version.
- Maven group/version/POM metadata come from `gradle.properties` (`GROUP`, `VERSION_NAME`, `POM_*`) and each module's
  `gradle.properties` (`POM_ARTIFACT_ID`).
- CI (`.github/workflows/build.yml`, composite action `.github/actions/setup`): the `native` job builds the
  `ghosttyPrebuilt` export once (the only job with zig, tic and the NDK; it alone caches the zig cache directory) and
  uploads it as the `ghostty-prebuilt` artifact; the other jobs build against it. They build everything on Linux, run
  the desktop tests on Linux, macOS and Windows and the Android tests on an emulator, and publish on `v*` tags.

# Browser target

`:library` and `:library-compose` target browser `js` and `wasmJs`. `webMain` shares browser logic using `kotlinx-browser` APIs for DOM, fetch, clipboard and events;
keep raw JavaScript limited to the Wasm backend ABI in `jsMain`/`wasmJsMain`. `jvmSharedMain` holds JNI, direct ByteBuffer
storage and JVM synchronization; `skiaMain` shares text shaping, glyph fallback and IME state between
JS/Wasm browsers and desktop. Keep Java imports out of commonMain. The browser ABI (`native/src/wasm.zig`) shares
Session and snapshot logic with JNI; Session callbacks carry host-neutral contexts, never JNI types.
`ghosttyWasm` participates in the prebuilt export. Browser apps must await GhosttyWebAssembly.load()
before constructing a session. Both core browser publications attach the binary with classifier ghostty-vt / extension wasm.

`:demo-web:offlineZip` builds a static package that must open directly through file:// without a local server.
Webpack adapts Skiko's loader before minification; the packager converts runtime assets into classic
script data. Keep its loader-contract assertions. Never replace the offline contract with a local server.
CJK and emoji fonts may be fetched from Google Fonts and jsDelivr when online; the bundled Latin face is local.
`:demo-web:preparePages` assembles `demo-web/build/pages` with the aggregated English Dokka
documentation at the root and both demo outputs at `demo/js/` and `demo/wasm/`. Pages has no ZIP files.
The `cmp` workflow mirrors the tested Pages artifact to `gh-pages` and deploys it through GitHub Actions.
Release ZIPs are `demo-web-js-<version>.zip` and `demo-web-wasm-<version>.zip`.
The web demo chrome is Compose Multiplatform (language: browser locale, Chinese or English;
appearance: system / light / dark). `.github/workflows/web.yml` gates publication on the
Pages Playwright tests for JS and Wasm in Chromium, Firefox and WebKit. Run `make demo-web`,
then `npm ci`, `npx playwright install` and `npm test` from `demo-web/browser-tests` for the
full suite, including the standalone ZIP tests for keyboard input, IME, clear-button focus,
completion, Unicode/emoji, paste and resizing. Preserve reports, screenshots and failure traces.
