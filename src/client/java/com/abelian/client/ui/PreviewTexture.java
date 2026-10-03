package com.abelian.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Thumbnail of the texture belonging to the inspected file.
 *
 * <p>One dynamic texture is registered once and its pixels are swapped on every selection, so browsing a
 * pack never grows the texture atlas. The pixels come from the pack that owns the file, which means a file
 * shadowed by a higher priority pack still shows the art it actually contains.
 */
public final class PreviewTexture {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("resourcemanager", "preview");

    private static final int MAX_SCALE = 6;

    private DynamicTexture texture;
    private int sourceWidth;
    private int sourceHeight;
    private String message = "";

    /** Replaces the thumbnail with the pixels of one file; returns false when nothing could be read. */
    public boolean show(IoSupplier<InputStream> supplier) {
        this.message = "";
        this.sourceWidth = 0;
        this.sourceHeight = 0;
        NativeImage image = read(supplier);
        if (image == null) {
            this.message = "no preview";
            return false;
        }
        try {
            if (this.texture == null) {
                this.texture = new DynamicTexture(image);
                this.texture.setFilter(false, false);
                Minecraft.getInstance().getTextureManager().register(ID, this.texture);
            } else {
                this.texture.setPixels(image);
                this.texture.upload();
            }
        } catch (Exception error) {
            image.close();
            this.texture = null;
            this.message = "no preview";
            return false;
        }
        this.sourceWidth = image.getWidth();
        this.sourceHeight = image.getHeight();
        return true;
    }

    public void clear() {
        this.sourceWidth = 0;
        this.sourceHeight = 0;
        this.message = "";
    }

    public boolean hasImage() {
        return this.texture != null && this.sourceWidth > 0 && this.sourceHeight > 0;
    }

    public int sourceWidth() {
        return this.sourceWidth;
    }

    public int sourceHeight() {
        return this.sourceHeight;
    }

    public String message() {
        return this.message;
    }

    /** Draws the thumbnail centred in the box, scaling pixel art by whole factors whenever possible. */
    public void render(GuiGraphics graphics, Ui.Rect box) {
        Ui.panel(graphics, box, Ui.BODY, Ui.BORDER);
        Font font = Minecraft.getInstance().font;
        if (!hasImage()) {
            String text = this.message.isEmpty() ? "no preview" : this.message;
            graphics.drawString(font, text, box.x() + (box.w() - font.width(text)) / 2,
                    box.y() + (box.h() - 8) / 2, Ui.MUTED, false);
            return;
        }
        int innerWidth = Math.max(1, box.w() - 6);
        int innerHeight = Math.max(1, box.h() - 6);
        int drawWidth;
        int drawHeight;
        if (this.sourceWidth <= innerWidth && this.sourceHeight <= innerHeight) {
            int scale = Math.min(MAX_SCALE,
                    Math.min(innerWidth / this.sourceWidth, innerHeight / this.sourceHeight));
            scale = Math.max(1, scale);
            drawWidth = this.sourceWidth * scale;
            drawHeight = this.sourceHeight * scale;
        } else {
            float fit = Math.min((float) innerWidth / this.sourceWidth, (float) innerHeight / this.sourceHeight);
            drawWidth = Math.max(1, (int) (this.sourceWidth * fit));
            drawHeight = Math.max(1, (int) (this.sourceHeight * fit));
        }
        int x = box.x() + (box.w() - drawWidth) / 2;
        int y = box.y() + (box.h() - drawHeight) / 2;
        graphics.blit(ID, x, y, 0.0F, 0.0F, drawWidth, drawHeight,
                this.sourceWidth, this.sourceHeight);
    }

    private static NativeImage read(IoSupplier<InputStream> supplier) {
        if (supplier == null) {
            return null;
        }
        try (InputStream stream = supplier.get()) {
            return NativeImage.read(stream);
        } catch (Exception error) {
            return null;
        }
    }
}
