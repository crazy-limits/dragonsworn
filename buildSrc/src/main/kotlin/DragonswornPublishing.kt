import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure

class PublishingDependencies {
	val required = mutableListOf<String>()
	val optional = mutableListOf<String>()

	fun requires(vararg slugs: String) { required += slugs }
	fun optional(vararg slugs: String) { optional += slugs }
}

/**
 * Configures uploading to Modrinth and CurseForge, enabled by the `modrinthToken` and `curseforgeToken` Gradle properties
 * (an empty one, like an unset CI secret, counts as missing) and the project ids `mod.modrinth` / `mod.curseforge`
 * (an empty id skips that platform). The GitHub release, with every jar in one place, is made by the release workflow.
 * Slugs are the same on both platforms for every dependency used here.
 */
fun Project.configurePublishing(
	loader: String,
	jar: Provider<RegularFile>,
	properties: (String) -> String,
	mcVersion: String,
	deps: PublishingDependencies.() -> Unit = {},
) {
	val dependencies = PublishingDependencies().apply(deps)
	val modVersion = properties("mod.version")

	extensions.configure<ModPublishExtension> {
		file.set(jar)
		displayName.set("$modVersion for $loader $mcVersion")
		version.set(project.version.toString())
		changelog.set(rootProject.file("CHANGELOG.md").readText())
		// Pre-release versions (0.1.0-alpha.1) are uploaded as alpha or beta files
		type.set(when {
			"-alpha" in modVersion -> ReleaseType.ALPHA
			"-beta" in modVersion -> ReleaseType.BETA
			else -> ReleaseType.STABLE
		})
		modLoaders.add(loader)
		// -PpublishDryRun checks everything without uploading
		dryRun.set(providers.gradleProperty("publishDryRun").isPresent)

		val modrinthId = properties("mod.modrinth")
		providers.gradleProperty("modrinthToken").orNull?.takeIf { it.isNotBlank() && modrinthId.isNotBlank() }?.let { token ->
			modrinth {
				projectId.set(modrinthId)
				accessToken.set(token)
				minecraftVersions.add(mcVersion)
				dependencies.required.forEach { requires(it) }
				dependencies.optional.forEach { optional(it) }
			}
		}

		val curseforgeId = properties("mod.curseforge")
		providers.gradleProperty("curseforgeToken").orNull?.takeIf { it.isNotBlank() && curseforgeId.isNotBlank() }?.let { token ->
			curseforge {
				projectId.set(curseforgeId)
				accessToken.set(token)
				// Needed on both sides
				client.set(true)
				server.set(true)
				minecraftVersions.add(mcVersion)
				dependencies.required.forEach { requires(it) }
				dependencies.optional.forEach { optional(it) }
			}
		}
	}
}
