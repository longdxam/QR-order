rootProject.name = "qros"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("backend/gradle/libs.versions.toml"))
        }
    }
}

include("backend")
