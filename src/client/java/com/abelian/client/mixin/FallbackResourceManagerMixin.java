package com.abelian.client.mixin;

import com.abelian.ResourceManager;
import com.abelian.client.FilteredPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wraps every pack that enters the resource manager so the GUI can hide single files.
 *
 * <p>{@code FallbackResourceManager.pushInternal(name, resources, filter)} is the single funnel through
 * which all packs (built-in, mod and {@code resourcepacks/} folder or zip packs) are registered — both
 * {@code push} overloads and {@code pushFilterOnly} call it. Wrapping there covers every namespace
 * manager Minecraft builds during a resource reload.
 */
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    @ModifyVariable(
            method = "pushInternal(Ljava/lang/String;Lnet/minecraft/server/packs/PackResources;Ljava/util/function/Predicate;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2)
    private PackResources resourcemanager$filterDisabledResources(PackResources pack) {
        if (pack == null) {
            return null;
        }
        if (ResourceManager.LOGGER.isDebugEnabled()) {
            ResourceManager.LOGGER.debug("Applying resource filters to pack {}", pack.packId());
        }
        return FilteredPackResources.wrap(pack);
    }
}
