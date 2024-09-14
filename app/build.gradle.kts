plugins {
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.mannodermaus.android.junit5)
}

android {
    project.tasks.preBuild.get().dependsOn("generateFbsKotlin")
    project.tasks.preBuild.get().mustRunAfter("generateFbsKotlin")
    compileSdk = 34
//    buildToolsVersion = "32.1.0-rc1"
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    defaultConfig {
        applicationId = "com.kuromelabs.kurome"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    sourceSets.getByName("main") {
        java.srcDir("build/generated/source/flatbuffers")
    }
    namespace = "com.kuromelabs.kurome"

    tasks.register<Exec>("generateFbsKotlin") {
        doFirst {
            delete("$projectDir/build/generated/source/flatbuffers/")
            mkdir("$projectDir/build/generated/source/flatbuffers/")
        }
        doLast {

        }
        group = "Generate FBS Kotlin"
        description = "Generate FBS Kotlin"
        val files = arrayListOf<File>()
        file("$projectDir/src/main/java/com/kuromelabs/kurome/application/flatbuffers").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".fbs") }
            .forEach { files.add(it) }
        val args = arrayListOf(
            "flatc",
            "-o",
            "$projectDir\\build\\generated\\source\\flatbuffers\\",
            "--kotlin"
        )
        files.forEach {
            args.add(it.path)
        }
        commandLine(args)
    }

    packaging {
        resources {
            pickFirsts += "**/MANIFEST.MF"
        }
    }

    testOptions.unitTests.all { it.jvmArgs("-Xms512m", "-Xmx4g") }
}

dependencies {

    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    //test
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.jupiter.engine)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)

    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.material.mdc)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.timber)
    implementation(libs.flatbuffers.java)
    implementation(libs.kotlinx.coroutines.android)



    // Compose
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.test.manifest)

    //leakcanary
    debugImplementation(libs.leakcanary.android)

    //SSL
    implementation(libs.bcpkix.jdk18on)

    implementation(libs.flatbuffers.java)
}