# libghostty-cmp

Kotlin Multiplatform bindings for [libghostty-vt](https://github.com/ghostty-org/ghostty/tree/main/src/lib) on
Android and the desktop JVM (Linux, macOS and Windows on x86_64 and aarch64): terminal sessions, a Compose
Multiplatform terminal, a theme catalog, and font handling.

```kotlin
dependencies {
    implementation("io.github.xchacha20-poly1305:libghostty:0.2.1")

    // optional
    implementation("io.github.xchacha20-poly1305:libghostty-extras:0.2.1")
    implementation("io.github.xchacha20-poly1305:libghostty-compose:0.2.1")
}
```

```kotlin
val session = GhosttyTerminalSession()
session.transport = myPty            // sends input and window size to the program
// on the thread that reads the program's output:
session.feedOutput(bytes, 0, count)

// in Compose
val state = rememberGhosttyTerminalState()
GhosttyTerminal(session, Modifier.fillMaxSize(), state = state)
GhosttyDialogs(state)
```

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

## License

MIT
