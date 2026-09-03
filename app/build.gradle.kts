plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.palan.hisaab"
    compileSdk = 34

    // A fixed, checked-in signing key (see /keystore/hisaab-debug.jks) used for every build —
    // debug included. Without this, each CI run signs the debug APK with a brand-new random
    // key from the runner's throwaway ~/.android/debug.keystore, so every new build has a
    // different signature than what's already on the phone. Android refuses to install an
    // update whose signature doesn't match the installed app's ("package conflicts with an
    // existing package"), forcing an uninstall every time. Pinning the keystore here fixes it.
    signingConfigs {
        create("hisaab") {
            storeFile = file("../keystore/hisaab-debug.jks")
            storePassword = "hisaab123"
            keyAlias = "hisaab"
            keyPassword = "hisaab123"
        }
    }

    // CI passes the GitHub Actions run number in as HISAAB_VERSION_CODE so every build gets a
    // higher versionCode than the last (Android also refuses an "update" with a versionCode
    // that isn't strictly greater than what's installed). Local builds without that env var
    // fall back to 1, which is fine for local debugging/sideloading only.
    val ciVersionCode = System.getenv("HISAAB_VERSION_CODE")?.toIntOrNull()

    defaultConfig {
        applicationId = "com.palan.hisaab"
        minSdk = 26
        targetSdk = 34
        versionCode = ciVersionCode ?: 1
        versionName = "1.0.${ciVersionCode ?: 0}"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("hisaab")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hisaab")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
