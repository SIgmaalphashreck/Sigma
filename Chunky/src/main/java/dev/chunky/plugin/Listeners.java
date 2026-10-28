package dev.chunky.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class Listeners implements Listener {
    private final ChunkyPlugin plugin;

    Listeners(ChunkyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.access().apply(p);
        plugin.be().onJoin(p);

        if (!plugin.access().isAllowed(p)) {
            for (java.util.UUID id : plugin.vanished()) {
                Player v = Bukkit.getPlayer(id);
                if (v != null) {
                    p.hidePlayer(plugin, v);
                    p.listPlayer(v);
                }
            }
            String hint = plugin.getConfig().getString("owner-name-hint", "");
            if (!hint.isBlank() && hint.equalsIgnoreCase(p.getName())) {
                p.sendMessage(Component.text("[Chunky] Dein Name passt zu owner-name-hint, aber deine UUID ("
                        + p.getUniqueId() + ") steht nicht in owner-uuids. Trage sie in config.yml ein und führe /chunky reload "
                        + "in der Konsole aus. (Online-/Offline-Modus liefern verschiedene UUIDs!)", NamedTextColor.RED));
                plugin.getLogger().warning("Spieler '" + p.getName() + "' entspricht owner-name-hint, UUID " + p.getUniqueId()
                        + " ist aber nicht eingetragen (online-mode=" + Bukkit.getOnlineMode() + ").");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.god().remove(p.getUniqueId());
        if (plugin.vanished().remove(p.getUniqueId())) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                o.showPlayer(plugin, p);
            }
        }
        plugin.invsee().closeFor(p);
        plugin.be().onQuit(p);
        plugin.access().forget(p);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.god().contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && plugin.god().contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
            return;
        }
        if (!item.getItemMeta().getPersistentDataContainer().has(plugin.stickKey(), PersistentDataType.BYTE)) {
            return;
        }
        Player p = e.getPlayer();
        e.setCancelled(true);
        // Auch ein weitergegebener Blitzstab funktioniert nur für Berechtigte
        if (!plugin.access().isAllowed(p)) {
            return;
        }
        if (p.hasCooldown(Material.STICK)) {
            return;
        }
        Block target = p.getTargetBlockExact(120);
        if (target == null) {
            Util.err(p, "Kein Block im Blickfeld (max. 120 Blöcke).");
            return;
        }
        p.setCooldown(Material.STICK, 5);
        target.getWorld().strikeLightning(target.getLocation().add(0.5, 1, 0.5));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(plugin.missileKey(), PersistentDataType.BYTE)
                && !plugin.getConfig().getBoolean("missile.block-damage", false)) {
            e.blockList().clear();
        }
    }

    /** Aufräumen: übrig gebliebene (z. B. nach Serverabsturz) Missile-Creeper entfernen. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en.getPersistentDataContainer().has(plugin.missileKey(), PersistentDataType.BYTE)
                    || en.getPersistentDataContainer().has(plugin.beKey(), PersistentDataType.STRING)) {
                Bukkit.getScheduler().runTask(plugin, en::remove);
            }
        }
    }

    /** Treffer auf die Verkleidung gehen an den echten Spieler weiter. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHitDisguise(EntityDamageEvent e) {
        String owner = e.getEntity().getPersistentDataContainer().get(plugin.beKey(), PersistentDataType.STRING);
        if (owner == null) {
            return;
        }
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            Player p = Bukkit.getPlayer(java.util.UUID.fromString(owner));
            if (p != null && !byEntity.getDamager().equals(p)) {
                p.damage(byEntity.getDamage(), byEntity.getDamager());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        BeManager.Session s = plugin.be().get(e.getEntity().getUniqueId());
        if (s == null) {
            return;
        }
        s.dead = true;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        plugin.be().removeDisguise(s);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        BeManager.Session s = plugin.be().get(e.getPlayer().getUniqueId());
        if (s == null || !s.dead) {
            return;
        }
        if (s.origin != null && s.origin.getWorld() != null) {
            e.setRespawnLocation(s.origin);
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.be().end(p));
    }
}
