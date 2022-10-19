plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

android {
    project.tasks.preBuild.get().dependsOn("generateFbsKotlin")
    project.tasks.preBuild.get().mustRunAfter("generateFbsKotlin")
    compileSdk = 33
//    buildToolsVersion = "32.1.0-rc1"
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    defaultConfig {
        applicationId = "com.kuromelabs.kurome"
        minSdk = 21
        targetSdk = 33
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
        kotlinCompilerExtensionVersion = "1.3.2"
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
    
    sourceSets.getByName("main") {
        java.srcDir("build/generated/source/fsb")
    }
    namespace = "com.kuromelabs.kurome"

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

    implementation("androidx.lifecycle:lifecycle-service:2.5.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")

    kapt("androidx.room:room-compiler:2.4.3")
    implementation("com.google.accompanist:accompanist-permissions:0.26.5-rc")

    implementation("com.google.dagger:hilt-android:2.44")
    kapt("com.google.dagger:hilt-android-compiler:2.44")
    kapt("androidx.hilt:hilt-compiler:1.0.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.room:room-runtime:2.4.3")
    implementation("androidx.room:room-ktx:2.4.3")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.flatbuffers:flatbuffers-java:2.0.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.8.1")
    testImplementation("androidx.test:core:1.4.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("androidx.test:core:1.4.0")
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")

    // Compose
    implementation("androidx.compose.runtime:runtime:1.2.1")
    implementation("androidx.compose.ui:ui:1.2.1")
    implementation("androidx.compose.foundation:foundation:1.2.1")
    implementation("androidx.compose.foundation:foundation-layout:1.2.1")
    implementation("androidx.navigation:navigation-compose:2.5.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")
    implementation("androidx.compose.material:material:1.2.1")
    implementation("androidx.compose.ui:ui-tooling:1.2.1")
    implementation("androidx.activity:activity-compose:1.6.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.2.1")

    //leakcanary
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.9.1")

    //SSL
    implementation("org.bouncycastle:bcpkix-jdk18on:1.72")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.0")

}

kapt {
    correctErrorTypes = true
}