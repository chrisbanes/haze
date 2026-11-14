plugins {
  kotlin("jvm") version "2.4.21"
  application
}

repositories { mavenCentral() }
kotlin { jvmToolchain(21) }
dependencies {
  implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.2")
}
application { mainClass.set("ReproducerKt") }
