plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}
val PUBLISH_GROUP_ID by extra("io.github.farimarwat")
val PUBLISH_VERSION by extra("1.0.9")
val PUBLISH_ARTIFACT_ID by extra("youtubedl-boom")
val PUBLISH_DESCRIPTION by extra("An android library based on youtubedl-android, developed by JunkFood, to download videos from social websites")
val PUBLISH_URL by extra("https://github.com/farimarwat/YoutubeDl-Boom")
val PUBLISH_LICENSE_NAME by extra("Apache 2.0 License")
val PUBLISH_LICENSE_URL by extra("https://www.apache.org/licenses/LICENSE-2.0")
val PUBLISH_DEVELOPER_ID by extra("farimarwat")
val PUBLISH_DEVELOPER_NAME by extra("Farman Ullah Marwat")
val PUBLISH_DEVELOPER_EMAIL by extra("farimarwat@gmail.com")
val PUBLISH_SCM_CONNECTION by extra("scm:git:github.com/farimarwat/YoutubeDl-Boom.git")
val PUBLISH_SCM_DEVELOPER_CONNECTION by extra("scm:git:ssh://github.com/farimarwat/YoutubeDl-Boom.git")
val PUBLISH_SCM_URL by extra("https://github.com/farimarwat/YoutubeDl-Boom/tree/main")

apply(from = "${rootProject.projectDir}/scripts/publish-module.gradle")
android {
    namespace = "com.farimarwat.library"
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
}