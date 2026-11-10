# libghostty-cmp

Kotlin Multiplatform bindings for [libghostty-vt](https://github.com/ghostty-org/ghostty/tree/main/src/lib) on
Android, the desktop JVM (Linux, macOS and Windows on x86_64 and aarch64), and browser WebAssembly:
terminal sessions, a Compose
Multiplatform terminal, a theme catalog, and font handling.

```kotlin
dependencies {
    implementation("io.github.xchacha20-poly1305:libghostty:0.5.0")

    // optional
    implementation("io.github.xchacha20-poly1305:libghostty-extras:0.5.0")
    implementation("io.github.xchacha20-poly1305:libghostty-compose:0.5.0")
}
```

```kotlin
val session = GhosttyTerminalSession()
session.transport = myPty            // sends input and window size to the program
// on the thread that reads the program's output:
session.feedOutput(bytes, 0, count)

// in Compose
val state = rememberGhosttyTerminalState()
Box(Modifier.fillMaxSize()) {
    GhosttyTerminal(session, Modifier.fillMaxSize(), state = state)
    // optional widgets; each uses only the public state, so any of them can be replaced by your own
    GhosttyScrollIndicator(state, Modifier.align(Alignment.CenterEnd))
    GhosttyJumpToBottomButton(state, Modifier.align(Alignment.BottomEnd).padding(16.dp))
}
GhosttyExtraKeysBar(state)          // keys a software keyboard lacks, with sticky Ctrl and Alt
GhosttyDialogs(state)
```

## Platforms

| Platform | Architectures | Notes |
| --- | --- | --- |
| Android | `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` | API 21+ for `libghostty` and `libghostty-extras`; API 23+ for `libghostty-compose` and the Android demo |
| Desktop JVM | x86_64 and aarch64 on Linux, macOS, and Windows | Java 17+. Linux needs glibc 2.17 or newer. macOS 11 or newer |
| Browser (WebAssembly) | `wasmJs` | `libghostty` and `libghostty-compose`. WebGL 2 and WasmGC: Chrome 119+, Firefox 120+, or Safari 18.2+ |

`libghostty-extras` (themes, terminfo, fonts) is Android and desktop JVM only.

GitHub Releases attach runnable demos: an Android APK, an offline web ZIP, and desktop uber jars for the three CI hosts (`linux-x64`, `macos-arm64`, `windows-x64`). The published JNI library includes natives for every desktop OS and architecture in the table.

## Desktop demo

Download a demo uber jar from [GitHub Releases](https://github.com/xchacha20-poly1305/libghostty-cmp/releases).
Choose the jar matching your operating system and CPU architecture (for example, `linux-x64`,
`macos-arm64`, or `windows-x64` in the filename). Each release includes builds for the three CI runner platforms.

Install Java 17 or newer with desktop support, then run the downloaded jar from a terminal:

```sh
java -jar demo-desktop-linux-x64-<version>.jar
```

Replace the filename with the jar you downloaded. The jar includes the application dependencies and
native libraries; Java must be installed separately. It opens a terminal window running your `$SHELL`,
falling back to `/bin/sh` on Linux/macOS or `cmd.exe` on Windows.

To run from source:

```sh
./gradlew :demo-desktop:run
```

Every push to `cmp` publishes the `-SNAPSHOT` version to
`https://central.sonatype.com/repository/maven-snapshots/`.

## Browser / WebAssembly

`libghostty` and `libghostty-compose` publish `wasmJs` artifacts. The extras module's
filesystem, terminfo installation and JVM resource APIs remain Android/JVM-only.

[Open the web demo](https://xchacha20-poly1305.github.io/libghostty-cmp/), or download the
[offline web demo](https://xchacha20-poly1305.github.io/libghostty-cmp/demo-web-offline.zip).
Unzip the entire directory and open `index.html` directly. No local server, Python or Java
is needed. The terminal runs without a network connection; CJK and emoji fonts download
from Google Fonts and jsDelivr when the browser is online. The chrome is Compose Multiplatform. Language defaults
to the browser's locale (Chinese or English) and falls back to English; appearance defaults
to the system color scheme and can be set to light or dark. The terminal uses Ghostty's
bundled **Adwaita** / **Adwaita Dark** palettes. The example button displays text, Unicode, emoji,
ANSI styles, indexed/true colors, control characters and an OSC 8 hyperlink together.

The shell is simulated and keeps its filesystem/history in memory. `help`, `clear`, `echo`,
`pwd`, `ls`, `cd`, `cat`, `history`, and `showcase` are available. Tab completes commands and
paths; arrow keys edit the current command and browse history. Refresh resets the environment.
`echo` preserves backslashes by default. `echo -e` interprets `\e`, `\xHH`, `\n`, `\r`, `\t`,
`\b`, and `\\`; `-n` suppresses its final newline. For example:

```sh
echo -e "\e[31mRed text\e[0m"
echo -e "12345\rABC"
cat 你好.txt
```

The browser backend shares the Zig session and packed snapshot format with JNI. Rendering uses
the existing Compose terminal component and Skia glyph shaping. Call the asynchronous loader
before constructing any terminal session:

```kotlin
GhosttyWebAssembly.load("ghostty-vt.wasm")
val session = GhosttyTerminalSession()
// Supply your own Transport (for example, a WebSocket transport or an in-memory shell).
```

Alternatively, `GhosttyWebAssembly.initialize(bytes)` accepts cached/embedded binary bytes.
The `libghostty-wasm-js` Maven publication includes the matching binary with classifier
`ghostty-vt` and extension `wasm`. Bundle that binary with the library version you use; host
resource loading is application-controlled. In this repository, the `ghosttyWasmBinary` Gradle
configuration exposes it to the demo without depending on task names or build-directory paths.

Browser sessions run on the browser event loop. Keyboard encoding, text/IME input, ANSI colors,
scrollback, selection, clipboard actions, resizing and OSC 8 links are supported. Kitty graphics,
system notifications, local file access, Node and WASI are not supported by this browser target.
`GhosttyBrowserClipboard` denies program reads and asks before OSC 52 writes; its constructor
allows applications to supply a write-approval policy. User copy actions and ordinary browser
paste events are separate from program clipboard access. Clipboard APIs may require permission;
browser paste events work without an asynchronous clipboard read. `GhosttyClipboard.readText(callback)`
also lets hosts complete explicit paste requests asynchronously; synchronous hosts keep their existing behavior.

The demo requires WebGL 2 and WasmGC (Chrome 119+, Firefox 120+, or Safari 18.2+). Automated browser checks cover
Chromium, Firefox and WebKit; real mobile keyboards and Safari on Apple hardware need manual
validation. The demo bundles a Latin subset of Noto Sans Mono CJK SC (OFL). CJK and emoji faces
are fetched at runtime from Google Fonts and jsDelivr; chrome text also uses Compose Multiplatform's
on-demand Noto fallback. Without a network the terminal keeps the bundled Latin face. Other scripts
may still need extra fonts; a library consumer controls its font family. The offline package serves
its own Wasm and Latin face locally and allows font CDN requests.

Build from source (Node.js 26 and Python 3 are build tools only):

```sh
./gradlew :demo-web:wasmJsBrowserDevelopmentRun       # local development server
./gradlew :demo-web:offlineZip :demo-web:shellTest   # production package and shell tests
cd demo-web/browser-tests
npm ci
npx playwright install --with-deps chromium firefox webkit
npm test                                            # opens file://; CJK/emoji fonts may be fetched
```

The ZIP is written to `demo-web/build/distributions/`. GitHub Pages is deployed from `cmp`
from the production offline build. Browser checks run locally; Actions only build and deploy the demo. Future `v*` releases automatically attach a versioned
web ZIP alongside the desktop demos. No new version or release is needed to update Pages.

## License

MIT. The bundled Latin face is distributed under the SIL Open Font License; see `fonts/OFL.txt`.
