package org.arctizzz.arctizzz.mixin.client

import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.client.render.entity.feature.FeatureRenderer
import net.minecraft.client.render.entity.model.EntityModel
import net.minecraft.client.render.entity.state.LivingEntityRenderState
import net.minecraft.client.render.state.CameraRenderState
import net.minecraft.client.util.math.MatrixStack
import org.arctizzz.arctizzz.client.ArctizzzClient
import org.arctizzz.arctizzz.client.esp.EntityFlatColorConsumer
import org.arctizzz.arctizzz.client.esp.EspLayers
import org.arctizzz.arctizzz.client.esp.EspMode
import org.arctizzz.arctizzz.client.esp.EspRenderQueue
import org.arctizzz.arctizzz.client.esp.EspShapedStates
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * SHAPED mode rendering — injects into [LivingEntityRenderer.render] just before the
 * model matrix is popped, while all entity transforms (position, body-yaw, scale, etc.)
 * are still applied to the stack.
 *
 * Submits a custom render command via the [OrderedRenderCommandQueue].  When the
 * command executes it:
 *   1. Re-renders the entity's base model via [EntityFlatColorConsumer] — flat ESP colour.
 *   2. Calls each feature renderer (armor, cape, etc.) through [EspRenderQueue], which
 *      intercepts all geometry submissions and redirects them to the same flat ESP colour.
 *
 * This produces a solid-colour overlay covering both the mob's skin and any worn armor,
 * submitted to [EspLayers.ESP_FILLED] (through-walls) or [EspLayers.ESP_FILLED_DEPTH]
 * (depth-tested), depending on the active [depthCheck] config setting.
 */
@Mixin(LivingEntityRenderer::class)
abstract class LivingEntityRendererMixin {

    @Shadow
    abstract fun getModel(): EntityModel<*>

    @field:Shadow
    protected lateinit var features: List<FeatureRenderer<LivingEntityRenderState, *>>

    @Inject(
        method = ["render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;" +
                "Lnet/minecraft/client/util/math/MatrixStack;" +
                "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;" +
                "Lnet/minecraft/client/render/state/CameraRenderState;)V"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V")]
    )
    private fun onBeforeMatrixPop(
        renderState: LivingEntityRenderState,
        matrices: MatrixStack,
        renderQueue: OrderedRenderCommandQueue,
        cameraState: CameraRenderState,
        ci: CallbackInfo
    ) {
        if (!EspShapedStates.isTracked(renderState)) return
        val config = ArctizzzClient.activeConfig ?: return
        if (config.mode != EspMode.SHAPED) return

        val (r, g, b) = config.parsedColor()
        val alpha = 200

        val model = getModel()
        @Suppress("UNCHECKED_CAST")
        val typedModel = model as EntityModel<LivingEntityRenderState>

        // Set the animated pose for THIS entity (the model is shared per entity-type).
        typedModel.setAngles(renderState)

        // Capture locals for the deferred command.
        // Head rotation values are read now (while renderState is guaranteed valid)
        // and passed directly to feature renderers — vanilla passes these same two floats.
        val capturedModel    = typedModel
        val capturedState    = renderState
        val capturedFeatures = features  // List ref is stable; captured before deferral
        val headYaw          = renderState.relativeHeadYaw
        val headPitch        = renderState.pitch

        val layer = if (config.depthCheck) EspLayers.ESP_FILLED_DEPTH else EspLayers.ESP_FILLED
        renderQueue.submitCustom(matrices, layer,
            OrderedRenderCommandQueue.Custom { entry, consumer ->
                // Re-apply angles inside the deferred call so concurrent entities of the same
                // type (which share a model instance) each get their own correct pose.
                capturedModel.setAngles(capturedState)

                // Build a scratch MatrixStack seeded with the entity's snapshotted world matrix.
                val stack = MatrixStack()
                stack.peek().positionMatrix.set(entry.positionMatrix)

                // 1. Base model — entity skin / body geometry.
                capturedModel.render(stack, EntityFlatColorConsumer(consumer, r, g, b, alpha),
                    0xF000F0, 0, -1)

                // 2. Feature layers (armor, cape, held items, …).
                //    EspRenderQueue intercepts every submitModel / submitModelPart call the
                //    feature renderer makes and redirects it to our flat-colour consumer,
                //    so worn armor appears in the overlay at the same colour and opacity.
                //    headYaw / headPitch match what vanilla passes at the same call site.
                val espQueue = EspRenderQueue(consumer, r, g, b, alpha)
                @Suppress("UNCHECKED_CAST")
                for (feature in capturedFeatures as List<FeatureRenderer<LivingEntityRenderState, *>>) {
                    feature.render(stack, espQueue, 0xF000F0, capturedState, headYaw, headPitch)
                }
            })
    }
}
