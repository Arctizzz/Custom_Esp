package org.arctizzz.arctizzz.client.esp

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import org.arctizzz.arctizzz.client.config.HighlightConfig
import org.joml.Matrix4f

/**
 * Renders visual overlays for all entities tracked by [EspManager].
 * Runs in [WorldRenderEvents.END_MAIN].
 *
 * Modes:
 *   OUTLINE — wireframe bounding box (LINES pipeline)
 *   BOX     — semi-transparent filled bounding box (QUADS pipeline)
 *   SHAPED  — full entity-model overlay; rendering is done inside
 *             [LivingEntityRendererMixin]. This renderer only clears the
 *             [EspShapedStates] tracker for the next frame.
 *
 * depthCheck = false:
 *   Single pass with ESP / ESP_FILLED (NO_DEPTH_TEST). ESP always visible
 *   through walls, full opacity (wireframe: alpha 255, filled: alpha 80).
 *
 * depthCheck = true:
 *   Single pass with ESP_DEPTH / ESP_FILLED_DEPTH (LEQUAL_DEPTH_TEST),
 *   alpha 255. Renders only where geometry passes the depth test — portions
 *   of the entity hidden behind walls are not drawn.
 */
object EspRenderer {

    fun render(context: WorldRenderContext, config: HighlightConfig) {
        // Always clear shaped states from the previous frame (must happen after entity
        // rendering, which is complete by the time END_MAIN fires).
        EspShapedStates.clear()

        val mode = config.mode
        if (mode == EspMode.SHAPED) return  // rendering done by LivingEntityRendererMixin

        val targets = EspManager.targets.values.filter { !it.isRemoved }
        if (targets.isEmpty()) return

        val consumers = context.consumers() ?: return
        val matrices  = context.matrices()
        val camPos    = context.gameRenderer().getCamera().getCameraPos()
        val (r, g, b) = config.parsedColor()

        matrices.push()
        matrices.translate(-camPos.x, -camPos.y, -camPos.z)
        val matrix = matrices.peek().positionMatrix

        when (mode) {
            EspMode.OUTLINE -> renderOutline(consumers, matrix, targets, r, g, b, config.depthCheck)
            EspMode.BOX     -> renderBox(consumers, matrix, targets, r, g, b, config.depthCheck)
            EspMode.SHAPED  -> { /* unreachable — handled above */ }
        }

        matrices.pop()
    }

    // -------------------------------------------------------------------------
    // Mode dispatch
    // -------------------------------------------------------------------------

    private fun renderOutline(
        consumers: VertexConsumerProvider,
        matrix: Matrix4f,
        targets: List<Entity>,
        r: Int, g: Int, b: Int,
        depthCheck: Boolean
    ) {
        if (!depthCheck) {
            // Single pass — always visible through walls.
            val consumer = consumers.getBuffer(EspLayers.ESP)
            for (entity in targets) drawWireframe(consumer, matrix, entity.boundingBox, r, g, b, 255)
            if (consumers is VertexConsumerProvider.Immediate) consumers.draw(EspLayers.ESP)
        } else {
            // Pass 2 — bright visible overlay (LEQUAL_DEPTH_TEST, 100% alpha).
            val consumerDepth = consumers.getBuffer(EspLayers.ESP_DEPTH)
            for (entity in targets) drawWireframe(consumerDepth, matrix, entity.boundingBox, r, g, b, 255)
            if (consumers is VertexConsumerProvider.Immediate) consumers.draw(EspLayers.ESP_DEPTH)
        }
    }

    private fun renderBox(
        consumers: VertexConsumerProvider,
        matrix: Matrix4f,
        targets: List<Entity>,
        r: Int, g: Int, b: Int,
        depthCheck: Boolean
    ) {
        if (!depthCheck) {
            // Single pass — always visible through walls.
            val consumer = consumers.getBuffer(EspLayers.ESP_FILLED)
            for (entity in targets) drawFilled(consumer, matrix, entity.boundingBox, r, g, b, 80)
            if (consumers is VertexConsumerProvider.Immediate) consumers.draw(EspLayers.ESP_FILLED)
        } else {
            // Pass 2 — bright visible overlay (LEQUAL_DEPTH_TEST, 100% alpha).
            val consumerDepth = consumers.getBuffer(EspLayers.ESP_FILLED_DEPTH)
            for (entity in targets) drawFilled(consumerDepth, matrix, entity.boundingBox, r, g, b, 255)
            if (consumers is VertexConsumerProvider.Immediate) consumers.draw(EspLayers.ESP_FILLED_DEPTH)
        }
    }

