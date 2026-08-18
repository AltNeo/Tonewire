plugins {
    base
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
}

tasks.named("build") {
    dependsOn(":protocol:build", ":tv:build", ":phone:build")
}

tasks.register("verify") {
    group = "verification"
    description = "Runs all JVM unit tests and Android lint checks."
    dependsOn(":protocol:test", ":tv:testDebugUnitTest", ":phone:testDebugUnitTest", ":tv:lint", ":phone:lint")
}
