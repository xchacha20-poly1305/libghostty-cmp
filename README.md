# libghostty-cmp

Kotlin Multiplatform bindings for [libghostty-vt](https://github.com/ghostty-org/ghostty/tree/main/src/lib) on
Android and the desktop JVM (Linux, macOS and Windows on x86_64 and aarch64): terminal sessions, a Compose
Multiplatform terminal, a theme catalog, and font handling.

```kotlin
dependencies {
    implementation("io.github.xchacha20-poly1305:libghostty:0.2.0-SNAPSHOT")

    // optional
    implementation("io.github.xchacha20-poly1305:libghostty-extras:0.2.0-SNAPSHOT")
    implementation("io.github.xchacha20-poly1305:libghostty-compose:0.2.0-SNAPSHOT")
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

`demo-desktop` runs your shell through pty4j: `./gradlew :demo-desktop:run`.

Every push to main publishes the `-SNAPSHOT` version to
`https://central.sonatype.com/repository/maven-snapshots/`.

## License

MIT
