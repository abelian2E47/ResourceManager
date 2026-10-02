package com.abelian.client.mixin;

import com.abelian.client.ResourceManagerConfig;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Translated components cache the string they were built with and only rebuild it when the language
 * <em>instance</em> changes, so an edit made in the GUI would only show up on newly created components.
 * Dropping that cache whenever a text override changes makes edits visible immediately everywhere.
 */
@Mixin(TranslatableContents.class)
public abstract class TranslatableContentsMixin {
    @Shadow
    private Language decomposedWith;

    @Unique
    private int resourcemanager$textVersion = -1;

    @Inject(method = "decompose", at = @At("HEAD"))
    private void resourcemanager$invalidateCache(CallbackInfo ci) {
        int version = ResourceManagerConfig.instance().textVersion();
        if (this.resourcemanager$textVersion != version) {
            this.resourcemanager$textVersion = version;
            this.decomposedWith = null;
        }
    }
}
