package com.abelian.client.mixin;

import com.abelian.client.ResourceManagerConfig;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the volume/pitch tuning configured in the GUI.
 *
 * <p>{@code AbstractSoundInstance#getLocation()} is the id of the sound <em>event</em> (the key used in
 * {@code sounds.json}), which is exactly what the GUI tunes. Concrete instances such as
 * {@code SimpleSoundInstance} do not override these accessors, so this covers every played sound.
 */
@Mixin(AbstractSoundInstance.class)
public abstract class SoundInstanceMixin {
    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void resourcemanager$applyVolume(CallbackInfoReturnable<Float> cir) {
        ResourceManagerConfig.SoundTuning tuning = resourcemanager$tuning();
        if (!tuning.isDefault()) {
            cir.setReturnValue(cir.getReturnValue() * tuning.volume());
        }
    }

    @Inject(method = "getPitch", at = @At("RETURN"), cancellable = true)
    private void resourcemanager$applyPitch(CallbackInfoReturnable<Float> cir) {
        ResourceManagerConfig.SoundTuning tuning = resourcemanager$tuning();
        if (!tuning.isDefault()) {
            cir.setReturnValue(cir.getReturnValue() * tuning.pitch());
        }
    }

    private ResourceManagerConfig.SoundTuning resourcemanager$tuning() {
        AbstractSoundInstance sound = (AbstractSoundInstance) (Object) this;
        return ResourceManagerConfig.instance().sound(sound.getIdentifier().toString());
    }
}
