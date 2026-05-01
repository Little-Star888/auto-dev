@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.io.File

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
    id("app.cash.sqldelight") version "2.1.0"
    id("de.comahe.i18n4k") version "0.11.1"
    alias(libs.plugins.ktlint)
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
    maven("https://jogamp.org/deployment/maven")
}

sqldelight {
    databases {
        create("DevInsDatabase") {
            packageName.set("cc.unitmesh.devins.db")
//            generateAsync = true
        }
    }
}

i18n4k {
    sourceCodeLocales = listOf("en", "zh")
}

group = "cc.unitmesh"
version = project.findProperty("mppVersion") as String? ?: "0.1.5"

// Workaround for Kotlin/JS IR internal compiler error:
// java.lang.IllegalArgumentException: List has more than one element (JsIntrinsics.getInternalFunction)
// Typically caused by multiple Kotlin stdlib klibs on the compilation classpath.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-js:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
        force("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    }
    // Exclude kotlin-logging-android-debug to prevent duplicate classes with kotlin-logging-android
    exclude(group = "io.github.oshai", module = "kotlin-logging-android-debug")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    // iOS targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AutoDevUI"
            isStatic = true

            // Set bundle ID
            binaryOption("bundleId", "com.phodal.autodev")

            // Export dependencies to make them available in Swift
            export(project(":mpp-core"))
            export(compose.runtime)
            export(compose.foundation)
            export(compose.material3)
            export(compose.ui)
        }
        iosTarget.compilations.getByName("main").compileTaskProvider.configure {
            compilerOptions.freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    js(IR) {
        // Node.js CLI only - no browser compilation
        // Web UI uses pure TypeScript/React + mpp-core (similar to CLI architecture)
//        nodejs {
            // Configure Node.js target for CLI
//        }
        browser()
        useCommonJs()
        binaries.executable()
        compilerOptions {
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
            // Allow deprecated CanvasBasedWindow API until migration to ComposeViewport is complete
            suppressWarnings = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "mpp-ui.js"
            }
        }
        binaries.executable()
        compilerOptions {
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
            // Allow deprecated CanvasBasedWindow API until migration to ComposeViewport is complete
            suppressWarnings = true
        }
//        d8 {
//            // Use d8 instead of binaryen (wasm-opt) for now
//        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":mpp-core"))
                implementation(project(":mpp-codegraph"))
                implementation(project(":mpp-viewer"))
                implementation(project(":mpp-viewer-web"))
                implementation(project(":xiuper-ui"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
                implementation(compose.ui)
                implementation(compose.components.resources)
                // Animation (needed for animateContentSize in LiveTerminalItem)
                implementation(compose.animation)

                // Rich text editor for Compose
                implementation(libs.richeditor)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)

                // DateTime for KMP
                implementation(libs.kotlinx.datetime)

                implementation(libs.kaml)

                // JSON serialization
                implementation(libs.kotlinx.serialization.json)

                // FileKit - Cross-platform file picker
                implementation(libs.filekit.core)
                implementation(libs.filekit.dialogs)

//                implementation("javax.naming:jndi:1.2.1")

                // Ktor HTTP Client (for remote agent)
                implementation(libs.ktor.client.core)

                // i18n4k - Internationalization
                implementation(libs.i18n4k.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(project(":mpp-viewer"))
                implementation(project(":mpp-viewer-web"))
                implementation(project(":xiuper-ui"))
                implementation(project(":xiuper-e2e"))
                implementation(compose.desktop.currentOs)

                // compose.webview for desktop WebView support
                implementation(libs.compose.webview)

                // ComposeCharts - Cross-platform chart library (JVM)
                implementation(libs.compose.charts)

                // Lets-Plot Compose (Desktop only - macOS, Windows, Linux)
                // https://github.com/JetBrains/lets-plot-compose
                implementation(libs.letsplot.kotlin)
                implementation(libs.letsplot.common)
                implementation(libs.letsplot.canvas)
                implementation(libs.letsplot.raster)
                implementation(libs.letsplot.imageExport)
                implementation(libs.letsplot.compose)

                // WebView support (KCEF) - needed for MermaidRenderer initialization
                implementation(libs.compose.webview)

                // Rich text editor for Compose Desktop
                implementation(libs.richeditor)

                // Bonsai Tree View (JVM only)
                implementation(libs.bonsai.core)
                implementation(libs.bonsai.fileSystem)

                // SQLDelight - JVM SQLite driver
                implementation(libs.sqldelight.sqlite)

                // CodeHighlight
                implementation(libs.highlights)
                // Multiplatform Markdown Renderer for JVM
                implementation(libs.markdown.renderer.code)
                implementation(libs.markdown.renderer.jvm)
                implementation(libs.markdown.renderer.m3)

                // plantuml
                implementation(libs.plantuml)

                // Logback for JVM logging backend with file storage
                implementation(libs.logback.get().toString()) {
                    exclude(group = "javax.naming", module = "javax.naming-api")
                }

                // RSyntaxTextArea for syntax highlighting in JVM
                implementation(libs.rsyntaxtextarea)

                implementation(libs.pty4j)
                implementation(libs.jediterm.core)
                implementation(libs.jediterm.ui)

                // Coroutines Swing for Dispatchers.Main on JVM Desktop
                implementation(libs.kotlinx.coroutines.swing)

                // Ktor HTTP Client CIO engine for JVM
                implementation(libs.ktor.client.cio)

                // i18n4k - JVM
                implementation(libs.i18n4k.core.jvm)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(project(":mpp-viewer-web"))
                implementation(project(":xiuper-ui")) {
                    exclude(group = "io.github.oshai", module = "kotlin-logging-jvm")
                    exclude(group = "io.github.oshai", module = "kotlin-logging-android-debug")
                }

                // Kotlin Logging for Android (use Android version instead of JVM version)
                implementation("io.github.oshai:kotlin-logging-android:${libs.versions.kotlinLogging.get()}") {
                    exclude(group = "io.github.oshai", module = "kotlin-logging-android-debug")
                }

                implementation(libs.androidx.activity)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core)

                // SLF4J Android backend (compatible with Android, replaces logback)
                implementation("com.github.tony19:logback-android:3.0.0")

                // ComposeCharts - Cross-platform chart library (Android)
                implementation(libs.compose.charts)

                // Lets-Plot Compose (Android)
                // https://github.com/JetBrains/lets-plot-compose
                implementation(libs.letsplot.kotlin)
                implementation(libs.letsplot.common)
                implementation(libs.letsplot.canvas)
                implementation(libs.letsplot.raster)
                implementation(libs.letsplot.compose)

                // Bonsai Tree View (Android)
                implementation(libs.bonsai.core)
                implementation(libs.bonsai.fileSystem)

                // SQLDelight - Android SQLite driver
                implementation(libs.sqldelight.android)

                // Multiplatform Markdown Renderer for Android
                implementation(libs.markdown.renderer)
                implementation(libs.markdown.renderer.m3)

                // Coroutines Android for Dispatchers.Main on Android
                implementation(libs.kotlinx.coroutines.android)

                // Ktor HTTP Client CIO engine for Android
                implementation(libs.ktor.client.cio)

                // i18n4k - Android
                implementation(libs.i18n4k.core.android)
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        iosMain {
            dependencies {
                // API dependencies (exported in framework)
                api(project(":mpp-core"))
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.ui)

                // Implementation dependencies
                implementation(project(":mpp-viewer-web"))
                implementation(project(":xiuper-ui"))

                // ComposeCharts - Cross-platform chart library (iOS)
                implementation(libs.compose.charts)

                // SQLDelight - iOS SQLite driver
                implementation(libs.sqldelight.native)

                // Ktor HTTP Client Darwin engine for iOS
                implementation(libs.ktor.client.darwin)

                // Multiplatform Markdown Renderer for iOS
                implementation(libs.markdown.renderer)
                implementation(libs.markdown.renderer.m3)
            }
        }

        val jsMain by getting {
            dependencies {
                // Node.js CLI dependencies
                implementation(compose.html.core)
                implementation(project(":xiuper-ui"))

                // Note: mpp-viewer-web is not included for JS as WebView is not supported in Node.js CLI
                // WebEdit features are only available on JVM/Desktop platforms

                // SQLDelight - JS driver
                implementation(libs.sqldelight.webWorker)

                // Ktor HTTP Client JS engine
                implementation(libs.ktor.client.js)

                // i18n4k - JS
                implementation(libs.i18n4k.core.js)
            }
        }

        // Needed by generated Karma/webpack test config (see build/js/**/karma.conf.js)
        // so that jsBrowserTest can load the polyfill config.
        val jsTest by getting {
            dependencies {
                implementation(devNpm("copy-webpack-plugin", "12.0.2"))
                // Required by webpack config injected via webpack.config.d/node-polyfills.js
                // (used by browser tests for codegraph + git + sql worker).
                implementation(npm("wasm-git", "0.0.13"))
                implementation(npm("sql.js", "1.8.0"))
            }
        }

        val wasmJsMain by getting {
            dependencies {
                // Force kotlin-stdlib to 2.2.0 to match compiler version
                implementation("org.jetbrains.kotlin:kotlin-stdlib") {
                    version {
                        strictly(libs.versions.kotlin.get())
                    }
                }

                implementation(project(":mpp-viewer-web"))
                implementation(project(":xiuper-ui"))

                implementation(devNpm("copy-webpack-plugin", "12.0.2"))

                implementation(npm("wasm-git", "0.0.13"))

                // ComposeCharts - Cross-platform chart library (WasmJS)
                implementation(libs.compose.charts)

                // SQLDelight - Web Worker driver (same as JS)
                implementation(npm("sql.js", "1.8.0"))
                implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.1.0"))
                implementation(libs.sqldelight.webWorker)
                implementation(libs.sqldelight.webWorker.wasmJs)

                // Ktor HTTP Client JS engine (works for WASM too)
                implementation(libs.ktor.client.js)

                // i18n4k - WASM
                implementation(libs.i18n4k.core.wasmJs)

                // Multiplatform Markdown Renderer for WASM
                implementation(libs.markdown.renderer)
                implementation(libs.markdown.renderer.m3)
                implementation(libs.markdown.renderer.code)
            }
        }
    }
}

