package com.abelian.client.sound;

/** The two controls used by both game playback and pack previews. */
public record SoundTuning(float volume, float pitch) {
    public static final SoundTuning DEFAULT = new SoundTuning(1, 1);

    public SoundTuning {
        volume = Float.isFinite(volume) ? Math.max(0, Math.min(8, volume)) : 1;
        pitch = Float.isFinite(pitch) ? Math.max(0.25f, Math.min(4, pitch)) : 1;
    }

    public boolean isDefault() {
        return Math.abs(volume - 1) < 0.001f && Math.abs(pitch - 1) < 0.001f;
    }

    public SoundTuning withVolume(float value) {
        return new SoundTuning(value, pitch);
    }

    public SoundTuning withPitch(float value) {
        return new SoundTuning(volume, value);
    }
}
