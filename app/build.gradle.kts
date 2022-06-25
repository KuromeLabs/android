plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

android {
    project.tasks.preBuild.get().dependsOn("generateFbsKotlin")
    project.tasks.preBuild.get().mustRunAfter("generateFbsKotlin")
    compileSdk = 32
    buildToolsVersion = "32.1.0-rc1"
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    defaultConfig {
        applicationId = "com.kuromelabs.kurome"
        minSdk = 21
        targetSdk = 32
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.2.0-rc02"
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
    
    sourceSets.getByName("main") {
        java.srcDir("build/generated/source/fsb")
    }

    tasks.register<Exec>("generateFbsKotlin") {
        doFirst {
            delete("$projectDir/build/generated/source/fsb/")
            mkdir("$projectDir/build/generated/source/fsb/")
            print("Directory created")
        }
        doLast {

        }
        group = "Generate FBS Kotlin"
        description = "Generate FBS Kotlin"
        val files =
            file("$projectDir/src/main/java/com/kuromelabs/kurome/application/fbs").listFiles()
        val args = arrayListOf(
            "flatc",
            "-o",
            "$projectDir\\build\\generated\\source\\fsb\\",
            "--kotlin"
        )
        files.forEach {
            args.add(it.path)
        }
        commandLine(args)

    }
}

dependencies {

    implementation("androidx.lifecycle:lifecycle-service:2.4.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")

    kapt("androidx.room:room-compiler:2.4.2")
    implementation("com.google.accompanist:accompanist-permissions:0.24.10-beta")

    implementation("com.google.dagger:hilt-android:2.42")
    kapt("com.google.dagger:hilt-android-compiler:2.42")
    kapt("androidx.hilt:hilt-compiler:1.0.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.room:room-runtime:2.4.2")
    implementation("androidx.room:room-ktx:2.4.2")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.flatbuffers:flatbuffers-java:2.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.2")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.6.1")
    testImplementation("androidx.test:core:1.4.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("androidx.test:core:1.4.0")
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")

    // Compose
    implementation("androidx.compose.runtime:runtime:${rootProject.extra["compose_version"]}")
    implementation("androidx.compose.ui:ui:${rootProject.extra["compose_version"]}")
    implementation("androidx.compose.foundation:foundation:${rootProject.extra["compose_version"]}")
    implementation("androidx.compose.foundation:foundation-layout:${rootProject.extra["compose_version"]}")
    implementation("androidx.navigation:navigation-compose:2.4.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")
    implementation("androidx.compose.material:material:${rootProject.extra["compose_version"]}")
    implementation("androidx.compose.ui:ui-tooling:${rootProject.extra["compose_version"]}")
    implementation("androidx.activity:activity-compose:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview:${rootProject.extra["compose_version"]}")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${rootProject.extra["compose_version"]}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${rootProject.extra["compose_version"]}")

    //leakcanary
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.9.1")

    //SSL
    implementation("org.bouncycastle:bcpkix-jdk18on:1.71")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")

}

kapt {
    correctErrorTypes = true
}