android {
    namespace = "cc.unitmesh.devins.ui"
    compileSdk = 36

    defaultConfig {
        applicationId = "cc.unitmesh.devins.ui"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = version.toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/INDEX.LIST",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE",
                    "META-INF/LICENSE.txt",
                    "META-INF/license.txt",
                    "META-INF/NOTICE",
                    "META-INF/NOTICE.txt",
                    "META-INF/notice.txt",
                    "META-INF/*.kotlin_module",
                    "META-INF/io.netty.versions.properties"
                )
        }
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
}

// Exclude logback from Android - it uses Java 9 module APIs not available on Android
configurations.all {
    if (name.contains("android", ignoreCase = true)) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
}

compose.desktop {
    application {
        mainClass = "cc.unitmesh.devins.ui.MainKt"

        jvmArgs += listOf(
            "--add-modules", "java.naming,java.sql",
            // JCEF (Java Chromium Embedded Framework) requires access to internal AWT classes
            "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports", "java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED"
        )

        // macOS-specific JVM args for JCEF
        if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
            jvmArgs += listOf(
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
            )
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AutoDev Desktop"
            packageVersion = "1.0.5"
            description = "AutoDev Desktop Application with Xiuper Agents Support"
            copyright = "© 2024 AutoDev Team. All rights reserved."
            vendor = "AutoDev Team"

            modules("java.naming", "java.sql")

            // File associations for .unit artifact bundles
            // Note: .unit files are ZIP archives, so they can be opened with ZIP applications
            fileAssociation(
                mimeType = "application/zip",
                extension = "unit",
                description = "AutoDev Unit Bundle"
            )

            // Custom app icon
            macOS {
                bundleID = "cc.unitmesh.devins.desktop"
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))

                // macOS-specific: register UTI for .unit files (as ZIP archives)
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDocumentTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleTypeName</key>
                                <string>AutoDev Unit Bundle</string>
                                <key>CFBundleTypeRole</key>
                                <string>Editor</string>
                                <key>LSHandlerRank</key>
                                <string>Owner</string>
                                <key>LSItemContentTypes</key>
                                <array>
                                    <string>cc.unitmesh.devins.unit</string>
                                </array>
                            </dict>
                        </array>
                        <key>UTExportedTypeDeclarations</key>
                        <array>
                            <dict>
                                <key>UTTypeIdentifier</key>
                                <string>cc.unitmesh.devins.unit</string>
                                <key>UTTypeDescription</key>
                                <string>AutoDev Unit Bundle (ZIP Archive)</string>
                                <key>UTTypeConformsTo</key>
                                <array>
                                    <string>public.zip-archive</string>
                                    <string>public.archive</string>
                                    <string>public.data</string>
                                </array>
                                <key>UTTypeTagSpecification</key>
                                <dict>
                                    <key>public.filename-extension</key>
                                    <array>
                                        <string>unit</string>
                                    </array>
                                    <key>public.mime-type</key>
                                    <string>application/zip</string>
                                </dict>
                            </dict>
                        </array>
                    """
                }
            }
            windows {
                menuGroup = "AutoDev"
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
            }
            linux {
                packageName = "autodev-desktop"
                iconFile.set(project.file("src/jvmMain/resources/icon-512.png"))
            }
        }

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
            optimize = false
            obfuscate = false
        }
    }
}

tasks.register("printClasspath") {
    doLast {
        println(configurations["jvmRuntimeClasspath"].asPath)
    }
}

// Task to run Remote Agent CLI
tasks.register<JavaExec>("runRemoteAgentCli") {
    group = "application"
    description = "Run Remote Agent CLI (Kotlin equivalent of TypeScript server command)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.devins.ui.cli.RemoteAgentCli")

    // Allow passing arguments from command line
    // Usage: ./gradlew :mpp-ui:runRemoteAgentCli --args="--server http://localhost:8080 --project-id autocrud --task 'Write tests'"
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ")
    }

    // Enable standard input for interactive mode (if needed in future)
    standardInput = System.`in`
}

// Task to run Code Review Demo
tasks.register<JavaExec>("runCodeReviewDemo") {
    group = "application"
    description = "Run Code Review Demo (Side-by-Side UI with Git integration)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.devins.ui.compose.agent.codereview.demo.CodeReviewDemoKt")

    // Enable standard input
    standardInput = System.`in`
}

// Task to run WebEdit Automation Test
tasks.register<JavaExec>("runWebEditTest") {
    group = "application"
    description = "Run WebEdit Automation Test (Automated testing of WebEdit features)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.devins.ui.webedit.WebEditAutomationTestKt")

    // Enable standard input
    standardInput = System.`in`
}

// Task to run WebEdit Vision Preview (Screenshot + GLM-4.6V testing)
tasks.register<JavaExec>("runVisionPreview") {
    group = "application"
    description = "Run WebEdit Vision Preview (Test screenshot capture + vision LLM fallback)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.devins.ui.webedit.WebEditVisionPreviewKt")

    // Enable standard input
    standardInput = System.`in`
}

// Task to run Document CLI
tasks.register<JavaExec>("runDocumentCli") {
    group = "application"
    description = "Run Document CLI"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.DocumentCli")

    // Pass properties - use docProjectPath to avoid conflict with Gradle's projectPath
    if (project.hasProperty("docProjectPath")) {
        systemProperty("projectPath", project.property("docProjectPath") as String)
    }
    if (project.hasProperty("docQuery")) {
        systemProperty("query", project.property("docQuery") as String)
    }
    if (project.hasProperty("docPath")) {
        systemProperty("documentPath", project.property("docPath") as String)
    }
    // New parameters for feature tree mode
    if (project.hasProperty("docMode")) {
        systemProperty("mode", project.property("docMode") as String)
    }
    if (project.hasProperty("docLanguage")) {
        systemProperty("language", project.property("docLanguage") as String)
    }

    standardInput = System.`in`
}

// Task to run PlotDSL CLI
tasks.register<JavaExec>("runPlotDSLCli") {
    group = "application"
    description = "Run PlotDSL CLI for generating statistical charts from natural language"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.PlotDSLCli")

    // Pass properties
    if (project.hasProperty("plotDescription")) {
        systemProperty("description", project.property("plotDescription") as String)
    }
    if (project.hasProperty("plotChartType")) {
        systemProperty("chartType", project.property("plotChartType") as String)
    }
    if (project.hasProperty("plotTheme")) {
        systemProperty("theme", project.property("plotTheme") as String)
    }

    standardInput = System.`in`
}

// Task to run Coding CLI
tasks.register<JavaExec>("runCodingCli") {
    group = "application"
    description = "Run Coding Agent CLI for autonomous coding tasks"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.CodingCli")

    // Pass properties - use codingProjectPath to avoid conflict with Gradle's projectPath
    if (project.hasProperty("codingProjectPath")) {
        systemProperty("projectPath", project.property("codingProjectPath") as String)
    }
    if (project.hasProperty("codingTask")) {
        systemProperty("task", project.property("codingTask") as String)
    }
    if (project.hasProperty("codingMaxIterations")) {
        systemProperty("maxIterations", project.property("codingMaxIterations") as String)
    }

    standardInput = System.`in`
}

// Task to run Renderer Batch Test
tasks.register<JavaExec>("runBatchTest") {
    group = "application"
    description = "Test ComposeRenderer batching functionality"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.RendererBatchTest")

    standardInput = System.`in`
}

// Task to capture ACP responses
tasks.register<JavaExec>("runAcpCapture") {
    group = "application"
    description = "Capture ACP agent responses for test cases"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.AcpCaptureCli")

    if (project.hasProperty("acpPrompt")) {
        systemProperty("acpPrompt", project.property("acpPrompt") as String)
    }
    if (project.hasProperty("acpAgentKey")) {
        systemProperty("acpAgentKey", project.property("acpAgentKey") as String)
    }
    if (project.hasProperty("acpCwd")) {
        systemProperty("acpCwd", project.property("acpCwd") as String)
    }

    standardInput = System.`in`
}

// Task to replay captured ACP events
tasks.register<JavaExec>("runAcpReplay") {
    group = "application"
    description = "Replay captured ACP events to test renderers"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.AcpReplayCli")

    if (project.hasProperty("acpCapture")) {
        systemProperty("acpCapture", project.property("acpCapture") as String)
    }

    standardInput = System.`in`
}

// Task to run E2E Test CLI
tasks.register<JavaExec>("runE2ECli") {
    group = "application"
    description = "Run E2E Test Agent CLI for AI-driven test scenario generation"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.E2ECli")

    // Pass properties
    if (project.hasProperty("e2eUrl")) {
        systemProperty("e2eUrl", project.property("e2eUrl") as String)
    }
    if (project.hasProperty("e2eGoal")) {
        systemProperty("e2eGoal", project.property("e2eGoal") as String)
    }
    if (project.hasProperty("e2eOutput")) {
        systemProperty("e2eOutput", project.property("e2eOutput") as String)
    }

    standardInput = System.`in`
}

// Task to run Review CLI
tasks.register<JavaExec>("runReviewCli") {
    group = "application"
    description = "Run Code Review CLI (Fix Generation with CodingAgent)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.ReviewCli")

    // Pass properties
    if (project.hasProperty("reviewProjectPath")) {
        systemProperty("reviewProjectPath", project.property("reviewProjectPath") as String)
    }
    if (project.hasProperty("reviewAnalysis")) {
        systemProperty("reviewAnalysis", project.property("reviewAnalysis") as String)
    }
    if (project.hasProperty("reviewPatch")) {
        systemProperty("reviewPatch", project.property("reviewPatch") as String)
    }
    if (project.hasProperty("reviewCommitHash")) {
        systemProperty("reviewCommitHash", project.property("reviewCommitHash") as String)
    }
    if (project.hasProperty("reviewUserFeedback")) {
        systemProperty("reviewUserFeedback", project.property("reviewUserFeedback") as String)
    }
    if (project.hasProperty("reviewLanguage")) {
        systemProperty("reviewLanguage", project.property("reviewLanguage") as String)
    }

    standardInput = System.`in`
}

// Task to run Domain Dictionary Deep Research CLI
tasks.register<JavaExec>("runDomainDictCli") {
    group = "application"
    description = "Run Domain Dictionary Deep Research CLI (analyze agent output)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.DomainDictCli")

    // Pass properties
    if (project.hasProperty("domainProjectPath")) {
        systemProperty("domainProjectPath", project.property("domainProjectPath") as String)
    }
    if (project.hasProperty("domainQuery")) {
        systemProperty("domainQuery", project.property("domainQuery") as String)
    }
    if (project.hasProperty("domainFocusArea")) {
        systemProperty("domainFocusArea", project.property("domainFocusArea") as String)
    }

    standardInput = System.`in`
}

// Task to run ChatDB CLI (Text2SQL Agent)
tasks.register<JavaExec>("runChatDBCli") {
    group = "application"
    description = "Run ChatDB CLI (Text2SQL Agent with Schema Linking and Revise Agent)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.ChatDBCli")

    // Pass database connection properties
    if (project.hasProperty("dbHost")) {
        systemProperty("dbHost", project.property("dbHost") as String)
    }
    if (project.hasProperty("dbPort")) {
        systemProperty("dbPort", project.property("dbPort") as String)
    }
    if (project.hasProperty("dbName")) {
        systemProperty("dbName", project.property("dbName") as String)
    }
    if (project.hasProperty("dbUser")) {
        systemProperty("dbUser", project.property("dbUser") as String)
    }
    if (project.hasProperty("dbPassword")) {
        systemProperty("dbPassword", project.property("dbPassword") as String)
    }
    if (project.hasProperty("dbDialect")) {
        systemProperty("dbDialect", project.property("dbDialect") as String)
    }
    if (project.hasProperty("dbQuery")) {
        systemProperty("dbQuery", project.property("dbQuery") as String)
    }
    if (project.hasProperty("generateVisualization")) {
        systemProperty("generateVisualization", project.property("generateVisualization") as String)
    }
    if (project.hasProperty("maxRows")) {
        systemProperty("maxRows", project.property("maxRows") as String)
    }

    standardInput = System.`in`
}

// Task to run Vision CLI (Multimodal GLM-4.6V)
tasks.register<JavaExec>("runVisionCli") {
    group = "application"
    description = "Run Vision CLI (Multimodal image understanding with GLM-4.6V)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.VisionCli")

    // Image and prompt properties
    if (project.hasProperty("visionImage")) {
        systemProperty("visionImage", project.property("visionImage") as String)
    }
    if (project.hasProperty("visionPrompt")) {
        systemProperty("visionPrompt", project.property("visionPrompt") as String)
    }
    if (project.hasProperty("enableThinking")) {
        systemProperty("enableThinking", project.property("enableThinking") as String)
    }

    // Tencent COS properties
    if (project.hasProperty("cosSecretId")) {
        systemProperty("cosSecretId", project.property("cosSecretId") as String)
    }
    if (project.hasProperty("cosSecretKey")) {
        systemProperty("cosSecretKey", project.property("cosSecretKey") as String)
    }
    if (project.hasProperty("cosBucket")) {
        systemProperty("cosBucket", project.property("cosBucket") as String)
    }
    if (project.hasProperty("cosRegion")) {
        systemProperty("cosRegion", project.property("cosRegion") as String)
    }

    standardInput = System.`in`
}

// Task to test COS bucket region
tasks.register<JavaExec>("runCosTest") {
    group = "application"
    description = "Test which region a Tencent COS bucket is in"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.CosTestCli")

    // Pass bucket name as argument
    if (project.hasProperty("bucket")) {
        args(project.property("bucket") as String)
    }
}

// Ktlint configuration
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.0.1")
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(true)

    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

// Note: We enable wasm-opt optimizer for production webpack builds
// If this causes issues, use wasmJsBrowserDevelopmentExecutableDistribution instead
// tasks.named("compileProductionExecutableKotlinWasmJsOptimize") {
//     enabled = false
// }

// Ensure wasmJsBrowserDistribution runs webpack before copying files
tasks.named("wasmJsBrowserDistribution") {
    dependsOn("wasmJsBrowserProductionWebpack")
}

// Force webpack to run (remove onlyIf condition that skips it)
tasks.named("wasmJsBrowserProductionWebpack") {
    onlyIf { true }
}

// =============================================================================
// Auto-download fonts for WASM UTF-8 support (not committed to Git)
// =============================================================================

/**
 * Task to download Noto Sans font for comprehensive UTF-8 support in WASM
 *
 * Downloads a lightweight Noto Sans variant that supports:
 * - Latin, Cyrillic, Greek
 * - Basic punctuation and symbols
 * - Some emoji support
 *
 * For full CJK (Chinese, Japanese, Korean) support, consider using Noto Sans CJK
 * which is larger (~15-20MB per variant).
 */
abstract class DownloadWasmFontsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val fontDir: DirectoryProperty

    @get:Input
    abstract val useCJKFont: Property<Boolean>

    @TaskAction
    fun download() {
        val fontDirectory = fontDir.get().asFile
        fontDirectory.mkdirs()

        val notoSansFile = File(fontDirectory, "NotoSans-Regular.ttf")
        val notoSansCJKFile = File(fontDirectory, "NotoSansSC-Regular.ttf")

        // Skip if fonts already exist
        if (notoSansFile.exists() || notoSansCJKFile.exists()) {
            println("Fonts already downloaded, skipping...")
            return
        }

        if (useCJKFont.get()) {
            // Download Noto Sans CJK SC (Simplified Chinese) TTF for full UTF-8 support
            println("Downloading Noto Sans CJK SC Variable TTF (Simplified Chinese)...")
            println("This font supports: Latin, Chinese, Japanese, Korean, Emoji")
            println("Size: ~17MB (Variable TTF format)")

            // Use GitHub raw content for reliable TTF download
            val cjkUrl = "https://github.com/notofonts/noto-cjk/raw/main/Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf"

            try {
                ant.invokeMethod("get", mapOf(
                    "src" to cjkUrl,
                    "dest" to notoSansCJKFile,
                    "verbose" to true,
                    "usetimestamp" to true,
                    "retries" to 3
                ))

                println("✅ Downloaded: ${notoSansCJKFile.name} (${notoSansCJKFile.length() / 1024 / 1024}MB)")
            } catch (e: Exception) {
                println("❌ Failed to download CJK TTF font: ${e.message}")
                println("You can manually download from: $cjkUrl")
                throw e
            }
        } else {
            // Download Noto Sans (lighter weight, basic UTF-8)
            println("Downloading Noto Sans TTF (lightweight)...")
            println("This font supports: Latin, Cyrillic, Greek, basic symbols")
            println("Size: ~500KB")

            val notoSansUrl = "https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSans/NotoSans-Regular.ttf"

            try {
                ant.invokeMethod("get", mapOf(
                    "src" to notoSansUrl,
                    "dest" to notoSansFile,
                    "verbose" to true,
                    "usetimestamp" to true,
                    "retries" to 3
                ))

                println("✅ Downloaded: ${notoSansFile.name} (${notoSansFile.length() / 1024}KB)")
                println("ℹ️  For CJK support, run: ./gradlew downloadWasmFonts -PuseCJKFont=true")
            } catch (e: Exception) {
                println("❌ Failed to download Noto Sans font: ${e.message}")
                println("You can manually download from: $notoSansUrl")
                throw e
            }
        }
    }
}

tasks.register<DownloadWasmFontsTask>("downloadWasmFonts") {
    group = "build"
    description = "Download fonts for WASM UTF-8 support (not committed to Git)"

    // Fonts are only needed for WASM platform, so download to wasmJsMain
    fontDir.set(file("src/wasmJsMain/composeResources/font"))
    useCJKFont.set(project.findProperty("useCJKFont")?.toString()?.toBoolean() ?: true)
}

// Auto-download fonts before resource processing
tasks.matching {
    it.name.contains("copyNonXmlValueResources") ||
    it.name.contains("prepareComposeResources") ||
    it.name.contains("generateComposeResClass")
}.configureEach {
    dependsOn("downloadWasmFonts")
}

// Also download before WASM compilation
tasks.matching { it.name.contains("compileKotlinWasm") }.configureEach {
    mustRunAfter("downloadWasmFonts")
}

// Configure JVM args for all JavaExec tasks (required for KCEF/WebView on desktop)
// This ensures that when running with `./gradlew :mpp-ui:run`, JCEF can access AWT internals
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

// JVM CLI: Render/debug NanoDSL text interpolation for generated cases
tasks.register<JavaExec>("runNanoDslTextRenderCli") {
    group = "application"
    description = "Parse NanoDSL -> init state -> print raw vs rendered string props (writes render-report.txt by default)"
    mainClass.set("cc.unitmesh.devins.ui.nano.cli.NanoDslTextRenderCli")
    dependsOn("jvmMainClasses")
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles +
            files(kotlin.jvm().compilations.getByName("main").output.classesDirs)
}

// Task to run NanoDSL Demo preview
tasks.register<JavaExec>("runNanoDSLDemo") {
    group = "application"
    description = "Run NanoDSL Demo preview window"
    mainClass.set("cc.unitmesh.devins.ui.nano.NanoDSLDemoPreviewKt")
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles +
            files(kotlin.jvm().compilations.getByName("main").output.classesDirs)
}

// Task to run Artifact CLI (HTML/JS artifact generation testing)
tasks.register<JavaExec>("runArtifactCli") {
    group = "application"
    description = "Run Artifact Agent CLI for testing HTML/JS artifact generation"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.ArtifactCli")

    // Pass properties
    if (project.hasProperty("artifactPrompt")) {
        systemProperty("artifactPrompt", project.property("artifactPrompt") as String)
    }
    if (project.hasProperty("artifactScenario")) {
        systemProperty("artifactScenario", project.property("artifactScenario") as String)
    }
    if (project.hasProperty("artifactOutput")) {
        systemProperty("artifactOutput", project.property("artifactOutput") as String)
    }
    if (project.hasProperty("artifactLanguage")) {
        systemProperty("artifactLanguage", project.property("artifactLanguage") as String)
    }

    standardInput = System.`in`
}

// Task to debug ACP issues (wildcard, session, bash)
tasks.register<JavaExec>("runAcpDebug") {
    group = "autodev"
    description = "Debug ACP agent issues (wildcard, session, bash)"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.server.cli.AcpDebugCli")

    // Pass command line arguments
    // Usage: ./gradlew :mpp-ui:runAcpDebug --args="--agent=Gemini --test=wildcard"
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ")
    }
    
    standardInput = System.`in`
}
