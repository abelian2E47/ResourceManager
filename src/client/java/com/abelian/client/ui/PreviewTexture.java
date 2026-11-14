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
 *
 * <p>Ownership of the decoded {@link NativeImage} is the one subtle part of this class. A
 * {@code DynamicTexture} takes the image it is handed (its constructor stores it and uploads it, and
 * {@code setPixels} closes the image it replaces), so the image must NOT be closed by the caller after a
 * successful upload: the texture keeps the reference and frees it in {@code DynamicTexture#close()}. The
 * leak that used to be here came from the other direction — a failed upload dropped the texture without
 * closing it — and from never releasing the texture when the screen closed, which
 * {@link #release()} now does.
 */
public final class PreviewTexture {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("resourcemanager", "preview");

    private static final int MAX_SCALE = 6;

    private DynamicTexture texture;
    private int sourceWidth;
    private int sourceHeight;
    private String message = "";

    /**
     * Replaces the thumbnail with the pixels of one file; returns false when nothing could be read.
     *
     * <p>A false result always leaves the box in its empty state: the previous source dimensions are
     * cleared as well, so a caller that ignores the result cannot blit a stale image believing it is
     * the file it just asked for.
     */
    public boolean show(IoSupplier<InputStream> supplier) {
        this.message = "";
        this.sourceWidth = 0;
        this.sourceHeight = 0;
        NativeImage image = read(supplier);
        if (image == null) {
            this.message = "no preview";
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        try {
            if (this.texture != null && this.texture.getPixels() == null) {
                // The slot was released from under us (another preview, or a previous release call);
                // uploading into a closed texture would write into the default texture object.
                release();
            }
            NativeImage current = this.texture == null ? null : this.texture.getPixels();
            if (current == null || current.getWidth() != width || current.getHeight() != height) {
                // A DynamicTexture allocates its GL storage in the constructor; setPixels and upload only
                // write into storage that already exists, so a differently sized image would be uploaded
                // into the old allocation and leave the thumbnail stale. Rebuilding is the only correct
                // way to change the dimensions, and release() frees the old texture and its pixels first.
                release();
                DynamicTexture created = new DynamicTexture(image);
                created.setFilter(false, false);
                this.texture = created;
                Minecraft.getInstance().getTextureManager().register(ID, created);
            } else {
                this.texture.setPixels(image);
                this.texture.upload();
            }
        } catch (Exception error) {
            // The image is only owned by the texture once the constructor or setPixels has taken it;
            // NativeImage#close is idempotent, so closing it here is safe either way.
            image.close();
            release();
            this.message = "no preview";
            return false;
        }
        this.sourceWidth = width;
        this.sourceHeight = height;
        return true;
    }

    /**
     * Makes the thumbnail empty without giving up the texture, so the next selection can reuse it.
     */
    public void clear() {
        this.sourceWidth = 0;
        this.sourceHeight = 0;
        this.message = "";
    }

    /**
     * Frees the texture and the native pixels it owns, and forgets the cached texture manager entry.
     *
     * <p>Without this a screen that was closed keeps a GPU texture and its uncompressed image alive for
     * the rest of the session, because the texture manager only frees an entry when it is replaced or
     * when the whole manager shuts down. Safe to call twice and safe to call when nothing was ever
     * shown; call it from the screen's dispose.
     */
    public void release() {
        DynamicTexture current = this.texture;
        this.texture = null;
        this.sourceWidth = 0;
        this.sourceHeight = 0;
        this.message = "";
        if (current == null) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(ID);
        } catch (Exception error) {
            try {
                current.close();
            } catch (Exception ignored) {
                // The texture is already gone; there is nothing left to free.
            }
        }
    }

    public boolean hasImage() {
        return this.texture != null && this.texture.getPixels() != null
                && this.sourceWidth > 0 && this.sourceHeight > 0;
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

    /**
     * Draws the thumbnail centred in the box, scaling pixel art by whole factors whenever possible.
     *
     * <p>Kept for the existing callers; it falls back to the global font, which is exactly what
     * {@link #render(GuiGraphics, Ui.Rect, Font)} avoids. New code should pass the font it already has.
     */
    public void render(GuiGraphics graphics, Ui.Rect box) {
        Minecraft minecraft = Minecraft.getInstance();
        render(graphics, box, minecraft == null ? null : minecraft.font);
    }

    /**
     * Draws the thumbnail centred in the box, scaling pixel art by whole factors whenever possible.
     *
     * <p>Takes the font from the caller so a panel that already owns one never has to touch the global
     * state, and tolerates null by drawing no text at all instead of failing the frame.
     */
    public void render(GuiGraphics graphics, Ui.Rect box, Font font) {
        Ui.bevel(graphics, box, UiColors.SLOT, true);
        Font textFont = font != null ? font : fallbackFont();
        if (!hasImage()) {
            if (textFont != null) {
                String text = this.message.isEmpty() ? "no preview" : this.message;
                graphics.drawString(textFont, text, box.x() + (box.w() - textFont.width(text)) / 2,
                        box.y() + (box.h() - 8) / 2, UiColors.TEXT_MUTED, false);
            }
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
        graphics.blit(RenderType::guiTextured, ID, x, y, 0.0F, 0.0F, drawWidth, drawHeight,
                this.sourceWidth, this.sourceHeight, this.sourceWidth, this.sourceHeight);
    }

    private static Font fallbackFont() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.font;
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
