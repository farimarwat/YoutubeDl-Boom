// youtubedl-boom-lite: the same library (same sources, resources and API, compiled from
// ../library) with a small ffmpeg built by scripts/ffmpeg-lite/build.sh instead of the full
// Termux one. Only the native files differ, see prepareLiteJniLibs.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id ("com.vanniktech.maven.publish") version "0.33.0"
}

val libraryDir = rootProject.file("library")

// The library's python/aria2c bundles plus the lite libffmpeg.so; the full ffmpeg is left out.
val prepareLiteJniLibs = tasks.register<Sync>("prepareLiteJniLibs") {
    from(File(libraryDir, "src/main/jniLibs")) {
        exclude("**/libffmpeg.so", "**/libffmpeg.zip.so", "**/libffprobe.so")
    }
    from("src/main/ffmpegLibs")
    into(layout.buildDirectory.dir("liteJniLibs"))
}
tasks.named("preBuild") { dependsOn(prepareLiteJniLibs) }

android {
    ndkVersion = "29.0.13599879"
    namespace = "com.farimarwat.library"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles(File(libraryDir, "consumer-rules.pro"))
    }

    sourceSets["main"].apply {
        manifest.srcFile(File(libraryDir, "src/main/AndroidManifest.xml"))
        java.setSrcDirs(listOf(File(libraryDir, "src/main/java")))
        res.setSrcDirs(listOf(File(libraryDir, "src/main/res")))
        jniLibs.setSrcDirs(listOf(layout.buildDirectory.dir("liteJniLibs").get().asFile))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        jniLibs {
            // The python/aria2c bundles are zip archives named .so: nothing to strip.
            keepDebugSymbols += "**/*.zip.so"
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = "io.github.farimarwat",
        artifactId = "youtubedl-boom-lite",
        version = "1.0.24"
    )
    pom {
        name.set("youtubedl-boom-lite")
        description.set("youtubedl-boom with a small ffmpeg (stream copy, merge, audio extraction; no external video encoders)")
        inceptionYear.set("2025")
        url.set("https://github.com/farimarwat/YoutubeDl-Boom")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("farimarwat")
                name.set("Farman Ullah Khan Marwat")
                email.set("farimarwat@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/farimarwat/YoutubeDl-Boom")
        }
    }
    publishToMavenCentral()
    signAllPublications()
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.timber)
    implementation(project(":commons"))
}
