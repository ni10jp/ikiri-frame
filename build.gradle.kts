plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}

// Keep generated classes out of synced source folders in both Android Studio and CLI builds.
// A checkout-specific directory prevents different copies of this project sharing outputs.
val workspaceId = Integer.toUnsignedString(rootDir.absolutePath.hashCode(), 16)
val localBuildRoot = providers.gradleProperty("ikiriBuildRoot").orNull
    ?.let { rootProject.file(it) }
    ?: gradle.gradleUserHomeDir.resolve("ikiri-frame-builds/$workspaceId")

allprojects {
    layout.buildDirectory.set(localBuildRoot.resolve(project.name))
}
