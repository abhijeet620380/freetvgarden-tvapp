import java.io.File   // needed: in this script "java" alone means Gradle's java setting, so File is imported by name

// TV page app: one single module (no sub-folder), plain Java + WebView, no extra libraries.
plugins {
    id("com.android.application") version "8.7.2"
}

// The GitHub build passes its build number here so every build is newer than the last.
val ftgVersionCode = (project.findProperty("ftgVersionCode") as String?)?.toIntOrNull() ?: 1
val ftgVersionName = (project.findProperty("ftgVersionName") as String?) ?: "1.0"

android {
    namespace = "com.freetvgarden.tvweb"
    compileSdk = 35
    defaultConfig {
        // A DIFFERENT id from your other apps, so they can all be installed side by side.
        applicationId = "com.freetvgarden.tvweb"
        minSdk = 24
        targetSdk = 35
        versionCode = ftgVersionCode
        versionName = ftgVersionName
    }
    // Optional permanent signing key (GitHub Secrets KEYSTORE_BASE64 / KEYSTORE_PASSWORD, same as your other app).
    signingConfigs {
        create("ftg") {
            val ks = System.getenv("FTG_KEYSTORE")
            if (ks != null && File(ks).exists()) {
                storeFile = File(ks)
                storeType = "pkcs12"
                storePassword = System.getenv("FTG_KEYSTORE_PASSWORD")
                keyAlias = "ftg"
                keyPassword = System.getenv("FTG_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            val ftg = signingConfigs.getByName("ftg")
            signingConfig = if (ftg.storeFile != null) ftg else signingConfigs.getByName("debug")
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
