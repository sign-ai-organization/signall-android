pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Las pruebas de composables necesitan un JDK 21+ (ver `app/build.gradle.kts`:
// `phosphoricons-regular-android:1.0.5` trae bytecode de Java 21). Este
// resolvedor le permite a Gradle **descargarlo solo** la primera vez, en vez de
// exigir que esté instalado a mano en cada máquina y en el CI.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SignAll"
include(":app")
