plugins {
    id("com.android.application")
}

android {
    namespace = "com.spotibjork.app"
    compileSdk = 35

    defaultConfig {
        // Identifiant propre : cohabite avec l'app Spotify du Play Store (com.spotify.music).
        applicationId = "com.spotibjork.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
