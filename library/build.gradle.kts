plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id ("com.vanniktech.maven.publish") version "0.33.0"
}

android {
    ndkVersion = "29.0.13599879"
    namespace = "com.farimarwat.library"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                arguments.addAll(
                    listOf(
                        "-DANDROID_LD_USE_16KB_PAGES=ON",
                        "-DANDROID_ALLOW_UNDEFINED_SYMBOLS=ON"
                    )
                )
            }
        }

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
mavenPublishing{
    coordinates(
        groupId = "io.github.farimarwat",
        artifactId = "youtubedl-boom",
        version = "1.0.23"
    )
    pom {
        name.set("youtubedl-boom")
        description.set("An android library based on youtubedl-android, developed by JunkFood, to download videos from social websites")
        inceptionYear.set("2025")
        url.set("https://github.com/farimarwat/YoutubeDl-Boom")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        // Specify developers information
        developers {
            developer {
                id.set("farimarwat")
                name.set("Farman Ullah Khan Marwat")
                email.set("farimarwat@gmail.com")
            }
        }

        // Specify SCM information
        scm {
            url.set("https://github.com/farimarwat/YoutubeDl-Boom")
        }
    }

    publishToMavenCentral()

    // Enable GPG signing for all publications
    signAllPublications()
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.commons.io)
    implementation(libs.commons.compress)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.timber)
    implementation(project(":commons"))
}