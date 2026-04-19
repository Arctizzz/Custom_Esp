package org.arctizzz.arctizzz.mixin.client

import net.minecraft.client.render.entity.EntityRenderManager
import net.minecraft.client.render.entity.state.EntityRenderState
import net.minecraft.entity.Entity
import org.arctizzz.arctizzz.client.ArctizzzClient
import org.arctizzz.arctizzz.client.esp.EspManager
import org.arctizzz.arctizzz.client.esp.EspMode
import org.arctizzz.arctizzz.client.esp.EspShapedStates
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Intercepts [EntityRenderManager.getAndUpdateRenderState] so that, in SHAPED mode,
 * we can tag the render state of any ESP-targeted entity before
 * [LivingEntityRendererMixin] renders it.
 */
@Mixin(EntityRenderManager::class)
class EntityRenderManagerMixin {

    @Inject(method = ["getAndUpdateRenderState"], at = [At("RETURN")])
    private fun <E : Entity> onGetAndUpdateRenderState(
        entity: E,
        tickDelta: Float,
        cir: CallbackInfoReturnable<EntityRenderState>
    ) {
        val config = ArctizzzClient.activeConfig ?: return
        if (config.mode != EspMode.SHAPED) return

        val isTarget = EspManager.isTarget(entity.uuid)
        if (!isTarget) return

        val state = cir.returnValue ?: return
        EspShapedStates.track(state)
    }
}
