package com.eymistaken.simplecps.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The game's text and item drawing calls, in one place.
 *
 * <p>These are the drawing methods whose names differ between versions ({@code text} is
 * {@code drawString} on 1.21.x, {@code item} is {@code renderItem}, and so on). They cannot
 * be renamed across the whole source, because {@code SettingsTheme.text} and
 * {@code EymHudFonts.text} are this mod's own methods with the same name, so every direct
 * call goes through here instead and only this file changes per version. Everything else on
 * {@link GuiGraphicsExtractor} ({@code fill}, {@code pose()} and the rest) is called directly.
 */
public final class Draw {

    private Draw() {}

    /** Text with the vanilla drop shadow. */
    public static void text(GuiGraphicsExtractor ctx, Font font, Component text, int x, int y, int color) {
        //? if >=26.1 {
        ctx.text(font, text, x, y, color);
        //?} else
        //ctx.drawString(font, text, x, y, color);
    }

    public static void text(GuiGraphicsExtractor ctx, Font font, Component text, int x, int y, int color,
                            boolean shadow) {
        //? if >=26.1 {
        ctx.text(font, text, x, y, color, shadow);
        //?} else
        //ctx.drawString(font, text, x, y, color, shadow);
    }

    /** Text centered on {@code centerX}, with the vanilla drop shadow. */
    public static void centeredText(GuiGraphicsExtractor ctx, Font font, Component text, int centerX, int y,
                                    int color) {
        //? if >=26.1 {
        ctx.centeredText(font, text, centerX, y, color);
        //?} else
        //ctx.drawCenteredString(font, text, centerX, y, color);
    }

    public static void item(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
        //? if >=26.1 {
        ctx.item(stack, x, y);
        //?} else
        //ctx.renderItem(stack, x, y);
    }

    /** The stack count and durability bar drawn over an item. */
    public static void itemDecorations(GuiGraphicsExtractor ctx, Font font, ItemStack stack, int x, int y) {
        //? if >=26.1 {
        ctx.itemDecorations(font, stack, x, y);
        //?} else
        //ctx.renderItemDecorations(font, stack, x, y);
    }
}
