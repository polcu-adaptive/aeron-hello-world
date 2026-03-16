plugins {
    id("java")
}

group = "weareadaptive.com"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

configurations {
    create("sbe")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("io.aeron:aeron-all:1.50.2")
    "sbe"("uk.co.real-logic:sbe-tool:1.37.1")
    implementation("io.vertx:vertx-core:5.0.8")
    implementation("io.vertx:vertx-web:5.0.8")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec> {
    jvmArgs(
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED"
    )
}

val sbeOutputDir = layout.buildDirectory.dir("generated/sbe")

tasks.register<JavaExec>("generateSbe") {
    group = "codegen"
    description = "Generate SBE codecs from XML schemas"

    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    classpath = configurations["sbe"]

    systemProperty("sbe.output.dir", sbeOutputDir.get().asFile.absolutePath)
    systemProperty("sbe.target.language", "Java")
    systemProperty("sbe.validation.xsd", "src/main/resources/sbe.xsd")
    systemProperty("sbe.validation.stop.on.error", "true")

    args(file("src/main/resources/message.xml").absolutePath)
}

sourceSets.main {
    java.srcDir(sbeOutputDir)
}