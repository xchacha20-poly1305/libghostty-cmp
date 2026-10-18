package io.github.xchacha20_poly1305.libghostty

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Loads the JNI library for this OS and architecture from the jar, extracted into the user cache directory under
 * a name derived from its content so that different library versions never overwrite each other. The system
 * property `libghostty.library.path` points at a library to load instead.
 */
internal actual fun loadGhosttyJni() {
    System.getProperty(LIBRARY_PATH_PROPERTY)?.let {
        System.load(File(it).absolutePath)
        return
    }
    val platform = "${osName()}-${archName()}"
    val name = System.mapLibraryName("ghostty_jni")
    val resource = "/io/github/xchacha20_poly1305/libghostty/native/$platform/$name"
    val bytes = GhosttyVt::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
        ?: throw UnsatisfiedLinkError("libghostty has no native library for $platform ($resource)")
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        .take(8).joinToString("") { "%02x".format(it) }
    val dir = File(cacheDir(), "libghostty/$digest").apply { mkdirs() }
    val library = File(dir, name)
    if (!library.isFile || library.length() != bytes.size.toLong()) {
        val partial = Files.createTempFile(dir.toPath(), name, ".partial")
        Files.write(partial, bytes)
        Files.move(partial, library.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    System.load(library.absolutePath)
}

internal actual fun logError(tag: String, message: String, error: Throwable?) {
    System.err.println("$tag: $message")
    error?.printStackTrace()
}

private const val LIBRARY_PATH_PROPERTY = "libghostty.library.path"

private fun osName(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.startsWith("linux") -> "linux"
        os.startsWith("mac") -> "macos"
        os.startsWith("windows") -> "windows"
        else -> throw UnsatisfiedLinkError("libghostty does not support $os")
    }
}

private fun archName(): String = when (val arch = System.getProperty("os.arch").lowercase()) {
    "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> throw UnsatisfiedLinkError("libghostty does not support $arch")
}

private fun cacheDir(): File {
    val home = System.getProperty("user.home")
    return when (osName()) {
        "macos" -> File(home, "Library/Caches")
        "windows" -> System.getenv("LOCALAPPDATA")?.let(::File) ?: File(home, "AppData/Local")
        else -> System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotEmpty() }?.let(::File) ?: File(home, ".cache")
    }
}
