package com.abelian.client;

import com.abelian.client.sound.SoundTuning;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.sound.sampled.*;

/** Auditions the selected pack's actual bytes, including a layer hidden by another pack. */
public final class PackSoundPreview {
    private static Clip clip;
    private static int generation;

    private PackSoundPreview() {}

    public static synchronized void stop() {
        generation++;
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    public static synchronized boolean isPlaying() {
        return clip != null && clip.isRunning();
    }

    public static void play(
            ResourceIndex.Snapshot snapshot,
            String pack,
            ResourceLocation location,
            SoundTuning tuning,
            Consumer<Component> status) {
        stop();
        final int token = generation;
        CompletableFuture.runAsync(
                () -> {
                    try {
                        var source = FilteredPackResources.raw(snapshot.packResources(pack));
                        var supplier = source.getResource(PackType.CLIENT_RESOURCES, location);
                        if (supplier == null) throw new java.io.IOException(location.toString());
                        try (var input = supplier.get();
                                var stream = new JOrbisAudioStream(input)) {
                            var buffer = stream.readAll();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            AudioFormat format = stream.getFormat();
                            AudioFormat pitched =
                                    new AudioFormat(
                                            format.getSampleRate() * tuning.pitch(),
                                            format.getSampleSizeInBits(),
                                            format.getChannels(),
                                            true,
                                            false);
                            synchronized (PackSoundPreview.class) {
                                if (token != generation) return;
                                Clip created = AudioSystem.getClip();
                                try {
                                    created.open(pitched, bytes, 0, bytes.length);
                                    if (created.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                                        FloatControl gain =
                                                (FloatControl)
                                                        created.getControl(
                                                                FloatControl.Type.MASTER_GAIN);
                                        float db =
                                                (float)
                                                        (20
                                                                * Math.log10(
                                                                        Math.max(
                                                                                0.0001,
                                                                                tuning.volume())));
                                        gain.setValue(
                                                Math.max(
                                                        gain.getMinimum(),
                                                        Math.min(gain.getMaximum(), db)));
                                    }
                                    clip = created;
                                    created.start();
                                } catch (Exception e) {
                                    created.close();
                                    throw e;
                                }
                            }
                            Minecraft.getInstance()
                                    .execute(
                                            () ->
                                                    status.accept(
                                                            Component.translatable(
                                                                    "resourcemanager.inventory.audio.playing",
                                                                    pack,
                                                                    location)));
                        }
                    } catch (Exception error) {
                        Minecraft.getInstance()
                                .execute(
                                        () ->
                                                status.accept(
                                                        Component.translatable(
                                                                "resourcemanager.inventory.audio.error",
                                                                error.getMessage())));
                    }
                });
    }
}
