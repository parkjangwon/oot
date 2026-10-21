plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val hasReleaseSigning =
    listOf(
        System.getenv("OOT_KEYSTORE"),
        System.getenv("OOT_STORE_PASSWORD"),
        System.getenv("OOT_KEY_ALIAS"),
        System.getenv("OOT_KEY_PASSWORD"),
    ).all { !it.isNullOrBlank() }

val appVersionName = version.toString()

val appVersionCode =
    Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(appVersionName)?.destructured?.let { (major, minor, patch) ->
        val minorNumber = minor.toInt()
        val patchNumber = patch.toInt()
        if (minorNumber > 99 || patchNumber > 99) {
            throw GradleException("version $appVersionName: MINOR and PATCH must be 0..99")
        }
        major.toInt() * 10000 + minorNumber * 100 + patchNumber
    } ?: throw GradleException("version must be MAJOR.MINOR.PATCH in gradle.properties, got '$appVersionName'")

tasks.register("printVersion") {
    val name = appVersionName
    val code = appVersionCode
    doLast {
        println("versionName=$name")
        println("versionCode=$code")
    }
}

android {
    namespace = "org.parkjw.apps.oot"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.parkjw.apps.oot"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(System.getenv("OOT_KEYSTORE")!!)
                storePassword = System.getenv("OOT_STORE_PASSWORD")
                keyAlias = System.getenv("OOT_KEY_ALIAS")
                keyPassword = System.getenv("OOT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                )
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

gradle.taskGraph.whenReady {
    val needsReleaseSigning =
        allTasks.any { task ->
            Regex("^(assemble|bundle|package|sign).*Release").containsMatchIn(task.name)
        }
    if (needsReleaseSigning && !hasReleaseSigning) {
        throw GradleException(
            "Release signing requires OOT_KEYSTORE, OOT_STORE_PASSWORD, OOT_KEY_ALIAS, and OOT_KEY_PASSWORD",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
