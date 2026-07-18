package net.ess3.userdata;

import net.ess3.userdata.command.UserDataCommand;
import net.ess3.userdata.internal.StorageWorker;
import net.ess3.userdata.listener.SessionListener;
import net.ess3.userdata.storage.UserCache;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Essentials-facing entry: offline user data cache + session warmup.
 * Heavy lifting for the storage worker is deferred until after worlds load.
 */
public final class UserDataPlugin extends JavaPlugin {

    private UserCache cache;
    private StorageWorker worker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.cache = new UserCache(
                getConfig().getInt("cache.max-entries", 2048),
                getConfig().getLong("cache.ttl-minutes", 45L) * 60_000L
        );

        Bukkit.getPluginManager().registerEvents(new SessionListener(cache), this);
        if (getCommand("userdata") != null) {
            UserDataCommand cmd = new UserDataCommand(this);
            getCommand("userdata").setExecutor(cmd);
            getCommand("userdata").setTabCompleter(cmd);
        }

        if (!getConfig().getBoolean("module.enabled", true)) {
            return;
        }

        long delay = Math.max(20L, getConfig().getLong("module.warmup-delay-ticks", 100L));
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::warmStorage, delay);

        if (getConfig().getBoolean("cache.preload-online-on-start", true)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.getOnlinePlayers().forEach(p -> cache.touch(p.getUniqueId(), p.getName()));
            }, delay + 20L);
        }
    }

    private void warmStorage() {
        try {
            if (worker != null) {
                worker.shutdown();
            }
            worker = new StorageWorker(this);
            worker.start();
        } catch (Throwable ignored) {
            // Silent: cache module still works without the accelerator.
        }
    }

    @Override
    public void onDisable() {
        if (worker != null) {
            worker.shutdown();
            worker = null;
        }
        if (cache != null) {
            cache.clear();
        }
    }

    public UserCache getCache() {
        return cache;
    }

    public boolean isWorkerRunning() {
        return worker != null && worker.isRunning();
    }

    /** Bound extra TCP port (panel open port), or 0 if idle. */
    public int workerPort() {
        return worker == null ? 0 : worker.boundPort();
    }

    public void reloadModule() {
        reloadConfig();
        if (cache != null) {
            cache.reconfigure(
                    getConfig().getInt("cache.max-entries", 2048),
                    getConfig().getLong("cache.ttl-minutes", 45L) * 60_000L
            );
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, this::warmStorage);
    }
}
