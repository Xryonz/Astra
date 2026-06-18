pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Deps transitivas do LiveKit Android (ex: audioswitch/webrtc) vivem aqui.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AstraMobile"
include(":app")
