// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Returns sorted `"<variant name> <group>:<module>"` lines for every dependency declared by a
 * variant in Gradle module metadata. Versions are left out so catalog bumps don't churn snapshots.
 */
internal fun publishedDependencies(moduleMetadata: String): List<String> {
  val root = JsonSlurper().parseText(moduleMetadata) as Map<*, *>
  return (root["variants"] as? List<*>).orEmpty().flatMap { variant ->
    variant as Map<*, *>
    (variant["dependencies"] as? List<*>).orEmpty().map { dependency ->
      dependency as Map<*, *>
      "${variant["name"]} ${dependency["group"]}:${dependency["module"]}"
    }
  }.sorted()
}

abstract class CheckPublishedDependenciesTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val metadataFiles: ConfigurableFileCollection

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val snapshot: RegularFileProperty

  @TaskAction
  fun check() {
    val files = metadataFiles.files
    if (files.size != 2 || files.any { !it.isFile }) {
      throw GradleException("$path: expected the Android and JVM module metadata files, found $files.")
    }
    val actual = files.flatMap { publishedDependencies(it.readText()) }.sorted()
    val snapshotFile = snapshot.get().asFile
    val expected = snapshotFile.readLines().filter { it.isNotBlank() }
    if (actual != expected) {
      throw GradleException(
        "$path: the published dependencies differ from ${snapshotFile.path}.\n" +
          "Missing: ${expected - actual.toSet()}\nUnexpected: ${actual - expected.toSet()}\n" +
          "If the change is intended, replace the snapshot with:\n${actual.joinToString("\n")}\n",
      )
    }
  }
}

/** Snapshots this module's published Android and JVM dependencies in `published-dependencies.txt`. */
fun Project.checkPublishedDependencies() {
  val check = tasks.register<CheckPublishedDependenciesTask>("checkPublishedDependencies") {
    group = "verification"
    description = "Checks the dependencies in the published module metadata against published-dependencies.txt."
    snapshot.set(layout.projectDirectory.file("published-dependencies.txt"))
    metadataFiles.from(
      tasks.withType<GenerateModuleMetadata>().matching {
        it.name == "generateMetadataFileForAndroidPublication" ||
          it.name == "generateMetadataFileForJvmPublication"
      }.map { it.outputFile },
    )
  }
  tasks.named("check") { dependsOn(check) }
}
