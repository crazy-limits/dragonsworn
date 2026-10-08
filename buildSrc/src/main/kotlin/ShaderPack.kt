import org.gradle.api.Project
import java.io.File
import java.net.URI

/** The shader pack the dev client is checked with: Complementary Reimagined, the pack we support (it runs on macOS). */
const val SHADER_PACK = "ComplementaryReimagined_r5.9.3.zip"
private const val SHADER_PACK_URL = "https://cdn.modrinth.com/data/HVnmMxH1/versions/Bqen1mJX/$SHADER_PACK"

/**
 * `-Pdragonsworn.iris`: puts [SHADER_PACK] into `<runDir>/shaderpacks` and tells Iris to use it, before `runClient`.
 * Both are kept once there (shaders switch off in-game as usual).
 */
fun Project.installShaderPack(runDir: File) {
	if (!providers.gradleProperty("dragonsworn.iris").isPresent) return
	val install = tasks.register("installShaderPack") {
		outputs.file(runDir.resolve("shaderpacks/$SHADER_PACK"))
		doLast {
			val pack = runDir.resolve("shaderpacks/$SHADER_PACK")
			if (!pack.exists()) {
				pack.parentFile.mkdirs()
				URI(SHADER_PACK_URL).toURL().openStream().use { input -> pack.outputStream().use { input.copyTo(it) } }
			}
			val config = runDir.resolve("config/iris.properties")
			if (!config.exists()) {
				config.parentFile.mkdirs()
				config.writeText("enableShaders=true\nshaderPack=$SHADER_PACK\n")
			}
		}
	}
	tasks.matching { it.name == "runClient" }.configureEach { dependsOn(install) }
}