    // -------------------------------------------------------------------------
    // OUTLINE helpers (LINES pipeline)
    // -------------------------------------------------------------------------

    private fun drawWireframe(
        consumer: VertexConsumer, matrix: Matrix4f, box: Box,
        r: Int, g: Int, b: Int, alpha: Int
    ) {
        val x1 = box.minX.toFloat(); val y1 = box.minY.toFloat(); val z1 = box.minZ.toFloat()
        val x2 = box.maxX.toFloat(); val y2 = box.maxY.toFloat(); val z2 = box.maxZ.toFloat()
        val lw = 2f
        // Bottom face
        line(consumer, matrix, x1,y1,z1, x2,y1,z1, r,g,b,alpha, lw)
        line(consumer, matrix, x2,y1,z1, x2,y1,z2, r,g,b,alpha, lw)
        line(consumer, matrix, x2,y1,z2, x1,y1,z2, r,g,b,alpha, lw)
        line(consumer, matrix, x1,y1,z2, x1,y1,z1, r,g,b,alpha, lw)
        // Top face
        line(consumer, matrix, x1,y2,z1, x2,y2,z1, r,g,b,alpha, lw)
        line(consumer, matrix, x2,y2,z1, x2,y2,z2, r,g,b,alpha, lw)
        line(consumer, matrix, x2,y2,z2, x1,y2,z2, r,g,b,alpha, lw)
        line(consumer, matrix, x1,y2,z2, x1,y2,z1, r,g,b,alpha, lw)
        // Vertical edges
        line(consumer, matrix, x1,y1,z1, x1,y2,z1, r,g,b,alpha, lw)
        line(consumer, matrix, x2,y1,z1, x2,y2,z1, r,g,b,alpha, lw)
        line(consumer, matrix, x2,y1,z2, x2,y2,z2, r,g,b,alpha, lw)
        line(consumer, matrix, x1,y1,z2, x1,y2,z2, r,g,b,alpha, lw)
    }

    private fun line(
        consumer: VertexConsumer, matrix: Matrix4f,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        r: Int, g: Int, b: Int, alpha: Int, lineWidth: Float
    ) {
        val dx = x2 - x1; val dy = y2 - y1; val dz = z2 - z1
        val len = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        val nx = if (len > 0f) dx / len else 0f
        val ny = if (len > 0f) dy / len else 1f
        val nz = if (len > 0f) dz / len else 0f
        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, alpha).normal(nx, ny, nz).lineWidth(lineWidth)
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, alpha).normal(nx, ny, nz).lineWidth(lineWidth)
    }

    // -------------------------------------------------------------------------
    // BOX helpers (QUADS pipeline)
    // -------------------------------------------------------------------------

    private fun drawFilled(
        consumer: VertexConsumer, matrix: Matrix4f, box: Box,
        r: Int, g: Int, b: Int, alpha: Int
    ) {
        val x1 = box.minX.toFloat(); val y1 = box.minY.toFloat(); val z1 = box.minZ.toFloat()
        val x2 = box.maxX.toFloat(); val y2 = box.maxY.toFloat(); val z2 = box.maxZ.toFloat()
        quad(consumer, matrix, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, r,g,b,alpha) // bottom
        quad(consumer, matrix, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1, r,g,b,alpha) // top
        quad(consumer, matrix, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1, r,g,b,alpha) // front
        quad(consumer, matrix, x2,y1,z2, x2,y2,z2, x1,y2,z2, x1,y1,z2, r,g,b,alpha) // back
        quad(consumer, matrix, x1,y1,z2, x1,y2,z2, x1,y2,z1, x1,y1,z1, r,g,b,alpha) // left
        quad(consumer, matrix, x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2, r,g,b,alpha) // right
    }

    private fun quad(
        consumer: VertexConsumer, matrix: Matrix4f,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        r: Int, g: Int, b: Int, a: Int
    ) {
        consumer.vertex(matrix, ax, ay, az).color(r, g, b, a)
        consumer.vertex(matrix, bx, by, bz).color(r, g, b, a)
        consumer.vertex(matrix, cx, cy, cz).color(r, g, b, a)
        consumer.vertex(matrix, dx, dy, dz).color(r, g, b, a)
    }
}
