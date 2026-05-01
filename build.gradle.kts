plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ktor) apply false
    id("com.android.library") version "8.10.0" apply false
    id("com.android.application") version "8.10.0" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
                useVersion(libs.versions.kotlin.get())
            }
        }
    }
}

// Convenience task to publish mpp-core and mpp-ui to mavenLocal for mpp-idea composite build
// Only publishes JVM and multiplatform metadata publications (skips WASM/JS/iOS)
tasks.register("publishDepsForIdea") {
    group = "publishing"
    description = "Publish mpp-core, mpp-ui, and xiuper-ui JVM artifacts to mavenLocal for mpp-idea"

    dependsOn(
        ":mpp-core:publishJvmPublicationToMavenLocal",
        ":mpp-core:publishKotlinMultiplatformPublicationToMavenLocal",
        ":mpp-ui:publishJvmPublicationToMavenLocal",
        ":mpp-ui:publishKotlinMultiplatformPublicationToMavenLocal",
        ":xiuper-ui:publishMavenPublicationToMavenLocal"
    )

    doLast {
        println("✅ Published mpp-core, mpp-ui, and xiuper-ui JVM artifacts to mavenLocal")
        println("Now you can build mpp-idea with: ./gradlew :mpp-idea:build")
    }
}
