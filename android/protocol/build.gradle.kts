plugins {
    `java-library`
}

// Plain JVM library: Android consumers require API 31; there is no Android manifest/minSdk DSL.
val minSdk = 31
extra["minSdk"] = minSdk
tasks.jar { manifest.attributes["Tonewire-Min-Android-Api"] = minSdk.toString() }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}
