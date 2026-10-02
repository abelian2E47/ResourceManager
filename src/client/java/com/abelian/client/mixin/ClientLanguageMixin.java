package com.abelian.client.mixin;

import com.abelian.client.ResourceManagerConfig;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Serves the translation strings edited in the GUI.
 *
 * <p>{@code Language.getOrDefault(String)} delegates to {@code getOrDefault(String, String)}, so hooking
 * the two-argument lookup of {@code ClientLanguage} covers every translation lookup of the client,
 * including the text that comes from the vanilla pack.
 */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"),
            cancellable = true)
    private void resourcemanager$textOverride(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String override = ResourceManagerConfig.instance().text(key);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Inject(method = "has", at = @At("HEAD"), cancellable = true)
    private void resourcemanager$hasOverride(String key, CallbackInfoReturnable<Boolean> cir) {
        if (ResourceManagerConfig.instance().text(key) != null) {
            cir.setReturnValue(true);
        }
    }
}
