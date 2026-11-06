import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: CI secrets / local env win; otherwise the repo's sideload keystore is used so
// every build is signed with the same key and installs over the previous version.
val signingProps = Properties().apply {
    val f = rootProject.file("signing/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(prop)

android {
    namespace = "capital.cannuran.pulse"
    compileSdk = 36

    defaultConfig {
        applicationId = "capital.cannuran.pulse"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 1
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "dev")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingValue("PULSE_KEYSTORE_PATH", "storeFile") ?: "signing/pulse-sideload.jks")
            storePassword = signingValue("PULSE_KEYSTORE_PASSWORD", "storePassword") ?: "cannuran-pulse"
            keyAlias = signingValue("PULSE_KEY_ALIAS", "keyAlias") ?: "pulse"
            keyPassword = signingValue("PULSE_KEY_PASSWORD", "keyPassword") ?: "cannuran-pulse"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
