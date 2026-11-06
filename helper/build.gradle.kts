plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id ("com.vanniktech.maven.publish") version "0.33.0"
}

android {
    namespace = "com.farimarwat.helper"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures{
        buildConfig = true
    }
}

dependencies {
    // Types from both appear in RYoutubeDL's public API (YoutubeDLRequest, VideoInfo, Job).
    api(project(":commons"))
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.farimarwat",
        artifactId = "youtubedl-boom-helper",
        version = "1.7"
    )
    pom {
        name.set("youtubedl-boom-helper")
        description.set("A helper library for youtubedl-boom")
        url.set("https://github.com/farimarwat/YoutubeDl-Boom")
        licenses {
            license {
                name.set("Apache 2.0 License")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("farimarwat")
                name.set("Farman Ullah Marwat")
                email.set("farimarwat@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:github.com/farimarwat/YoutubeDl-Boom.git")
            developerConnection.set("scm:git:ssh://github.com/farimarwat/YoutubeDl-Boom.git")
            url.set("https://github.com/farimarwat/YoutubeDl-Boom/tree/main")
        }
    }
    publishToMavenCentral()
    signAllPublications()
}
