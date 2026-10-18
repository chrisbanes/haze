// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fully qualified names of public classes that are allowed to declare `componentN()` functions in
 * the checked-in API signatures, which is how a Kotlin `data class` appears there.
 *
 * Adding a property to a public `data class` changes its constructor, `copy` and `componentN` JVM
 * signatures, so it breaks binary compatibility. These classes shipped as data classes and are
 * already stable, so they can only be converted in the next major version. New public value types
 * must use `@Poko` with an explicit `copy` instead (see AGENTS.md). Add an entry only when
 * destructuring is an intentional part of the API, and remove an entry when its class is converted.
 */
internal val API_DATA_CLASS_ALLOWLIST: Set<String> = setOf(
  "dev.chrisbanes.haze.HazeInput.Sources",
  "dev.chrisbanes.haze.HazeInput.Backdrop",
  "dev.chrisbanes.haze.HazeProgressive.LinearGradient",
  "dev.chrisbanes.haze.glass.OpticalSizePoint",
  "dev.chrisbanes.haze.glass.RefractionProfile.Edge",
)

private val PACKAGE_LINE = Regex("""^package (\S+) \{""")
private val CLASS_LINE = Regex(
  """^ {2}\S.*\b(?:class|interface|enum) ([\w.]+)(?:<.*?>)?(?: (?:extends|implements) .*)? \{$""",
)
private val COMPONENT_METHOD = Regex("""^ {4}method .*\bcomponent[1-9][0-9]*\(\);""")

/**
 * Returns the fully qualified names of classes in a Metalava signature file that declare a
 * zero-argument `componentN()` method. Metalava doesn't print `operator` on every such method, so
 * the method name is what identifies a data class.
 */
internal fun findComponentClasses(signature: String): Set<String> {
  var packageName = ""
  var className: String? = null
  val result = linkedSetOf<String>()
  for (line in signature.lineSequence()) {
    val packageMatch = PACKAGE_LINE.find(line)
    val classMatch = CLASS_LINE.find(line)
    when {
      packageMatch != null -> {
        packageName = packageMatch.groupValues[1]
        className = null
      }

      classMatch != null -> className = "$packageName.${classMatch.groupValues[1]}"

      COMPONENT_METHOD.containsMatchIn(line) -> className?.let(result::add)
    }
  }
  return result
}

abstract class CheckApiDataClassesTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val apiFile: RegularFileProperty

  @get:Input
  abstract val allowlist: SetProperty<String>

  @TaskAction
  fun check() {
    val offenders = findComponentClasses(apiFile.get().asFile.readText()) - allowlist.get()
    if (offenders.isNotEmpty()) {
      throw GradleException(
        "$path: ${apiFile.get().asFile} declares componentN() on non-allowlisted classes: " +
          "${offenders.joinToString()}. Public value types must use @Poko with an explicit copy " +
          "(see AGENTS.md). If destructuring is intentional, add the class to " +
          "API_DATA_CLASS_ALLOWLIST in " +
          "gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/ApiDataClassCheck.kt.",
      )
    }
  }
}
