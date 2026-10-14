plugins {
    java
    application
}

group = "ru.lab"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val hadoopVersion = "3.5.0"

dependencies {
    compileOnly("org.apache.hadoop:hadoop-common:$hadoopVersion")
    compileOnly("org.apache.hadoop:hadoop-mapreduce-client-core:$hadoopVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.apache.hadoop:hadoop-common:$hadoopVersion")
    testImplementation("org.apache.hadoop:hadoop-mapreduce-client-core:$hadoopVersion")
    testRuntimeOnly("org.apache.hadoop:hadoop-mapreduce-client-jobclient:$hadoopVersion")
}

application {
    mainClass = "ru.lab.tfidf.Main"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ru.lab.tfidf.Main"
    }
}

tasks.register<JavaExec>("runLocalTfidf") {
    group = "application"
    description = "Runs the local single-threaded TF-IDF pipeline"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "ru.lab.tfidf.local.LocalTfidfMain"
}
