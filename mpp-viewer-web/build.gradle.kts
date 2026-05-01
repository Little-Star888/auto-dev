import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URL

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.library")
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
    maven("https://jogamp.org/deployment/maven")
}

group = "cc.unitmesh.viewer.web"
version = project.findProperty("mppVersion") as String? ?: "0.1.5"

// Force consistent Kotlin stdlib version across all dependencies
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
        force("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AutoDevViewerWeb"
            isStatic = true
        }
    }

    js(IR) {
        browser()
        useCommonJs()
        binaries.executable()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":mpp-viewer"))
                implementation(project(":mpp-core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")

                // JSON serialization
                implementation(libs.kotlinx.serialization.json)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                // compose-webview-multiplatform - JVM/Desktop only
                implementation(libs.compose.webview)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter:5.10.0")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
            }
        }

        val androidMain by getting {
            dependencies {
                // compose-webview-multiplatform - Android support
                implementation(libs.compose.webview)
            }
        }

        val iosMain by creating {
            dependencies {
                // compose-webview-multiplatform - iOS support
                implementation(libs.compose.webview)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                // WASM-specific dependencies if needed
                // Note: compose-webview not available for WASM
            }
        }

        val jsMain by getting {
            dependencies {
                // JS-specific dependencies if needed
                // Note: compose-webview not available for JS
            }
        }
    }
}

// Desktop configuration for KCEF (Chromium Embedded Framework)
compose.desktop {
    application {
        mainClass = "cc.unitmesh.viewer.web.WebViewDebugApp_jvmKt"
    }
}

// Task to run E2E Test Agent Demo
tasks.register<JavaExec>("runE2ETestDemo") {
    group = "application"
    description = "Run the E2E Test Agent Demo application"
    mainClass.set("cc.unitmesh.viewer.web.e2etest.E2ETestAgentDemoKt")
    classpath = sourceSets["jvmMain"].runtimeClasspath

    jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

    if (System.getProperty("os.name").contains("Mac")) {
        jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
    }
}

// Add JVM flags for KCEF
afterEvaluate {
    tasks.withType<JavaExec> {
        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

        if (System.getProperty("os.name").contains("Mac")) {
            jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }
    }
}

// Task to download Mermaid.js library
abstract class DownloadMermaidTask : DefaultTask() {
    @get:Input
    val mermaidVersion = "11.4.0"
    
    @get:OutputFile
    abstract val outputFile: RegularFileProperty
    
    @TaskAction
    fun download() {
        val output = outputFile.get().asFile
        
        if (output.exists()) {
            logger.lifecycle("Mermaid.js already exists: ${output.absolutePath}")
            return
        }
        
        output.parentFile.mkdirs()
        
        logger.lifecycle("Downloading mermaid.min.js version $mermaidVersion...")
        val jsUrl = "https://cdn.jsdelivr.net/npm/mermaid@$mermaidVersion/dist/mermaid.min.js"
        
        try {
            URL(jsUrl).openStream().use { input ->
                output.outputStream().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
            logger.lifecycle("Downloaded: ${output.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to download Mermaid.js: ${e.message}")
            throw e
        }
    }
}

val downloadMermaid = tasks.register<DownloadMermaidTask>("downloadMermaid") {
    group = "build"
    description = "Download Mermaid.js library"
    outputFile.set(file("src/commonMain/resources/mermaid/mermaid.min.js"))
}

// Make processResources tasks depend on downloadMermaid
tasks.matching { it.name.contains("processResources", ignoreCase = true) }.configureEach {
    dependsOn(downloadMermaid)
}

// Compose resources copy tasks (especially iOS test resource tasks) also read from src/commonMain/resources.
// Ensure Mermaid download runs first to satisfy Gradle task dependency validation.
tasks.matching {
    it.name.startsWith("copyComposeResourcesFor") || it.name.startsWith("copyTestComposeResourcesFor")
}.configureEach {
    dependsOn(downloadMermaid)
}

android {
    namespace = "cc.unitmesh.viewer.web"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
