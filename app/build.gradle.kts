plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.stardewoffline"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.stardewoffline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform("androidx.compose:compose-bom:${libs.versions.composeBom.get()}"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:${libs.versions.composeBom.get()}"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation("androidx.compose.ui:ui-tooling")
    baselineProfile(project(":baselineprofile"))
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

tasks.register("verifyRealV4Package") {
    group = "verification"
    description = "用 STARDEW_SVDATA 指定的工作区外 schema 4 包执行设备验收。"
    dependsOn("assembleDebug", "assembleDebugAndroidTest")

    doLast {
        val source = providers.environmentVariable("STARDEW_SVDATA").orNull
            ?.let(::file)
            ?.takeIf { it.isFile }
            ?: throw GradleException("verifyRealV4Package 需要指向真实 .svdata 文件的 STARDEW_SVDATA 环境变量")
        val adb = File(android.sdkDirectory, "platform-tools/adb.exe").absolutePath
        val remote = "/data/local/tmp/stardew-real-v4.svdata"
        val debugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val testApk = layout.buildDirectory.file("outputs/apk/androidTest/debug/app-debug-androidTest.apk").get().asFile

        fun runAdb(vararg arguments: String) {
            val exitCode = ProcessBuilder(arguments.toList()).inheritIO().start().waitFor()
            check(exitCode == 0) { "ADB 验收命令失败：${arguments.joinToString(" ")}" }
        }
        runAdb(adb, "install", "-r", debugApk.absolutePath)
        runAdb(adb, "install", "-r", testApk.absolutePath)
        runAdb(adb, "push", source.absolutePath, remote)
        runAdb(
            adb, "shell", "am", "instrument", "-w", "-r",
            "-e", "class", "com.example.stardewoffline.core.datapackage.RealV4PackageValidationTest",
            "-e", "realV4Required", "true",
            "-e", "realV4PackagePath", remote,
            "com.example.stardewoffline.debug.test/androidx.test.runner.AndroidJUnitRunner",
        )
    }
}
