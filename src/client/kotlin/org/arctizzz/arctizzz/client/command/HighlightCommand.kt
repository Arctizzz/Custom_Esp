package org.arctizzz.arctizzz.client.command

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.arctizzz.arctizzz.client.config.HighlightConfig
import org.arctizzz.arctizzz.client.esp.EspManager
import org.arctizzz.arctizzz.client.esp.EspMode
import org.arctizzz.arctizzz.client.gui.ColorPickerScreen

object HighlightCommand {

    fun register(config: HighlightConfig) {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("highlight")
                    // ----------------------------------------------------------
                    // /highlight add <filter>
                    // ----------------------------------------------------------
                    .then(literal("add")
                        .then(argument("filter", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val filter = StringArgumentType.getString(ctx, "filter")
                                if (config.filters.none { it.equals(filter, ignoreCase = true) }) {
                                    config.filters.add(filter)
                                    config.save()
                                    EspManager.clear()
                                    ctx.source.sendFeedback(Text.literal("§aAdded filter: §f\"$filter\""))
                                } else {
                                    ctx.source.sendFeedback(Text.literal("§eFilter already exists: §f\"$filter\""))
                                }
                                1
                            }))
                    // ----------------------------------------------------------
                    // /highlight remove <filter>
                    // ----------------------------------------------------------
                    .then(literal("remove")
                        .then(argument("filter", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val filter = StringArgumentType.getString(ctx, "filter")
                                val removed = config.filters.removeIf { it.equals(filter, ignoreCase = true) }
                                if (removed) {
                                    config.save()
                                    EspManager.clear()
                                    ctx.source.sendFeedback(Text.literal("§aRemoved filter: §f\"$filter\""))
                                } else {
                                    ctx.source.sendFeedback(Text.literal("§cFilter not found: §f\"$filter\""))
                                }
                                1
                            }))
                    // ----------------------------------------------------------
                    // /highlight list
                    // ----------------------------------------------------------
                    .then(literal("list")
                        .executes { ctx ->
                            if (config.filters.isEmpty()) {
                                ctx.source.sendFeedback(Text.literal("§eNo active filters."))
                            } else {
                                ctx.source.sendFeedback(Text.literal("§6Active filters §7(${config.filters.size}):"))
                                config.filters.forEachIndexed { i, f ->
                                    ctx.source.sendFeedback(Text.literal("§7  ${i + 1}. §f\"$f\""))
                                }
                            }
                            1
                        })
                    // ----------------------------------------------------------
                    // /highlight scandelay <ticks>
                    // ----------------------------------------------------------
                    .then(literal("scandelay")
                        .then(argument("ticks", IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                config.scanDelay = IntegerArgumentType.getInteger(ctx, "ticks")
                                config.save()
                                ctx.source.sendFeedback(
                                    Text.literal("§aScan delay set to §f${config.scanDelay} ticks §7(${config.scanDelay / 20.0}s)")
                                )
                                1
                            }))
                    // ----------------------------------------------------------
                    // /highlight color          → opens color picker GUI
                    // /highlight color <RRGGBB> → sets color directly
                    // ----------------------------------------------------------
                    .then(literal("color")
                        .executes { ctx ->
                            val mc = MinecraftClient.getInstance()
                            mc.execute { mc.setScreen(ColorPickerScreen(config, null)) }
                            ctx.source.sendFeedback(Text.literal("§7Opening color picker…"))
                            1
                        }
                        .then(argument("hex", StringArgumentType.string())
                            .executes { ctx ->
                                val input = StringArgumentType.getString(ctx, "hex").trimStart('#')
                                val valid = input.length == 6 &&
                                    input.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                                if (!valid) {
                                    ctx.source.sendFeedback(
                                        Text.literal("§cInvalid format. Use RRGGBB hex, e.g. §fFF0000§c for red.")
                                    )
                                } else {
                                    config.colorHex = input.uppercase()
                                    config.save()
                                    ctx.source.sendFeedback(Text.literal("§aColor set to §f#${config.colorHex}"))
                                }
                                1
                            }))
                    // ----------------------------------------------------------
                    // /highlight mode <outline|box|shaped>
                    // ----------------------------------------------------------
                    .then(literal("mode")
                        .then(literal("outline").executes { ctx ->
                            config.mode = EspMode.OUTLINE; config.save()
                            ctx.source.sendFeedback(Text.literal("§aESP mode → §fOUTLINE §7(wireframe box)"))
                            1
                        })
                        .then(literal("box").executes { ctx ->
                            config.mode = EspMode.BOX; config.save()
                            ctx.source.sendFeedback(Text.literal("§aESP mode → §fBOX §7(semi-transparent filled box)"))
                            1
                        })
                        .then(literal("shaped").executes { ctx ->
                            config.mode = EspMode.SHAPED; config.save()
                            ctx.source.sendFeedback(Text.literal("§aESP mode → §fSHAPED §7(full entity model overlay)"))
                            1
                        }))
                    // ----------------------------------------------------------
                    // /highlight depthcheck <true|false>
                    // ----------------------------------------------------------
                    .then(literal("depthcheck")
                        .then(argument("enabled", BoolArgumentType.bool())
                            .executes { ctx ->
                                config.depthCheck = BoolArgumentType.getBool(ctx, "enabled")
                                config.save()
                                val desc = if (config.depthCheck)
                                    "§aenabled §7(box only when visible)"
                                else
                                    "§cdisabled §7(true ESP, visible through walls)"
                                ctx.source.sendFeedback(Text.literal("§7Depth check $desc"))
                                1
                            }))
            )
        }
    }
}
