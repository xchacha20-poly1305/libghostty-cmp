# libghostty-android

Android bindings for [libghostty-vt](https://github.com/ghostty-org/ghostty/tree/main/src/lib),
with a terminal view, session management, theme catalog, and font handling.

```kotlin
dependencies {
    implementation("io.github.xchacha20-poly1305:libghostty-android:0.1.0-alpha01")

    // optional
    implementation("io.github.xchacha20-poly1305:libghostty-android-extras:0.1.0-alpha01")
    implementation("io.github.xchacha20-poly1305:libghostty-android-compose:0.1.0-alpha01")
}
```

Every push to main publishes `0.1.0-SNAPSHOT` to
`https://central.sonatype.com/repository/maven-snapshots/`.

## License

MIT
