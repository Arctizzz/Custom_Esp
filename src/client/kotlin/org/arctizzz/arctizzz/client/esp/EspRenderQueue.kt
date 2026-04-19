package org.arctizzz.arctizzz.client.esp

import net.minecraft.block.BlockState
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.model.Model
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.block.MovingBlockRenderState
import net.minecraft.client.render.command.ModelCommandRenderer
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.render.command.RenderCommandQueue
import net.minecraft.client.render.entity.state.EntityRenderState
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.client.render.model.BlockStateModel
import net.minecraft.client.render.state.CameraRenderState
import net.minecraft.client.texture.Sprite
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemDisplayContext
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import org.joml.Quaternionf

/**
 * An [OrderedRenderCommandQueue] used during SHAPED ESP feature rendering.
 *
 * When the feature renderers registered on a [LivingEntityRenderer] (e.g. armor,
 * cape, held items) submit geometry through this queue, all model and model-part
 * submissions are redirected to a flat-colour [EntityFlatColorConsumer] backed by
 * the ESP buffer — producing a solid-colour overlay that covers the entity's
 * equipment as well as its bare skin.
 *
 * All non-geometry submissions (labels, fire, leash, shadow, items, blocks) are
 * silently discarded so only actual 3-D surface geometry appears in the overlay.
 */
class EspRenderQueue(
    private val espBuffer: VertexConsumer,
    private val r: Int,
    private val g: Int,
    private val b: Int,
    private val a: Int
) : OrderedRenderCommandQueue {

    /** Returns a fresh [EntityFlatColorConsumer] wrapping the shared ESP buffer. */
    private fun consumer() = EntityFlatColorConsumer(espBuffer, r, g, b, a)

    // ---- geometry submissions — redirected to the ESP consumer ----

    @Suppress("UNCHECKED_CAST")
    override fun <S : Any> submitModel(
        model: Model<in S>,
        state: S,
        matrices: MatrixStack,
        layer: RenderLayer,
        light: Int,
        overlay: Int,
        color: Int,
        sprite: Sprite?,
        sortKey: Int,
        crumbling: ModelCommandRenderer.CrumblingOverlayCommand?
    ) {
        // Angles are already set by the feature renderer before submitModel is called.
        model.render(matrices, consumer(), light, overlay)
    }

    override fun submitModelPart(
        part: ModelPart,
        matrices: MatrixStack,
        layer: RenderLayer,
        light: Int,
        overlay: Int,
        sprite: Sprite?,
        leftHanded: Boolean,
        rightHanded: Boolean,
        color: Int,
        crumbling: ModelCommandRenderer.CrumblingOverlayCommand?,
        sortKey: Int
    ) {
        part.render(matrices, consumer(), light, overlay)
    }

    override fun submitCustom(
        matrices: MatrixStack,
        layer: RenderLayer,
        custom: OrderedRenderCommandQueue.Custom
    ) {
        // Execute the custom command immediately using the current matrix entry
        // and our ESP consumer instead of the requested layer's consumer.
        custom.render(matrices.peek(), consumer())
    }

    // ---- non-geometry submissions — all silently discarded ----

    override fun getBatchingQueue(sortKey: Int): RenderCommandQueue = this
    override fun submitCustom(custom: OrderedRenderCommandQueue.LayeredCustom) {}
    override fun submitShadowPieces(matrices: MatrixStack, opacity: Float, pieces: List<EntityRenderState.ShadowPiece>) {}
    override fun submitLabel(matrices: MatrixStack, nameLabelPos: Vec3d?, y: Int, label: Text, notSneaking: Boolean, light: Int, squaredDistancetoCamera: Double, cameraState: CameraRenderState) {}
    override fun submitText(matrices: MatrixStack, x: Float, y: Float, text: OrderedText, seeThrough: Boolean, layerType: TextRenderer.TextLayerType, p1: Int, p2: Int, p3: Int, p4: Int) {}
    override fun submitFire(matrices: MatrixStack, state: EntityRenderState, rotation: Quaternionf) {}
    override fun submitLeash(matrices: MatrixStack, leashData: EntityRenderState.LeashData) {}
    override fun submitBlock(matrices: MatrixStack, blockState: BlockState, light: Int, overlay: Int, color: Int) {}
    override fun submitMovingBlock(matrices: MatrixStack, movingBlock: MovingBlockRenderState) {}
    override fun submitBlockStateModel(matrices: MatrixStack, layer: RenderLayer, model: BlockStateModel, r: Float, g: Float, b: Float, light: Int, overlay: Int, color: Int) {}
    override fun submitItem(matrices: MatrixStack, context: ItemDisplayContext, light: Int, overlay: Int, color: Int, tints: IntArray?, quads: List<BakedQuad>, layer: RenderLayer, glint: ItemRenderState.Glint) {}
}
