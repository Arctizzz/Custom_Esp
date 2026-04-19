package org.arctizzz.arctizzz.client.esp

import net.minecraft.client.render.VertexConsumer

/**
 * Adapts the entity vertex format
 * [POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL] down to [POSITION_COLOR].
 *
 * ModelPart.renderCuboids emits vertices in the chain:
 *   vertex(x,y,z) → color(packed) → texture(u,v) → overlay(oi,oj) → light(li,lj) → normal(nx,ny,nz)
 *
 * This wrapper:
 *  • forwards vertex(x,y,z) to [delegate] unchanged (coordinates are already camera-space)
 *  • overrides color() with the ESP color
 *  • silently drops texture / overlay / light / normal calls — POSITION_COLOR doesn't need them
 *
 * The delegate is expected to be backed by [EspLayers.ESP_FILLED] (POSITION_COLOR + QUADS).
 */
class EntityFlatColorConsumer(
    private val delegate: VertexConsumer,
    private val r: Int,
    private val g: Int,
    private val b: Int,
    private val a: Int
) : VertexConsumer {

    override fun vertex(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.vertex(x, y, z)
        return this
    }

    override fun color(r: Int, g: Int, b: Int, a: Int): VertexConsumer {
        delegate.color(this.r, this.g, this.b, this.a)
        return this
    }

    override fun color(color: Int): VertexConsumer {
        delegate.color(r, g, b, a)
        return this
    }

    // ---- fields not used by POSITION_COLOR — all no-ops ----

    override fun texture(u: Float, v: Float): VertexConsumer = this
    override fun overlay(u: Int, v: Int): VertexConsumer = this
    override fun light(u: Int, v: Int): VertexConsumer = this
    override fun normal(x: Float, y: Float, z: Float): VertexConsumer = this
    override fun lineWidth(lineWidth: Float): VertexConsumer = this
}
