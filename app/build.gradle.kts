import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

val hasReleaseSigning =
    listOf(
        System.getenv("OOT_KEYSTORE"),
        System.getenv("OOT_STORE_PASSWORD"),
        System.getenv("OOT_KEY_ALIAS"),
        System.getenv("OOT_KEY_PASSWORD"),
    ).all { !it.isNullOrBlank() }

// Antigravity's Google OAuth client (installed-app: id + secret on every token call). Never committed.
// Each value comes from, in order: the env var, a gradle property, or the properties file that OOT_OAUTH_PROPS
// (env var or gradle property) points to, kept outside the repo. No fallback: the id and the secret must come from
// the same OAuth client, so a missing value leaves the build without Antigravity login (the app says so).
val oauthProps = Properties().apply {
    val path = System.getenv("OOT_OAUTH_PROPS")?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("OOT_OAUTH_PROPS").orNull?.takeIf { it.isNotBlank() }
    if (path != null) {
        val propsFile = file(path)
        if (!propsFile.isFile) throw GradleException("OOT_OAUTH_PROPS does not point to a readable file")
        propsFile.inputStream().use { load(it) }
    }
}

fun oauthValue(key: String): String =
    (System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
        ?: oauthProps.getProperty(key))
        .orEmpty()
        .trim()

fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val antigravityClientId = oauthValue("OOT_ANTIGRAVITY_CLIENT_ID")
val antigravityClientSecret = oauthValue("OOT_ANTIGRAVITY_CLIENT_SECRET")

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
        buildConfigField("String", "ANTIGRAVITY_CLIENT_ID", buildConfigString(antigravityClientId))
        buildConfigField("String", "ANTIGRAVITY_CLIENT_SECRET", buildConfigString(antigravityClientSecret))

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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
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

roborazzi {
    outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.espresso.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
