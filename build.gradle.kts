
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar>().configureEach {
    multiReleaseOriginal.set(false)
    multiReleaseVersions.set(emptySet())
}

tasks.withType<Jar>().configureEach {
    exclude("META-INF/versions/**")
    manifest {
        attributes(
            "Lwjgl3ify-Aware" to "true"
        )
    }
}



