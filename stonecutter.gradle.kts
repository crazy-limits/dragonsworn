plugins {
	id("dev.kikugie.stonecutter")
	id("dev.kikugie.loom-back-compat") apply false
	id("net.neoforged.moddev") version "2.0.148" apply false
}

// The version the IDE / plain `./gradlew` commands operate on.
stonecutter active file(".sc_active_version")

stonecutter parameters {
	// Enables `//? if fabric {` / `//? if neoforge {` in shared sources.
	constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")

	// `/*$ mod_version*/"0.0.0";` token swaps in shared sources. A swap consumes everything up to
	// the trailing `;`, so a token can only end a statement.
	swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
	swaps["mod_id"] = "\"${properties.get<String>("mod.id")}\";"
	swaps["minecraft"] = "\"${current.version}\";"
}

tasks.register("runActiveClient") {
	group = "stonecutter"
	description = "Run the client for whichever version .sc_active_version points at"
	dependsOn("${stonecutter.current!!.project}:runClient")
}

tasks.register("buildAll") {
	group = "stonecutter"
	description = "Build every version/loader combination"
	dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}

tasks.register("testAll") {
	group = "stonecutter"
	description = "Run the shared-core tests on every version/loader combination"
	dependsOn(stonecutter.versions.map { ":${it.project}:test" })
}
