pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kotlin-dotnet-engine"

include(":compiler-plugin")
include(":dotnetutils")
