plugins {
	java
	idea
	id("dev.kikugie.loom-back-compat")
}

stonecutter {
	val (mc, loader) = current.project.split('-', limit = 2)
	properties.tags(mc, loader)
}

fun prop(key: String): String = stonecutter.properties.get<String>(key)
/** A property this version may not declare (optional integrations). */
fun optionalProp(key: String): String? = runCatching { stonecutter.properties.get<String>(key) }.getOrNull()

val modId = prop("mod.id")
val mcVersion = stonecutter.current.version
val geckoMajor = if (stonecutter.current.parsed >= "1.21.2") 5 else 4
// 26.x requires Java 25 and ships deobfuscated; the 1.21 line runs on 21 with Mojang mappings.
val isUnobfuscated = stonecutter.current.parsed >= "26"
val javaVersion = if (isUnobfuscated) 25 else 21

group = prop("mod.group")
version = "${prop("mod.version")}+$mcVersion-fabric"
base.archivesName = "$modId-fabric"

java {
	toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
	withSourcesJar()
}

// Shared code must be compiled from Stonecutter's PROCESSED copy of `src/main/java`, never the raw
// tree, or `//? if` branches and token swaps are silently ignored. Loom rebuilds the source set after
// Stonecutter substitutes it, so on Fabric the generated mirror is named by hand.
val stonecutterJava = layout.buildDirectory.dir("generated/stonecutter/main/java")

sourceSets.main {
	java.setSrcDirs(
		listOf(
			stonecutterJava,                                   // preprocessed, game-free core
			rootProject.file("src/mc/shared/java"),            // bridge code identical on every version
			rootProject.file("src/mc/$mcVersion/java"),        // GeckoLib + Minecraft bridge for this version
			rootProject.file("src/fabric/java"),               // loader entrypoints
			rootProject.file("src/fabric/mc$mcVersion/java"),  // fabric-api whose shape moved between versions
		).filter { it !is File || it.isDirectory }
	)
	resources.srcDir(rootProject.file("src/mc/shared/resources"))     // textures
	// GeckoLib 4 (1.21.1) and 5 (1.21.2+) look for models and animations in different folders.
	resources.srcDir(rootProject.file("src/gecko$geckoMajor/resources"))
	resources.srcDir(rootProject.file("src/mc/$mcVersion/resources")) // pack.mcmeta, version-specific asset paths
	resources.srcDir(rootProject.file("src/fabric/resources"))
}

// The tests prove the core stays loader-free and Minecraft-free: `src/test` against `src/main` only.
sourceSets.test {
	java.setSrcDirs(listOf(rootProject.file("src/test/java")))
	resources.setSrcDirs(listOf(rootProject.file("src/test/resources")))
}

repositories {
	mavenCentral()
	maven("https://maven.fabricmc.net/") { name = "Fabric" }
	maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") { name = "GeckoLib"; content { includeGroup("software.bernie.geckolib"); includeGroup("com.geckolib") } }
	maven("https://api.modrinth.com/maven") { name = "Modrinth"; content { includeGroup("maven.modrinth") } }
	maven("https://maven.quiltmc.org/repository/release/") { name = "Quilt"; content { includeGroup("org.quiltmc.parsers") } }
	maven("https://maven.shedaniel.me/") { name = "Shedaniel"; content { includeGroup("me.shedaniel.cloth") } }
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	if (!isUnobfuscated) mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	if (isUnobfuscated) implementation(prop("deps.geckolib")) else modImplementation(prop("deps.geckolib"))
	// config screens: built with YACL or Cloth Config when the player has one (else a plain screen), Mod Menu's button;
	// none of them is needed at runtime
	listOf("deps.modmenu", "deps.yacl", "deps.cloth-config").mapNotNull { optionalProp(it) }.forEach { modCompileOnly(it) { isTransitive = false } }
	// `-Pdragonsworn.configLibs`: YACL and Cloth Config in the dev client too, to see their screens
	if (providers.gradleProperty("dragonsworn.configLibs").isPresent) {
		listOf("deps.yacl", "deps.cloth-config").mapNotNull { optionalProp(it) }.forEach { modLocalRuntime(it) { isTransitive = false } }
		// YACL's own libraries (its released jar nests them; the Modrinth maven's dev classpath does not)
		localRuntime("org.quiltmc.parsers:json:0.2.1")
		localRuntime("org.quiltmc.parsers:gson:0.2.1")
		localRuntime("me.shedaniel.cloth:basic-math:0.6.1")     // Cloth Config's, likewise
	}
}

