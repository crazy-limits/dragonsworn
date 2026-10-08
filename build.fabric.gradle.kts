plugins {
	java
	idea
	id("dev.kikugie.loom-back-compat")
	id("me.modmuss50.mod-publish-plugin")
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
	// Each of these groups is only ever looked up in its own repository: a lookup elsewhere that fails (a 502 from
	// another maven) would disable that repository for the rest of the build
	fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
		forRepository { maven(url) { name = alias } }
		filter { groups.forEach(::includeGroup) }
	}
	strictMaven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/", "GeckoLib", "software.bernie.geckolib", "com.geckolib")
	strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
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
	val configLibs = providers.gradleProperty("dragonsworn.configLibs").isPresent
	// `-Pdragonsworn.enderscape`: Enderscape (+ Lithostitched, Apollib, Mixson, YACL) in the dev client, for compatibility checks
	val enderscape = providers.gradleProperty("dragonsworn.enderscape").isPresent
	// `-Pdragonsworn.stellarity`, `-Pdragonsworn.yungsEnd`: Stellarity, YUNG's Better End Island (+ YUNG's API, Cloth Config)
	// in the dev client: the arena tour then tours their island (compatibility checks)
	val yungsEnd = providers.gradleProperty("dragonsworn.yungsEnd").isPresent
	if (providers.gradleProperty("dragonsworn.stellarity").isPresent) optionalProp("deps.stellarity")?.let { modLocalRuntime(it) { isTransitive = false } }
	if (yungsEnd) optionalProp("deps.yungs-end")?.split(',')?.forEach { modLocalRuntime(it.trim()) { isTransitive = false } }
	if (yungsEnd) optionalProp("deps.yungs-end-libs")?.split(',')?.forEach { localRuntime(it.trim()) { isTransitive = false } }    // nested like YACL's
	if (configLibs || yungsEnd) optionalProp("deps.cloth-config")?.let { modLocalRuntime(it) { isTransitive = false } }
	if (configLibs || enderscape) optionalProp("deps.yacl")?.let {
		modLocalRuntime(it) { isTransitive = false }
		// YACL's own libraries (its released jar nests them; the Modrinth maven's dev classpath does not)
		localRuntime("org.quiltmc.parsers:json:0.2.1")
		localRuntime("org.quiltmc.parsers:gson:0.2.1")
	}
	if (configLibs || yungsEnd) localRuntime("me.shedaniel.cloth:basic-math:0.6.1")     // Cloth Config's, likewise
	if (enderscape) {
		optionalProp("deps.enderscape")?.split(',')?.forEach { modLocalRuntime(it.trim()) { isTransitive = false } }
		localRuntime("de.marhali:json5-java:3.0.0")     // Apollib's, nested like YACL's
	}
	// `-Pdragonsworn.shoulderSurfing`: Shoulder Surfing Reloaded (+ Forge Config API Port) in the dev client; the showcase's
	// `grabs` stage then checks a held player's camera in its over-the-shoulder view too (nothing compiled against it)
	if (providers.gradleProperty("dragonsworn.shoulderSurfing").isPresent) {
		optionalProp("deps.shouldersurfing")?.split(',')?.forEach {
			if (isUnobfuscated) localRuntime(it.trim()) { isTransitive = false } else modLocalRuntime(it.trim()) { isTransitive = false }
		}
		optionalProp("deps.shouldersurfing-libs")?.split(',')?.forEach { localRuntime(it.trim()) }    // nested like YACL's
	}
	// `-Pdragonsworn.iris`: Sodium + Iris in the dev client (with Complementary Reimagined: installShaderPack, buildSrc/ShaderPack.kt)
	if (providers.gradleProperty("dragonsworn.iris").isPresent) {
		optionalProp("deps.iris")?.split(',')?.forEach {
			if (isUnobfuscated) localRuntime(it.trim()) { isTransitive = false } else modLocalRuntime(it.trim()) { isTransitive = false }
		}
		// Iris's shader libraries (nested in its released jar, like YACL's)
		optionalProp("deps.iris-libs")?.split(',')?.forEach { localRuntime(it.trim()) }
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

installShaderPack(rootProject.file("run/$mcVersion-fabric"))

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

// The release workflow puts every target's jar in one place for the GitHub release
tasks.register<Copy>("buildAndCollect") {
	group = "build"
	description = "Build the mod jar and copy it to `build/libs/{mod version}/`"
	from(loomx.modJar.flatMap { it.archiveFile })
	into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
	dependsOn("build")
}

configurePublishing("fabric", loomx.modJar.flatMap { it.archiveFile }, ::prop, mcVersion) {
	requires("fabric-api", "geckolib")
	optional("modmenu", "yacl", "cloth-config")
}
