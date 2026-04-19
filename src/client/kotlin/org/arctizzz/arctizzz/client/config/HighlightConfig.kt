package org.arctizzz.arctizzz.client.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.arctizzz.arctizzz.client.esp.EspMode
import java.io.File

class HighlightConfig(
    val filters: MutableList<String> = mutableListOf(),
    var scanDelay: Int = 20,
    var colorHex: String = "FF0000",
    var depthCheck: Boolean = false,
    // Stored as String? so Gson can handle it safely when the field didn't exist in an
    // older config file.  A null / unrecognised value falls back to OUTLINE.
    private var modeName: String? = null
) {
    /**
     * Active rendering mode.  Backed by [modeName] (String) to survive Gson round-trips
     * even when the field was absent in an older config file.
     */
    var mode: EspMode
        get() = modeName?.let { runCatching { EspMode.valueOf(it) }.getOrNull() } ?: EspMode.OUTLINE
        set(value) { modeName = value.name }

    /** Returns R, G, B as ints (0–255). Falls back to red on invalid / null hex. */
    fun parsedColor(): Triple<Int, Int, Int> {
        val hex = (colorHex ?: "FF0000").trimStart('#').padEnd(6, '0').take(6)
        return Triple(
            hex.substring(0, 2).toIntOrNull(16) ?: 255,
            hex.substring(2, 4).toIntOrNull(16) ?: 0,
            hex.substring(4, 6).toIntOrNull(16) ?: 0
        )
    }

    fun save() {
        configFile.parentFile.mkdirs()
        configFile.writeText(gson.toJson(this))
    }

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()
        private val configFile: File
            get() = FabricLoader.getInstance().configDir
                .resolve("arctizzz/highlight.json").toFile()

        fun load(): HighlightConfig {
            if (!configFile.exists()) return HighlightConfig().also { it.save() }
            return runCatching {
                gson.fromJson(configFile.readText(), HighlightConfig::class.java)
            }.getOrNull() ?: HighlightConfig()
        }
    }
}
