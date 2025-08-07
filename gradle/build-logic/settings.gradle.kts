// Copyright 2023, Christopher Banes and the Tivi project contributors
// SPDX-License-Identifier: Apache-2.0

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex(".*google.*")
        includeGroupByRegex(".*android.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      content {
        includeGroupByRegex(".*google.*")
        includeGroupByRegex(".*android.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }

  versionCatalogs {
    create("libs") {
      from(files("../libs.versions.toml"))
    }
  }
}

include(":convention")
