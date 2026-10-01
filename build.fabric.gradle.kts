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
	maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") { name = "GeckoLib"; content { includeGroup("software.bernie.geckolib") } }
	maven("https://api.modrinth.com/maven") { name = "Modrinth"; content { includeGroup("maven.modrinth") } }
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	if (!isUnobfuscated) mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	if (isUnobfuscated) implementation(prop("deps.geckolib")) else modImplementation(prop("deps.geckolib"))
}

loom {
	runs.named("client") {
		client()
		ideConfigGenerated(true)
		runDir = "../../run/$mcVersion-fabric"
		programArgs("--username", "Dev")
		configName = "Fabric $mcVersion Client"
	}
	// `-Pdragonfall.showcase`: the client builds a test world, spawns the dragon, plays every animation,
	// photographs it into run/<target>/screenshots and quits. The in-game test; see Showcase.java.
	if (providers.gradleProperty("dragonfall.showcase").isPresent) {
		runs.named("client") {
			vmArgs("-Ddragonfall.showcase=true")
			programArgs("--width", "1600", "--height", "900")
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
