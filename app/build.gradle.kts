plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.smartbridge.tunnel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartbridge.tunnel"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
        buildFeatures { viewBinding = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE.md", "META-INF/NOTICE.md",
                "META-INF/versions/**",
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    // JSch for SSH tunneling (pure Java, no native libs needed)
    implementation("com.github.mwiede:jsch:0.2.23")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
