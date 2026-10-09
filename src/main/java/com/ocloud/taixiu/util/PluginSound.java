package com.ocloud.taixiu.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class PluginSound {
    private PluginSound() {}

    public static void play(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline() && sound != null && volume > 0) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
