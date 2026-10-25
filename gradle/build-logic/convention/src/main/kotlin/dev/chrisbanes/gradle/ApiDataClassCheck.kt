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
 * the Metalava API signature, which is how a Kotlin `data class` appears there.
 *
 * Adding a property to a public `data class` changes its constructor, `copy` and `componentN` JVM
 * signatures, so it breaks binary compatibility. New public value types must use `@Poko` with an
 * explicit `copy` instead (see AGENTS.md). Add an entry only when destructuring is an intentional
 * part of the API, and remove an entry when its class is converted.
 *
 * The `haze` entries (`HazeInput.Sources`, `HazeInput.Backdrop` and `HazeProgressive.LinearGradient`)
 * shipped as data classes in a stable API, so they can only be converted in the next major version.
 * `OpticalSizePoint` and `RefractionProfile.Edge` were `@ExperimentalHazeApi` in 2.0.1 and could
 * have been converted for 2.1.0; they stay data classes by the decision in
 * https://github.com/chrisbanes/haze/issues/1559, so adding a property to either needs the same
 * bridge treatment as a stable class.
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
internal fun findComponentClasses(signature: String): Set<String> = findClasses(signature).filterValues { it }.keys

/**
 * Returns every class declared in a Metalava signature file, mapped to whether it declares a
 * zero-argument `componentN()` method.
 */
internal fun findClasses(signature: String): Map<String, Boolean> {
  var packageName = ""
  var className: String? = null
  val result = linkedMapOf<String, Boolean>()
  for (line in signature.lineSequence()) {
    val packageMatch = PACKAGE_LINE.find(line)
    val classMatch = CLASS_LINE.find(line)
    when {
      packageMatch != null -> {
        packageName = packageMatch.groupValues[1]
        className = null
      }

      classMatch != null -> {
        className = "$packageName.${classMatch.groupValues[1]}"
        result.putIfAbsent(className, false)
      }

      COMPONENT_METHOD.containsMatchIn(line) -> className?.let { result[it] = true }
    }
  }
  return result
}

/**
 * Returns the [allowlist] entries that [classes] declares without `componentN()`. Each module sees
 * only its own classes, so an entry for a class in another module isn't stale.
 */
internal fun staleAllowlistEntries(classes: Map<String, Boolean>, allowlist: Set<String>): List<String> = allowlist.filter { classes[it] == false }

abstract class CheckApiDataClassesTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val signatureFile: RegularFileProperty

  @get:Input
  abstract val allowlist: SetProperty<String>

  @TaskAction
  fun check() {
    val classes = findClasses(signatureFile.get().asFile.readText())
    val offenders = classes.filterValues { it }.keys - allowlist.get()
    if (offenders.isNotEmpty()) {
      throw GradleException(
        "$path: the public API generated from sources declares componentN() on non-allowlisted " +
          "classes: ${offenders.joinToString()}. Public value types must use @Poko with an explicit copy " +
          "(see AGENTS.md). If destructuring is intentional, add the class to " +
          "API_DATA_CLASS_ALLOWLIST in $ALLOWLIST_PATH.",
      )
    }
    val stale = staleAllowlistEntries(classes, allowlist.get())
    if (stale.isNotEmpty()) {
      throw GradleException(
        "$path: these API_DATA_CLASS_ALLOWLIST entries no longer declare componentN(): " +
          "${stale.joinToString()}. Remove them from $ALLOWLIST_PATH.",
      )
    }
  }
}

private const val ALLOWLIST_PATH = "gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/ApiDataClassCheck.kt"
