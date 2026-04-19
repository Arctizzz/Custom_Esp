package org.arctizzz.arctizzz.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.arctizzz.arctizzz.client.config.HighlightConfig

/**
 * Simple in-game RGB color picker opened by `/highlight color` (no args).
 *
 * Three sliders adjust R/G/B independently and sync the hex field.
 * The hex field is also editable; Apply reads it if it's a valid 6-char hex value,
 * otherwise it falls back to the slider values.
 */
class ColorPickerScreen(
    private val config: HighlightConfig,
    private val parent: Screen?
) : Screen(Text.literal("ESP Color Picker")) {

    private var r: Int = 0
    private var g: Int = 0
    private var b: Int = 0

    private var hexField: TextFieldWidget? = null

    override fun init() {
        val (pr, pg, pb) = config.parsedColor()
        r = pr; g = pg; b = pb

        val cx = width / 2
        val startY = height / 2 - 70

        addDrawableChild(channelSlider(cx - 75, startY,      "R", r) { v -> r = v; syncHex() })
        addDrawableChild(channelSlider(cx - 75, startY + 28, "G", g) { v -> g = v; syncHex() })
        addDrawableChild(channelSlider(cx - 75, startY + 56, "B", b) { v -> b = v; syncHex() })

        val field = TextFieldWidget(textRenderer, cx - 40, startY + 88, 80, 20, Text.literal("#RRGGBB"))
        field.setMaxLength(7)
        field.text = "%02X%02X%02X".format(r, g, b)
        hexField = addDrawableChild(field)

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Apply")) { applyColor() }
                .dimensions(cx - 52, startY + 118, 50, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Cancel")) { close() }
                .dimensions(cx + 2, startY + 118, 50, 20).build()
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        val cx = width / 2
        val swatchY = height / 2 - 70 + 144

        // Preview swatch
        context.fill(cx - 40, swatchY, cx + 40, swatchY + 20,
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Preview"), cx, swatchY - 10, 0xFFFFFF)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        // Enter / numpad-Enter → apply
        if (input.keycode == 257 || input.keycode == 335) {
            applyColor()
            return true
        }
        return super.keyPressed(input)
    }

    override fun shouldCloseOnEsc() = true

    override fun close() {
        client?.setScreen(parent)
    }

    // -------------------------------------------------------------------------

    private fun applyColor() {
        val raw = hexField?.text?.trimStart('#') ?: ""
        val isHex = raw.length == 6 && raw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        config.colorHex = if (isHex) raw.uppercase() else "%02X%02X%02X".format(r, g, b)
        config.save()
        close()
    }

    private fun syncHex() {
        hexField?.text = "%02X%02X%02X".format(r, g, b)
    }

    private fun channelSlider(x: Int, y: Int, label: String, initial: Int, onChange: (Int) -> Unit): SliderWidget {
        return object : SliderWidget(x, y, 150, 20, Text.empty(), initial / 255.0) {
            init { updateMessage() }

            override fun updateMessage() {
                message = Text.literal("$label: ${(value * 255).toInt().coerceIn(0, 255)}")
            }

            override fun applyValue() {
                onChange((value * 255).toInt().coerceIn(0, 255))
            }
        }
    }
}
