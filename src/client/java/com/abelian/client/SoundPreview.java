package com.abelian.client;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * Turns a {@code sounds.json} event id into an audible preview.
 *
 * <p>The sound engine silently drops anything it cannot resolve, which is what makes a broken preview
 * look like a broken button: an event declared only in a pack whose {@code sounds.json} lost the merge
 * against a higher priority pack is unknown to the engine, and an event whose files are missing resolves
 * to {@link SoundManager#EMPTY_SOUND}. Both cases are reported instead of played silently.
 */
public final class SoundPreview {
    public enum Availability {
        /** The event is registered and has at least one usable sound file. */
        PLAYABLE("resourcemanager.ui.preview.playable"),
        /** No {@code sounds.json} in the loaded packs declares the event (it is overridden). */
        UNKNOWN("resourcemanager.ui.preview.unknown"),
        /** Declared, but every file it points at is missing. */
        EMPTY("resourcemanager.ui.preview.empty"),
        /** Declared as intentionally silent. */
        INTENTIONALLY_EMPTY("resourcemanager.ui.preview.intentionallyEmpty");

        private final String translationKey;

        Availability(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        public boolean isPlayable() {
            return this == PLAYABLE;
        }
    }

    private SoundPreview() {
    }

    public static Availability availability(SoundManager manager, Identifier id) {
        WeighedSoundEvents events = manager.getSoundEvent(id);
        if (events == null) {
            return Availability.UNKNOWN;
        }
        Sound sound = events.getSound(SoundInstance.createUnseededRandom());
        if (sound == SoundManager.EMPTY_SOUND) {
            return Availability.EMPTY;
        }
        if (sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
            return Availability.INTENTIONALLY_EMPTY;
        }
        return Availability.PLAYABLE;
    }

    /**
     * A relative, non attenuated instance: the preview is audible wherever the player stands and is not
     * muted by distance. Volume and pitch stay at 1 so the tuning mixin applies the configured factors.
     */
    public static SoundInstance create(Identifier id) {
        return new SimpleSoundInstance(id, SoundSource.MASTER, 1.0F, 1.0F, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
    }
}
