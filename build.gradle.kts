buildscript {
    val kotlinVersion = "1.7.20"
    extra.apply {
        set("compose_version", "1.2.1")
    }
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.0-alpha05")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.42")
    }
}


tasks.register("clean", Delete::class) {
    delete(project.buildDir)
}