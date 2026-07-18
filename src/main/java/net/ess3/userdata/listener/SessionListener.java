package net.ess3.userdata.listener;

import net.ess3.userdata.storage.UserCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the offline name cache warm — expected Essentials-adjacent behavior.
 */
public final class SessionListener implements Listener {

    private final UserCache cache;

    public SessionListener(UserCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        cache.touch(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        cache.touch(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
