plugins {
    java
    kotlin("jvm") version "1.7.0"
}

group = "il.ac.technion.cs"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

val graalVersion = "22.1.0"
val jgraphtVersion = "1.5.1"
val junitVersion = "5.8.2"

val apron_location = "/home/mip/phd/repos/apron/prefix"
val elina_location = "/home/mip/phd/repos/ELINA"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    // ReactiveX
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")
    implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")

    // Arrow (should use sparingly?)
    implementation("io.arrow-kt:arrow-core:1.1.2")

    // Graal
    implementation("org.graalvm.sdk:graal-sdk:$graalVersion")
    implementation("org.graalvm.compiler:compiler:$graalVersion")
    implementation("org.graalvm.tools:insight:$graalVersion")

    // jgrapht
    implementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
    implementation("org.jgrapht:jgrapht-io:$jgraphtVersion")

    // spotbugs annotations (for @NonNull in Java)
    implementation("com.github.spotbugs:spotbugs-annotations:4.6.0")

    // better-parse parser library
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.4")

    // JSON parser/builder
    implementation("com.beust:klaxon:5.6")

    if (File(apron_location).isDirectory) {
        implementation(files("$apron_location/lib/gmp.jar", "$apron_location/lib/apron.jar"))
    }

    if (File(elina_location).isDirectory) {
        implementation(files("$elina_location/java_interface/elina.jar"))
    }

    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.amshove.kluent:kluent:1.68")
    testImplementation("io.mockk:mockk:1.12.4")
}

val moduleArgs = listOf(
    "--add-modules", "jdk.internal.vm.ci,jdk.internal.vm.compiler",
    "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED", // diff
    "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.code=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.api.runtime=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.debug=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.options=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.runtime=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.core.target=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.cfg=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.virtual=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodeinfo=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.phases.tiers=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.phases=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.code=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.core=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.core.phases=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.graphbuilderconf=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.java=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.graph=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.graph=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.memory=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.memory.address=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.phases.common=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.java=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.graph.iterators=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.calc=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.spi=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.extended=ALL-UNNAMED"
)

tasks {
    compileJava {
        options.apply {
            compilerArgs = moduleArgs
        }
    }
    compileTestJava {
        options.apply {
            compilerArgs = moduleArgs
        }
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.freeCompilerArgs = listOf("-Xextended-compiler-checks")
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.freeCompilerArgs = listOf("-Xextended-compiler-checks")
    }

    test {
        useJUnitPlatform()
        jvmArgs = moduleArgs + listOf(
            "-Ddebug.graal.TrackNodeCreationPosition=true",
            "--add-opens",
            "jdk.internal.vm.compiler/org.graalvm.compiler.graph=ALL-UNNAMED",
        )
        maxHeapSize = "8g"
        var libraryPath = "/usr/local/lib"
        if (File(apron_location).exists())
            libraryPath += ":$apron_location/lib"
        if (File(elina_location).exists())
            libraryPath += ":$elina_location/lib"
        systemProperty("java.library.path", libraryPath)
        environment("LD_LIBRARY_PATH", libraryPath)
    }
}