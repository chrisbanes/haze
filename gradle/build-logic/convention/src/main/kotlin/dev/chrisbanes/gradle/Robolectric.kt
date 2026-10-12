// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

/**
 * Resolves the Robolectric `android-all-instrumented` runtime jars as ordinary Gradle dependencies,
 * so setup-gradle can cache and retry them, and points Robolectric at them so tests never download.
 */
internal fun Project.configureRobolectricRuntime() {
  val robolectricVersion = libs.findVersion("robolectric").get().requiredVersion

  val sdkProvider = configurations.create("robolectricSdkProvider") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }
  dependencies.add(sdkProvider.name, "org.robolectric:robolectric:$robolectricVersion")

  // One detached configuration per jar: the artifacts share a module, so one configuration would
  // conflict-resolve them down to the newest version.
  val runtimeJars = files(
    provider {
      androidAllCoordinates(sdkProvider.files, Versions.ROBOLECTRIC_SDKS).map {
        configurations.detachedConfiguration(dependencies.create(it)).apply { isTransitive = false }
      }
    },
  )

  val depsFile = layout.buildDirectory.file("robolectric/robolectric-deps.properties")
  val writeDeps = tasks.register("writeRobolectricDependencies") {
    val jars = runtimeJars
    // The output embeds absolute paths, so keep the default path sensitivity: it re-runs if they move.
    inputs.files(jars)
    outputs.file(depsFile)
    doLast {
      val deps = Properties()
      jars.forEach {
        val version = it.name.removePrefix("android-all-instrumented-").removeSuffix(".jar")
        deps["org.robolectric:android-all-instrumented:$version"] = it.absolutePath
      }
      depsFile.get().asFile.also { it.parentFile.mkdirs() }.outputStream().use { deps.store(it, null) }
    }
  }

  tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    dependsOn(writeDeps)
    // The jar contents key the build cache; their machine-specific location does not.
    inputs.files(runtimeJars).withPathSensitivity(PathSensitivity.NONE).withPropertyName("robolectricRuntimeJars")
    jvmArgumentProviders.add(RobolectricOfflineArguments(depsFile))
  }
}

internal class RobolectricOfflineArguments(
  @get:Internal val depsFile: Provider<RegularFile>,
) : CommandLineArgumentProvider {
  override fun asArguments() = listOf(
    "-Drobolectric.offline=true",
    "-Drobolectric-deps.properties=${depsFile.get().asFile}",
  )
}

/**
 * Asks Robolectric's own SDK table which `android-all-instrumented` artifact each API level needs.
 * This reflects over `DefaultSdkProvider(DependencyResolver)`, which is not a published API, so a
 * Robolectric bump that reshapes it fails here with a message that names the missing piece.
 */
private fun androidAllCoordinates(robolectricClasspath: Iterable<File>, apiLevels: List<Int>): List<String> {
  URLClassLoader(robolectricClasspath.map { it.toURI().toURL() }.toTypedArray(), null).use { loader ->
    try {
      val resolverType = loader.loadClass("org.robolectric.internal.dependency.DependencyResolver")
      val dependencyJarType = loader.loadClass("org.robolectric.internal.dependency.DependencyJar")
      val sdkType = loader.loadClass("org.robolectric.pluginapi.Sdk")
      // Robolectric only checks that the file it is handed exists, so any existing file will do.
      val placeholder = robolectricClasspath.first().toURI().toURL()
      val requested = mutableListOf<String>()
      // Every call to the resolver is a request for one jar: record its coordinate, return a stand-in.
      val resolver = Proxy.newProxyInstance(loader, arrayOf(resolverType)) { _, method, args ->
        check(method.name == "getLocalArtifactUrl") { "Unexpected DependencyResolver call: ${method.name}" }
        requested += dependencyJarType.getMethod("getShortName").invoke(args[0]) as String
        placeholder
      }
      val sdkProvider = loader.loadClass("org.robolectric.plugins.DefaultSdkProvider")
        .getConstructor(resolverType)
        .newInstance(resolver)
      val sdks = sdkProvider.javaClass.getMethod("getSdks").invoke(sdkProvider) as Collection<*>
      val byLevel = sdks.filterNotNull().associateBy { sdkType.getMethod("getApiLevel").invoke(it) as Int }
      return apiLevels.map { level ->
        val sdk = checkNotNull(byLevel[level]) { "Robolectric does not know SDK $level" }
        requested.clear()
        sdkType.getMethod("getJarPath").invoke(sdk)
        checkNotNull(requested.singleOrNull()) { "Robolectric requested $requested for SDK $level, expected one jar" }
      }
    } catch (e: ReflectiveOperationException) {
      val cause = (e as? InvocationTargetException)?.targetException ?: e
      throw IllegalStateException(
        "Cannot read the android-all-instrumented coordinates from Robolectric: $cause. " +
          "If a Robolectric bump reshaped DefaultSdkProvider or DependencyResolver, update Robolectric.kt.",
        cause,
      )
    }
  }
}
