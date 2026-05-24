package net.kgomc.zelda.messaging.channel;

import net.kyori.adventure.sound.Sound;

/**
 * Payload for {@link SendChannel#SOUND}.
 *
 * <pre>{@code
 * SoundData.of("entity.experience_orb.pickup")
 * SoundData.of("block.note_block.pling", Sound.Source.MASTER, 1.0f, 1.5f)
 * }</pre>
 */
public record SoundData(
        String key,
        Sound.Source source,
        float volume,
        float pitch
) {
    public static SoundData of(String key) {
        return new SoundData(key, Sound.Source.MASTER, 1.0f, 1.0f);
    }

    public static SoundData of(String key, Sound.Source source, float volume, float pitch) {
        return new SoundData(key, source, volume, pitch);
    }
}