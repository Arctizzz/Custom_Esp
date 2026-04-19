package org.arctizzz.arctizzz.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import org.arctizzz.arctizzz.client.command.HighlightCommand
import org.arctizzz.arctizzz.client.config.HighlightConfig
import org.arctizzz.arctizzz.client.esp.EspLayers
import org.arctizzz.arctizzz.client.esp.EspManager
import org.arctizzz.arctizzz.client.esp.EspRenderer

class ArctizzzClient : ClientModInitializer {

    companion object {
        /** Exposed so mixins and screens can read the active config without DI. */
        @JvmField
        var activeConfig: HighlightConfig? = null
    }

    override fun onInitializeClient() {
        val config = HighlightConfig.load()
        activeConfig = config

        // Touch EspLayers early so pipelines register during mod init.
        EspLayers.init()

        HighlightCommand.register(config)

        ClientTickEvents.END_CLIENT_TICK.register {
            EspManager.tick(config)
        }

        // Immediately register/unregister armor stands as they enter/leave the world,
        // so ESP appears without waiting for the next scan cycle.
        ClientEntityEvents.ENTITY_LOAD.register { entity, _ ->
            EspManager.onEntityLoad(entity, config)
        }
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            EspManager.onEntityUnload(entity)
        }

        WorldRenderEvents.END_MAIN.register { context: WorldRenderContext ->
            EspRenderer.render(context, config)
        }
    }
}
