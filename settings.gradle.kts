pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		maven("https://maven.architectury.dev/") { name = "Architectury" }
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("dev.kikugie.stonecutter") version "0.9.8"
	// Lets one Loom version drive several Minecraft versions that would normally each want their own.
	id("dev.kikugie.loom-back-compat") version "0.4.2"
}

rootProject.name = "dragonsworn"

stonecutter {
	create(rootProject) {
		fun match(version: String, vararg loaders: String) =
			loaders.forEach { version("$version-$it", version).buildscript = "build.$it.gradle.kts" }

		// Newest first. Each entry becomes a Gradle subproject, e.g. :1.21.1-neoforge.
		// Add a Minecraft version here AND in stonecutter.properties.toml -- nowhere else.
		// Paused until their src/mc/<version> bridge exists (every declared target is configured, and
		// downloaded, on each Gradle run, and buildAll/testAll could not pass). Their deps stay in the
		// properties file; uncomment to start a port.
		// match("26.2", "fabric", "neoforge")
		// match("1.21.11", "fabric", "neoforge")
		match("1.21.1", "fabric", "neoforge")

		vcsVersion = "1.21.1-neoforge"
	}
}
