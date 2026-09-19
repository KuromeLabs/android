import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.library)
}

/**
 * Runs `flatc` over the module's schemas to produce the Kotlin bindings that the rest of
 * the project compiles against.
 */
@CacheableTask
abstract class GenerateFlatBuffersTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemas: ConfigurableFileCollection

    /** Name of, or path to, the flatc executable. Override with -Pflatc=/path/to/flatc. */
    @get:Input
    abstract val compiler: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        fileSystemOperations.delete { delete(outputDir) }
        outputDir.mkdirs()

        execOperations.exec {
            commandLine(
                buildList {
                    add(compiler.get())
                    add("--kotlin")
                    add("-o")
                    add(outputDir.absolutePath)
                    schemas.files.forEach { add(it.absolutePath) }
                }
            )
        }

        stripVersionGuard(outputDir)
    }

    /**
     * flatc emits `validateVersion()`, which asserts that the runtime library was released
     * alongside the schema compiler. Google publishes flatc more often than it publishes
     * flatbuffers-java, so the assertion names a runtime version that does not exist on Maven
     * Central and the generated sources fail to compile. Nothing in the generated code calls
     * it, and the rest of the output only uses long-stable APIs, so it is dropped here.
     *
     * Remove this once flatbuffers-java catches up with the flatc release in use.
     */
    private fun stripVersionGuard(outputDir: File) {
        val guard = Regex("""^\s*fun validateVersion\(\) = Constants\.FLATBUFFERS_[0-9_]+\(\)\s*$\n""", RegexOption.MULTILINE)
        val import = Regex("""^import com\.google\.flatbuffers\.Constants\s*$\n""", RegexOption.MULTILINE)

        outputDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val original = file.readText()
            var patched = guard.replace(original, "")
            if (!patched.contains("Constants.")) {
                patched = import.replace(patched, "")
            }
            if (patched != original) {
                file.writeText(patched)
            }
        }
    }
}

val generateFbsKotlin = tasks.register<GenerateFlatBuffersTask>("generateFbsKotlin") {
    group = "build"
    description = "Generates Kotlin sources from the FlatBuffers schemas"
    schemas.from(fileTree("src/main/java/com/kuromelabs/models_fbs") { include("**/*.fbs") })
    compiler.set(providers.gradleProperty("flatc").orElse("flatc"))
    outputDirectory.set(layout.buildDirectory.dir("generated/source/flatbuffers"))
}

android {
    namespace = "com.kuromelabs.models_fbs"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            generateFbsKotlin,
            GenerateFlatBuffersTask::outputDirectory
        )
    }
}

dependencies {
    // Generated bindings expose FlatBuffers types in their public API.
    api(libs.flatbuffers.java)
}
