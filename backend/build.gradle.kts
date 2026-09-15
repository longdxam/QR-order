plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.owasp.dependencycheck)
    alias(libs.plugins.cyclonedx)
}

group = "com.qros"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")
val openApiSpecUri = rootProject.file("docs/api/openapi.yaml").toURI().toString()

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(openApiSpecUri)
    outputDir.set(generatedOpenApiDir.get().asFile.absolutePath)
    apiPackage.set("com.qros.generated.api")
    modelPackage.set("com.qros.generated.model")
    invokerPackage.set("com.qros.generated")
    cleanupOutput.set(true)
    configOptions.set(
        mapOf(
            "annotationLibrary" to "none",
            "documentationProvider" to "none",
            "interfaceOnly" to "true",
            "openApiNullable" to "false",
            "skipDefaultInterface" to "true",
            "useBeanValidation" to "true",
            "useSpringBoot3" to "true",
            "useTags" to "true",
        )
    )
    globalProperties.set(
        mapOf(
            "apis" to "",
            "apiDocs" to "false",
            "apiTests" to "false",
            "models" to "",
            "modelDocs" to "false",
            "modelTests" to "false",
            "supportingFiles" to "false",
        )
    )
}

openApiValidate {
    inputSpec.set(openApiSpecUri)
}

sourceSets {
    main {
        java.srcDir(generatedOpenApiDir.map { it.dir("src/main/java") })
    }
}

val verifyGeneratedOpenApi by tasks.registering {
    group = "verification"
    description = "Từ chối build nếu OpenAPI Generator không sinh interface hoặc model Java."
    dependsOn(tasks.openApiGenerate)

    doLast {
        val generatedSources = generatedOpenApiDir.get().dir("src/main/java").asFile
        val apiFiles = fileTree(generatedSources) { include("**/api/*Api.java") }.files
        val modelFiles = fileTree(generatedSources) { include("**/model/*.java") }.files

        check(apiFiles.isNotEmpty()) { "OpenAPI Generator không sinh interface API nào." }
        check(modelFiles.isNotEmpty()) { "OpenAPI Generator không sinh model nào." }
    }
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    annotationProcessor(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.spring.boot.dependencies))
    testRuntimeOnly(platform(libs.spring.boot.dependencies))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.opentelemetry)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.websocket)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.bouncycastle.provider)
    implementation(libs.logstash.logback.encoder)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.test {
    useJUnitPlatform()
    // QrosApplication.main() chuẩn hoá timezone hạ tầng thành UTC trước khi Spring khởi động,
    // nhưng test không đi qua main(). Thiếu dòng này thì JVM giữ alias hệ điều hành
    // "Asia/Saigon" và PostgreSQL từ chối kết nối — đúng cái bẫy đã gặp ở BL-M0-02.
    systemProperty("user.timezone", "UTC")
}

tasks.compileJava {
    dependsOn(verifyGeneratedOpenApi)
}

// NFR-SEC-25, TM-OPS-01, BL-M0-12: CVSS ≥ 7 chặn pipeline. NVD_API_KEY để trống vẫn quét được,
// nhưng tốc độ tải dữ liệu NVD chậm hơn nhiều — đặt secret NVD_API_KEY trong CI để tránh vượt
// timeout. Không đặt giá trị mặc định cho secret: thiếu thì chạy chậm chứ không lộ gì.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    nvd.apiKey = System.getenv("NVD_API_KEY")
}

