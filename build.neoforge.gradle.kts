plugins {
	java
	idea
	id("net.neoforged.moddev")
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
val javaVersion = if (stonecutter.current.parsed >= "26") 25 else 21

group = prop("mod.group")
version = "${prop("mod.version")}+$mcVersion-neoforge"
base.archivesName = "$modId-neoforge"

java {
	toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
	withSourcesJar()
}

// Stonecutter owns the root `src/main` tree and substitutes it here itself, so only the extra
// roots are declared. See build.fabric.gradle.kts for what each one holds.
sourceSets.main {
	java.srcDir(rootProject.file("src/mc/shared/java"))
	java.srcDir(rootProject.file("src/mc/$mcVersion/java"))
	java.srcDir(rootProject.file("src/neoforge/java"))
	java.srcDir(rootProject.file("src/neoforge/mc$mcVersion/java"))
	resources.srcDir(rootProject.file("src/mc/shared/resources"))
	// GeckoLib 4 (1.21.1) and 5 (1.21.2+) look for models and animations in different folders.
	resources.srcDir(rootProject.file("src/gecko$geckoMajor/resources"))
	resources.srcDir(rootProject.file("src/mc/$mcVersion/resources"))
	resources.srcDir(rootProject.file("src/neoforge/resources"))
}

sourceSets.test {
	java.setSrcDirs(listOf(rootProject.file("src/test/java")))
	resources.setSrcDirs(listOf(rootProject.file("src/test/resources")))
}

repositories {
	mavenCentral()
	maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") { name = "GeckoLib"; content { includeGroup("software.bernie.geckolib") } }
	maven("https://api.modrinth.com/maven") { name = "Modrinth"; content { includeGroup("maven.modrinth") } }
}

dependencies {
	implementation(prop("deps.geckolib"))
	// config screens: built with YACL or Cloth Config when the player has one (else a plain screen);
	// neither is needed at runtime
	listOf("deps.yacl", "deps.cloth-config").mapNotNull { optionalProp(it) }.forEach { compileOnly(it) { isTransitive = false } }
}

neoForge {
	version = prop("deps.neoforge")

	runs {
		register("client") {
			client()
			gameDirectory = rootProject.file("run/$mcVersion-neoforge")
			ideName = "NeoForge $mcVersion Client"
			programArgument("--username")
			programArgument("Dev")
			// `-Pdragonsworn.showcase` makes the client build a test world, spawn the dragon, play every
			// animation, photograph it and quit -- the NeoForge half of the in-game test.
			if (providers.gradleProperty("dragonsworn.showcase").isPresent) {
				jvmArgument("-Ddragonsworn.showcase=true")
				val only = providers.gradleProperty("dragonsworn.showcase").get()
				if (only.isNotBlank() && only != "true") jvmArgument("-Ddragonsworn.showcase.only=$only")
				programArguments.addAll("--width", "1600", "--height", "900")
			}
			// `-Pdragonsworn.arena`: a tour of the End's monoliths (see ArenaTour.java)
			if (providers.gradleProperty("dragonsworn.arena").isPresent) {
				jvmArgument("-Ddragonsworn.arena=true")
				programArguments.addAll("--width", "1600", "--height", "900")
			}
			// `-Pdragonsworn.configScreen=plain|cloth|yacl`: which library builds the config screen (ConfigScreens)
			if (providers.gradleProperty("dragonsworn.configScreen").isPresent) {
				jvmArgument("-Ddragonsworn.configScreen=" + providers.gradleProperty("dragonsworn.configScreen").get())
			}
		}
		register("server") {
			server()
			gameDirectory = rootProject.file("run/$mcVersion-neoforge-server")
			ideName = "NeoForge $mcVersion Server"
		}
	}

	mods { register(modId) { sourceSet(sourceSets["main"]) } }
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release = javaVersion
}

tasks.processResources {
	val tokens = mapOf(
		"id" to modId,
		"name" to prop("mod.name"),
		"version" to prop("mod.version"),
		"description" to prop("mod.description"),
		"mcVersion" to mcVersion,
		"neoVersion" to prop("deps.neoforge"),
		"java" to javaVersion.toString(),
		"license" to prop("mod.license"),
		"authors" to prop("mod.authors"),
	)
	inputs.properties(tokens)
	filesMatching(listOf("META-INF/neoforge.mods.toml", "*.mixins.json")) { expand(tokens) }
}

tasks.named("createMinecraftArtifacts") { dependsOn(tasks.named("stonecutterGenerate")) }

dependencies {
	testImplementation(platform("org.junit:junit-bom:6.1.3"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

// LGPL: the licence travels with the binary (and says which assets it does not cover)
tasks.named<Jar>("jar") { from(rootProject.files("LICENSE", "COPYING", "LICENSE-ASSETS.md")) }
