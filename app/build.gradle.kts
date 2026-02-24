plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val embeddedServerAssetFile = layout.projectDirectory.file("src/main/assets/server.dex")
val embeddedServerDexPathProp = providers.gradleProperty("embeddedServerDexPath")
val embeddedServerDexPathEnv = providers.environmentVariable("EMBEDDED_SERVER_DEX")

tasks.register("syncEmbeddedServerDex") {
    group = "embedded"
    description = "Copy external server.dex into app/src/main/assets/server.dex"
    doLast {
        val sourcePath = embeddedServerDexPathProp.orNull ?: embeddedServerDexPathEnv.orNull
        if (sourcePath.isNullOrBlank()) {
            throw GradleException(
                "Missing server dex source path. Provide -PembeddedServerDexPath=/abs/path/server.dex " +
                    "or set EMBEDDED_SERVER_DEX."
            )
        }

        val sourceFile = file(sourcePath)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            throw GradleException("server dex not found: ${sourceFile.absolutePath}")
        }
        if (!sourceFile.name.endsWith(".dex", ignoreCase = true)) {
            throw GradleException("server dex must be a .dex file: ${sourceFile.absolutePath}")
        }

        val destFile = embeddedServerAssetFile.asFile
        destFile.parentFile.mkdirs()
        sourceFile.copyTo(destFile, overwrite = true)
        println("Embedded server dex synced: ${destFile.absolutePath}")
    }
}

tasks.register("verifyEmbeddedServerDex") {
    group = "embedded"
    description = "Check app/src/main/assets/server.dex exists and is non-empty"
    doLast {
        val file = embeddedServerAssetFile.asFile
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            throw GradleException(
                "Missing ${file.absolutePath}. Run :app:syncEmbeddedServerDex first."
            )
        }
        println("Embedded server dex verified: ${file.absolutePath} (${file.length()} bytes)")
    }
}

tasks.register("prepareEmbeddedServerDex") {
    group = "embedded"
    description = "Sync then verify embedded server dex"
    dependsOn("syncEmbeddedServerDex", "verifyEmbeddedServerDex")
}

android {
    namespace = "com.safe.discipline"
    ndkVersion = "29.0.13846066"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.safe.discipline"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        prefab = true
        viewBinding = true
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.boringssl)
    implementation(libs.bouncycastle)
    implementation(libs.hidden.compat)
    implementation(libs.hiddenapibypass)
    compileOnly(libs.hidden.stub)
    compileOnly(libs.refine.annotation)
    
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
