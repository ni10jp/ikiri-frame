import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

abstract class WriteLegacyApkLocator : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val apkListing: RegularFileProperty

    @get:OutputFile
    abstract val locator: RegularFileProperty

    @TaskAction
    fun writeLocator() {
        val target = locator.get().asFile
        val relativeListing = apkListing.get().asFile.relativeTo(target.parentFile).invariantSeparatorsPath
        target.parentFile.mkdirs()
        target.writeText("#- File Locator -\nlistingFile=$relativeListing\n")
    }
}

// An open Studio project can retain the original locator until its next successful sync.
// Follow AGP's APK artifact provider: IDE deployment and CLI builds use different directories.
androidComponents {
    onVariants(selector().all()) { variant ->
        if (layout.buildDirectory.get().asFile != layout.projectDirectory.dir("build").asFile) {
            val variantTaskName = variant.name.replaceFirstChar { it.uppercaseChar() }
            val writeLocator = tasks.register<WriteLegacyApkLocator>("write${variantTaskName}LegacyApkLocator") {
                apkListing.set(variant.artifacts.get(SingleArtifact.APK).map { it.file("output-metadata.json") })
                locator.set(layout.projectDirectory.file(
                    "build/intermediates/apk_ide_redirect_file/${variant.name}/create${variantTaskName}ApkListingFileRedirect/redirect.txt"
                ))
            }
            tasks.matching { it.name == "assemble$variantTaskName" }.configureEach {
                dependsOn(writeLocator)
            }
        }
    }
}

android {
    namespace = "jp.ni10.ikiriframe"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "jp.ni10.ikiriframe"
        minSdk = 24
        targetSdk = 37
        versionCode = 67
        versionName = "1.4.60"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("io.coil-kt.coil3:coil-core:3.4.0")
    constraints {
        implementation("com.google.errorprone:error_prone_annotations:2.30.0") {
            because("Keep Material Components and the Android test runtime aligned")
        }
    }
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    compileOnly("com.google.errorprone:error_prone_annotations:2.30.0")
    implementation("com.valentinilk.shimmer:compose-shimmer:1.5.0")
    implementation("me.saket.telephoto:zoomable:0.19.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.caverock:androidsvg-aar:1.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.12.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.12.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.1")
}
