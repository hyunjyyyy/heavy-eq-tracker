plugins {
    application
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
repositories {
    mavenCentral()
}
dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application {
    mainClass = "io.github.hyunjyyyy.heavyeqtracker.simulator.Simulator"
}
tasks.withType<Test> {
    useJUnitPlatform()
}
