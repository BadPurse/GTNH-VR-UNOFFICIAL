
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Lwjgl3ify-Aware" to "true"
        )
    }
}


