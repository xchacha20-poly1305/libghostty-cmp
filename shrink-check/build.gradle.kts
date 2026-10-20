plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Shrinks a consumer that uses as little of the library as possible with R8, as an app that minifies would,
// then runs it. R8 reads the keep rules from the library jar itself, so a member the JNI library looks up by
// name and the rules miss fails here instead of in somebody's release build.
val r8 = configurations.create("r8")

dependencies {
    implementation(project(":library"))
    r8(libs.r8)
}

val shrunkJar = layout.buildDirectory.file("r8/shrunk.jar")

val shrink = tasks.register<JavaExec>("shrink") {
    description = "Shrinks the consumer and the library with R8."
    classpath = r8
    mainClass = "com.android.tools.r8.R8"
    // Locals, so the argument provider captures no script object for the configuration cache.
    val programJars = files(tasks.jar, configurations.runtimeClasspath)
    val output = shrunkJar
    val jdkHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
        .map { it.metadata.installationPath.asFile.path }
    val entryPoint = file("entry-point.pro")
    inputs.files(programJars).withNormalizer(ClasspathNormalizer::class)
    inputs.file(entryPoint)
    outputs.file(output)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--classfile",
            "--release",
            "--lib", jdkHome.get(),
            "--pg-conf", entryPoint.path,
            "--output", output.get().asFile.path,
        ) + programJars.files.map { it.path }
    })
}

val shrinkCheck = tasks.register<JavaExec>("shrinkCheck") {
    description = "Runs the consumer R8 shrank, which loads the JNI library."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = files(shrunkJar).builtBy(shrink)
    mainClass = "io.github.xchacha20_poly1305.libghostty.shrinkcheck.MainKt"
}

tasks.check {
    dependsOn(shrinkCheck)
}
