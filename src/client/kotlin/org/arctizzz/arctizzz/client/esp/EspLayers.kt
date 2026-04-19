package org.arctizzz.arctizzz.client.esp

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderSetup
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier

/**
 * Custom render layers for ESP.
 *
 * Through-wall layers (NO_DEPTH_TEST, depth-write off):
 *   ESP            — LINES,  used for OUTLINE mode when depthCheck=false, and as the
 *                    dim occluded pass when depthCheck=true.
 *   ESP_FILLED     — QUADS,  used for BOX mode when depthCheck=false, and as the
 *                    dim occluded pass when depthCheck=true.
 *
 * Depth-aware layers (LEQUAL_DEPTH_TEST, depth-write on):
 *   ESP_DEPTH        — LINES,  bright visible pass for OUTLINE mode when depthCheck=true.
 *   ESP_FILLED_DEPTH — QUADS,  bright visible pass for BOX mode when depthCheck=true.
 *
 * Access Wideners required (src/main/resources/arctizzz.accesswidener):
 *   accessible method net/minecraft/client/render/RenderLayer of (...)
 *   accessible field  net/minecraft/client/gl/RenderPipelines TRANSFORMS_AND_PROJECTION_SNIPPET
 *   accessible field  net/minecraft/client/gl/RenderPipelines RENDERTYPE_LINES_SNIPPET
 *   accessible field  net/minecraft/client/gl/RenderPipelines POSITION_COLOR_SNIPPET
 */
object EspLayers {

    lateinit var ESP: RenderLayer
        private set

    lateinit var ESP_FILLED: RenderLayer
        private set

    lateinit var ESP_DEPTH: RenderLayer
        private set

    lateinit var ESP_FILLED_DEPTH: RenderLayer
        private set

    fun init() {
        if (::ESP.isInitialized) return

        // ---- through-wall wireframe (LINES, NO_DEPTH_TEST) ----
        val linesPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(
                RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET,
                RenderPipelines.RENDERTYPE_LINES_SNIPPET
            )
                .withLocation(Identifier.of("arctizzz", "pipeline/esp_lines"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build()
        )
        ESP = RenderLayer.of("arctizzz:esp", RenderSetup.builder(linesPipeline).build())

        // ---- through-wall filled (QUADS, NO_DEPTH_TEST) ----
        val filledPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(
                RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET,
                RenderPipelines.POSITION_COLOR_SNIPPET
            )
                .withLocation(Identifier.of("arctizzz", "pipeline/esp_filled"))
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthBias(-1.0f, -1.0f)
                .build()
        )
        ESP_FILLED = RenderLayer.of("arctizzz:esp_filled", RenderSetup.builder(filledPipeline).build())

        // ---- depth-tested wireframe (LINES, LEQUAL_DEPTH_TEST) ----
        val linesDepthPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(
                RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET,
                RenderPipelines.RENDERTYPE_LINES_SNIPPET
            )
                .withLocation(Identifier.of("arctizzz", "pipeline/esp_lines_depth"))
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                .build()
        )
        ESP_DEPTH = RenderLayer.of("arctizzz:esp_depth", RenderSetup.builder(linesDepthPipeline).build())

        // ---- depth-tested filled (QUADS, LEQUAL_DEPTH_TEST) ----
        val filledDepthPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(
                RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET,
                RenderPipelines.POSITION_COLOR_SNIPPET
            )
                .withLocation(Identifier.of("arctizzz", "pipeline/esp_filled_depth"))
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthBias(-1.0f, -1.0f)
                .build()
        )
        ESP_FILLED_DEPTH = RenderLayer.of("arctizzz:esp_filled_depth", RenderSetup.builder(filledDepthPipeline).build())
    }
}
