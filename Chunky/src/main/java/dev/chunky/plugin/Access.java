package dev.chunky.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zugriffsschutz: Owner werden ausschließlich über ihre UUID (config.yml) erkannt.
 * Zusätzlich gilt die Permission "chunky.use" (z. B. von LuckPerms) – Standard: niemand, auch keine OPs.
 */
public final class Access {
    public static final String PERMISSION = "chunky.use";

    private final ChunkyPlugin plugin;
    private volatile Set<UUID> owners = Set.of();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    Access(ChunkyPlugin plugin) {
        this.plugin = plugin;
    }

    void reload() {
        Set<UUID> parsed = new HashSet<>();
        for (String raw : plugin.getConfig().getStringList("owner-uuids")) {
            try {
                parsed.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ungültige UUID in owner-uuids ignoriert: '" + raw
                        + "' (Format: 8-4-4-4-12 Hex-Zeichen mit Bindestrichen)");
            }
        }
        owners = Set.copyOf(parsed);
        if (owners.isEmpty()) {
            plugin.getLogger().warning("Keine gültige Owner-UUID konfiguriert – nur die Konsole kann Chunky-Befehle nutzen!");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            apply(p);
        }
    }

    public boolean isOwner(UUID uuid) {
        return owners.contains(uuid);
    }

    public boolean isAllowed(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (sender instanceof Player p) {
            return owners.contains(p.getUniqueId()) || p.hasPermission(PERMISSION);
        }
        return false;
    }

    /** Vergibt/entzieht die Permission "chunky.use" und aktualisiert die Befehlsliste des Clients. */
    void apply(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
                // war bereits entfernt
            }
        }
        if (owners.contains(p.getUniqueId())) {
            attachments.put(p.getUniqueId(), p.addAttachment(plugin, PERMISSION, true));
        }
        p.updateCommands();
    }

    void forget(Player p) {
        attachments.remove(p.getUniqueId());
    }
}
