import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.play.publisher)
}

// Upload key for Play (see keystore/README.txt). Not in SVN; release builds are unsigned if it's missing.
val uploadKeyProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dk.Android.Cpmonster.mgmanager"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dk.rhea.cabincomfort"
        minSdk = 30
        targetSdk = 37
        versionCode = 5
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadKeyProps.isNotEmpty()) {
            create("upload") {
                storeFile = rootProject.file("keystore/" + uploadKeyProps.getProperty("storeFile"))
                storePassword = uploadKeyProps.getProperty("storePassword")
                keyAlias = uploadKeyProps.getProperty("keyAlias")
                keyPassword = uploadKeyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (uploadKeyProps.isNotEmpty()) signingConfig = signingConfigs.getByName("upload")
            // R8 shrinking/optimisation. Keep this on: Play Vitals flags releases without it.
            optimization {
                enable = true
            }
        }
        // Same as release (no test server, not debuggable) but signed with the debug key,
        // so it can be installed from Android Studio. Use the "Run as release" run configuration.
        create("releaseTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // DevOnly.ENABLED uses BuildConfig.DEBUG
    }
    // releaseTest is signed with the debug key – never upload it to Play.
    playConfigs {
        register("releaseTest") {
            enabled.set(false)
        }
    }
}

// Gradle Play Publisher (bootstrapListing, publishListing, publishBundle, ...).
// Service account key is kept outside the project. Override with PLAY_CREDENTIALS
// or playCredentials=... in local.properties.
val playCredentials: String = System.getenv("PLAY_CREDENTIALS")
    ?: Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }.getProperty("playCredentials")
    ?: "${System.getProperty("user.home")}/.keys/gen-lang-client-0418284192-29ae594645cb.json"

play {
    serviceAccountCredentials.set(file(playCredentials))
    defaultToAppBundles.set(true)
    // publishBundle -> internal testing; promoteReleaseArtifact -> copies that release to closed testing (alpha).
    track.set("internal")
    fromTrack.set("internal")
    promoteTrack.set("alpha")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
}
// Installs the releaseTest build on the connected device/emulator and starts it.
// Used by the "Run as release" run configuration.
tasks.register("runReleaseTest") {
    group = "install"
    description = "Install and start the app as released (test server hidden) on every connected device."
    dependsOn("installReleaseTest")
    val adbPath = androidComponents.sdkComponents.adb.get().asFile.absolutePath
    doLast {
        val component = "dk.rhea.cabincomfort/dk.Android.Cpmonster.mgmanager.MainActivity"
        val devices = ProcessBuilder(adbPath, "devices").start().inputStream.bufferedReader().readLines()
            .drop(1).map { it.trim() }.filter { it.endsWith("device") }.map { it.split(Regex("\\s+"))[0] }
        devices.forEach { serial ->
            ProcessBuilder(adbPath, "-s", serial, "shell", "am", "start", "-n", component)
                .inheritIO().start().waitFor()
        }
    }
}
