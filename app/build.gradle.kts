import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The root launcher icon (mipmap-anydpi-v26/ic_launcher*.xml, its foreground
// drawable, and ic_launcher_background) is proprietary branding, not code - it
// deliberately lives outside app/src/main/res so it's never bundled into this
// MIT-licensed repo (the branding assets carry their own, different license).
// This resolves to a real branding res directory when one is supplied (via
// -PbrandingResDir=... or $GRIMOIRE_BRANDING_RES_DIR, the latter wired through
// by build.sh for Docker builds), and otherwise falls back to the generic
// placeholder icon checked into branding/oss/res so the repo still builds
// standalone for anyone without the real assets.
val brandingResDir: String = run {
    val requested = (project.findProperty("brandingResDir") as String?)
        ?: System.getenv("GRIMOIRE_BRANDING_RES_DIR")
    requested?.let { File(it) }?.takeIf { it.isDirectory }?.absolutePath
        ?: rootProject.file("branding/oss/res").absolutePath
}

// The release signing key - like the launcher icon, this is sensitive material
// (far more so: losing control of it is worse than losing the repo) that lives
// outside this repo and is only pulled in when supplied, via
// -PkeystoreDir=... or $GRIMOIRE_KEYSTORE_DIR (the latter wired through by
// build.sh for Docker builds). Points at a directory containing keystore.properties
// (storeFile, storePassword, keyAlias, keyPassword) plus the keystore file itself.
// With none supplied, release builds are simply left unsigned - correct for the
// open-source repo, which has no signing key of its own to fall back to.
val keystoreDir: File? = run {
    val requested = (project.findProperty("keystoreDir") as String?)
        ?: System.getenv("GRIMOIRE_KEYSTORE_DIR")
    requested?.let { File(it) }?.takeIf { it.isDirectory }
        ?: rootProject.file("signing/demilich").takeIf { it.isDirectory }
}
val keystoreProps: Properties? = keystoreDir
    ?.resolve("keystore.properties")
    ?.takeIf { it.isFile }
    ?.let { propsFile ->
        val props = Properties()
        propsFile.inputStream().use { stream -> props.load(stream) }
        props
    }

android {
    namespace = "digital.demilich.grimoire"
    compileSdk = 36

    defaultConfig {
        applicationId = "digital.demilich.grimoire"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets {
        getByName("main") {
            res.srcDir(brandingResDir)
        }
    }

    if (keystoreProps != null) {
        signingConfigs {
            create("release") {
                storeFile = keystoreDir!!.resolve(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.webkit)
    implementation(libs.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)
}
