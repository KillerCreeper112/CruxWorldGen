
plugins {
    java
    alias(libs.plugins.paperweight)
    alias(libs.plugins.shadow)
    kotlin("jvm") version "2.1.0"
}

version = "1.0"

repositories {
    mavenCentral()
}

tasks{
    assemble{
        dependsOn(shadowJar)
    }
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)
    compileOnly(files(
        "E:\\Plugins\\YO\\CruxCore\\build\\libs\\CruxCore-1.0-all.jar",
    ))

    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))
    //implementation(kotlin("reflect"))
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))

    compileOnly(fileTree("libs"){
        include("*.jar")
    })
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

allprojects{

    plugins.apply("java")

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        systemProperty("file.encoding", "UTF-8")
    }

    tasks.withType<Javadoc>{
        options.encoding = "UTF-8"
    }
}













