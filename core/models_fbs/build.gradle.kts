import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "com.kuromelabs.models_fbs"
    compileSdk = 36

    project.tasks.preBuild.get().dependsOn(":core:models_fbs:generateFbsKotlin")
    project.tasks.preBuild.get().mustRunAfter(":core:models_fbs:generateFbsKotlin")

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    val generateFbsKotlin by tasks.registering(Exec::class) {
        doFirst {
            delete("$projectDir/build/generated/source/flatbuffers/")
            mkdir("$projectDir/build/generated/source/flatbuffers/")
        }
        doLast {

        }
        group = "Generate FBS Kotlin"
        description = "Generate FBS Kotlin"
        val files = arrayListOf<File>()
        file("$projectDir/src/main/java/com/kuromelabs/models_fbs/packet.fbs").walkTopDown()
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

    tasks.withType<KotlinCompile>().configureEach {
        dependsOn(generateFbsKotlin)
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets.getByName("main") {
        java.srcDir("build/generated/source/flatbuffers")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.flatbuffers.java)
}