loom {
	runs.named("client") {
		client()
		ideConfigGenerated(true)
		runDir = "../../run/$mcVersion-fabric"
		programArgs("--username", "Dev")
		configName = "Fabric $mcVersion Client"
	}
	// `-Pdragonsworn.showcase`: the client builds a test world, spawns the dragon, plays every animation,
	// photographs it into run/<target>/screenshots and quits. The in-game test; see Showcase.java.
	if (providers.gradleProperty("dragonsworn.showcase").isPresent) {
		runs.named("client") {
			vmArgs("-Ddragonsworn.showcase=true")
			// `-Pdragonsworn.showcase=<stage>` runs only that stage (see Showcase.ONLY)
			val only = providers.gradleProperty("dragonsworn.showcase").get()
			if (only.isNotBlank() && only != "true") vmArgs("-Ddragonsworn.showcase.only=$only")
			programArgs("--width", "1600", "--height", "900")
		}
	}
	// `-Pdragonsworn.arena`: a tour of the End's monoliths, photographed, then a dragon respawn that must
	// rebuild them exactly (see ArenaTour.java).
	// `-Pdragonsworn.configScreen=plain|cloth|yacl`: which library builds the config screen (ConfigScreens)
	if (providers.gradleProperty("dragonsworn.configScreen").isPresent) {
		runs.named("client") { vmArgs("-Ddragonsworn.configScreen=" + providers.gradleProperty("dragonsworn.configScreen").get()) }
	}
	if (providers.gradleProperty("dragonsworn.arena").isPresent) {
		runs.named("client") {
			vmArgs("-Ddragonsworn.arena=true")
			programArgs("--width", "1600", "--height", "900")
		}
	}
	// `-Pdragonsworn.film`: clips of the dragon on the End island, a frame a tick, into run/<target>/screenshots
	// (see Film.java; `-Pdragonsworn.film=walk,jaws` films only those scenes); tools/film_gifs.py makes the GIFs.
	if (providers.gradleProperty("dragonsworn.film").isPresent) {
		runs.named("client") {
			vmArgs("-Ddragonsworn.film=true")
			val only = providers.gradleProperty("dragonsworn.film").get()
			if (only.isNotBlank() && only != "true") vmArgs("-Ddragonsworn.film.only=$only")
			programArgs("--width", "1280", "--height", "720")
		}
	}
	runs.named("server") {
		server()
		ideConfigGenerated(true)
		runDir = "../../run/$mcVersion-fabric-server"
		configName = "Fabric $mcVersion Server"
	}
}

tasks.withType<JavaCompile>().configureEach {
	dependsOn(tasks.named("stonecutterGenerate"))
	options.encoding = "UTF-8"
	// every error at once (a port to a new Minecraft version starts with hundreds)
	options.compilerArgs.addAll(listOf("-Xmaxerrs", "5000"))
	options.release = javaVersion
}
tasks.withType<Jar>().configureEach { dependsOn(tasks.named("stonecutterGenerate")) }

tasks.processResources {
	dependsOn(tasks.named("stonecutterGenerate"))
	val tokens = mapOf(
		"id" to modId,
		"name" to prop("mod.name"),
		"version" to prop("mod.version"),
		"description" to prop("mod.description"),
		"license" to prop("mod.license"),
		"authors" to prop("mod.authors"),
		"mcVersion" to mcVersion,
		"loaderVersion" to prop("deps.fabric-loader"),
		"java" to javaVersion.toString(),
	)
	inputs.properties(tokens)
	filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(tokens) }
}

dependencies {
	testImplementation(platform("org.junit:junit-bom:6.1.3"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

// LGPL: the licence travels with the binary (and says which assets it does not cover)
tasks.named<Jar>("jar") { from(rootProject.files("LICENSE", "COPYING", "LICENSE-ASSETS.md")) }
