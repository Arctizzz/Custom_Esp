package org.arctizzz.arctizzz.client.esp

import net.minecraft.client.render.entity.state.EntityRenderState
import java.util.Collections

/**
 * Tracks which [EntityRenderState] objects belong to ESP-targeted entities for the
 * current frame's SHAPED-mode rendering.
 *
 * Lifecycle per frame:
 *  1. [EntityRenderManagerMixin] populates this set via [track] as render states are built.
 *  2. [LivingEntityRendererMixin] queries [isTracked] inside the entity render call to decide
 *     whether to submit a flat-colour overlay command.
 *  3. [EspRenderer.render] calls [clear] (runs after entity rendering, in END_MAIN) so the set
 *     is clean at the start of the next frame.
 *
 * We compare by object identity (IdentityHashMap) because render states are reused from a pool
 * and their equals/hashCode may not be meaningful between frames.
 */
object EspShapedStates {

    private val tracked: MutableSet<EntityRenderState> =
        Collections.newSetFromMap(java.util.IdentityHashMap())

    fun track(state: EntityRenderState) {
        tracked.add(state)
    }

    fun isTracked(state: EntityRenderState): Boolean = tracked.contains(state)

    fun clear() {
        tracked.clear()
    }
}
