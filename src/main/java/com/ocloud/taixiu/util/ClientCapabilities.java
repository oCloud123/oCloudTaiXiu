package com.ocloud.taixiu.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ClientCapabilities {
    private ClientCapabilities() {}

    public static boolean supportsDialogs(Player player) {
        var via = Bukkit.getPluginManager().getPlugin("ViaVersion");
        if (via == null || !via.isEnabled()) return true;
        try {
            Class<?> entry = Class.forName("com.viaversion.viaversion.api.Via", true, via.getClass().getClassLoader());
            Object api = entry.getMethod("getAPI").invoke(null);
            Object result = BedrockPlatform.invoke(api, "getPlayerVersion", player.getUniqueId());
            return result instanceof Number version && version.intValue() >= 771;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {

            return false;
        }
    }
}
