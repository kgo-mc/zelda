package net.kgomc.zelda.messaging.channel;

/**
 * Delivery channel for a message.
 */
public enum SendChannel {

    /** Standard chat message. Supported by all targets. */
    CHAT,

    /** Action bar (above hotbar). Player targets only. */
    ACTION_BAR,

    /**
     * Title + optional subtitle.
     * Use {@link TitleData} when sending via this channel.
     * Player targets only.
     */
    TITLE,

    /**
     * Boss bar across the top of the screen.
     * Use {@link BossBarData} when sending via this channel.
     * Player targets only.
     */
    BOSS_BAR,

    /**
     * Play a sound to the player.
     * Use {@link SoundData} when sending via this channel.
     * Player targets only.
     */
    SOUND
}