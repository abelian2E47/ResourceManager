package com.abelian.client.sound;

/**
 * The two controls used by both game playback and pack previews.
 *
 * <p>The ranges are the ones the player can actually hear. Vanilla clamps what it plays
 * ({@code SoundEngine#calculatePitch}) to 0.5–2, so the pitch control stops there; the volume is a
 * multiplier applied before vanilla clamps the total to 1, so its top end only lifts events a pack
 * registered quieter than 1. Volume 0 is silence, which is what the mute button writes.
 */
public record SoundTuning(float volume, float pitch) {
    public static final SoundTuning DEFAULT = new SoundTuning(1, 1);
    /** Loudest multiplier on offer: an event a pack registers at 1/16 of full volume reaches 1. */
    public static final float MAX_VOLUME = 16;
    /** Exactly the range {@code SoundEngine#calculatePitch} lets through. */
    public static final float MIN_PITCH = 0.5f;
    public static final float MAX_PITCH = 2;

    public SoundTuning {
        volume = Float.isFinite(volume) ? Math.max(0, Math.min(MAX_VOLUME, volume)) : 1;
        pitch = Float.isFinite(pitch) ? Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch)) : 1;
    }

    public boolean isDefault() {
        return Math.abs(volume - 1) < 0.001f && Math.abs(pitch - 1) < 0.001f;
    }

    /** True when the event is silenced: the multiplier is 0, so vanilla skips the channel. */
    public boolean isMuted() {
        return volume <= 0;
    }

    public SoundTuning withVolume(float value) {
        return new SoundTuning(value, pitch);
    }

    public SoundTuning withPitch(float value) {
        return new SoundTuning(volume, value);
    }
}
