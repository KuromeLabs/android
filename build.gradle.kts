plugins {
    id("com.android.application") version "8.2.1" apply false
    id("com.android.library") version "8.2.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("com.google.devtools.ksp") version "1.9.21-1.0.15" apply false

}

tasks.register("clean", Delete::class) {
    delete(project.layout.buildDirectory)
}
