plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.kalasyan.recompa"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.kalasyan.recompa"
        minSdk = 26
        targetSdk = 35
        // CI numbers each build so Android and Obtainium see every build as an update.
        // Tagged releases (see release.yml) pass their version in RECOMPA_VERSION.
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val releaseVersion = System.getenv("RECOMPA_VERSION")
        versionCode = buildNumber ?: 1
        versionName = releaseVersion ?: if (buildNumber != null) "0.1.0-nightly.$buildNumber" else "0.1.0"

        buildConfigField(
            "String",
            "CATALOG_URL",
            "\"${providers.gradleProperty("recompa.catalogUrl").getOrElse("https://kalasyan.github.io/recompa/index.json")}\"",
        )
    }

    signingConfigs {
        getByName("debug") {
            // A fixed, public debug key: every CI build installs over the previous one.
            // Release builds will need their own private key.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The private release key only exists in CI, decoded from repository secrets.
        System.getenv("RECOMPA_KEYSTORE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("RECOMPA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RECOMPA_KEY_ALIAS")
                keyPassword = System.getenv("RECOMPA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.compose.ui.tooling)
}
