package dev.chunky.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /be: Verkleidung als Mob, Block oder Item.
 * Der echte Spieler wird für andere versteckt (hidePlayer, bleibt aber in der Tab-Liste); ein Mob/Display-Entity
 * folgt ihm jeden Tick. Das Inventar wird gesichert (auch auf Platte) und ersetzt; beim Tod, /be off,
 * Logout oder Plugin-Stopp wird es zurückgegeben.
 */
final class BeManager {
    enum Kind { MOB, BLOCK, ITEM }

    record Form(Kind kind, EntityType type, Material material) {
        String label() {
            return switch (kind) {
                case MOB -> "Mob " + Util.key(type);
                case BLOCK -> "Block " + material.getKey().getKey();
                case ITEM -> "Item " + material.getKey().getKey();
            };
        }
    }

    static final class Session {
        UUID id;
        Form form;
        Entity disguise;
        Location origin;
        boolean dead;
        ItemStack[] storage;
        ItemStack[] armor;
        ItemStack offhand;
    }

    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private final ChunkyPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final File dir;
    private BukkitTask task;
    private long tick;

    BeManager(ChunkyPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "be-backup");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    // ---------- Auflösen ----------
    static Form resolve(String kw, String name) throws CmdException {
        String n = name.toLowerCase(Locale.ROOT);
        if (kw == null || kw.equals("mob")) {
            NamespacedKey k = NamespacedKey.fromString(n);
            EntityType t = k == null ? null : Registry.ENTITY_TYPE.get(k);
            if (t != null && t.isSpawnable() && t.getEntityClass() != null
                    && LivingEntity.class.isAssignableFrom(t.getEntityClass())) {
                return new Form(Kind.MOB, t, null);
            }
            if (kw != null) {
                throw new CmdException("Unbekannter Mob: '" + name + "'.");
            }
        }
        Material m = Material.matchMaterial(n);
        boolean valid = m != null && !m.isLegacy() && !m.isAir();
        if ((kw == null || kw.equals("block")) && valid && m.isBlock()) {
            return new Form(Kind.BLOCK, null, m);
        }
        if (kw != null && kw.equals("block")) {
            throw new CmdException("Unbekannter Block: '" + name + "'.");
        }
        if ((kw == null || kw.equals("item")) && valid && m.isItem()) {
            return new Form(Kind.ITEM, null, m);
        }
        throw new CmdException("'" + name + "' ist weder Mob, Block noch Item.");
    }

    // ---------- Zugriff ----------
    Session get(UUID id) {
        return sessions.get(id);
    }

    boolean isActive(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    // ---------- Tick ----------
    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            for (Session s : new ArrayList<>(sessions.values())) {
                Player p = Bukkit.getPlayer(s.id);
                if (p == null || s.dead) {
                    continue;
                }
                Entity d = s.disguise;
                if (d == null || !d.isValid()) {
                    try {
                        s.disguise = createDisguise(p, s.form);
                    } catch (RuntimeException | CmdException ex) {
                        continue;
                    }
                    d = s.disguise;
                } else {
                    d.teleport(p.getLocation());
                }
                if (tick % 10 == 0) {
                    mirror(p, d);
                }
            }
        }, 1L, 1L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Session s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.id);
            if (p != null) {
                restore(p, s, true);
            } else {
                removeDisguise(s);
            }
        }
        sessions.clear();
    }

    // ---------- Verkleidung ----------
    private Entity createDisguise(Player p, Form f) throws CmdException {
        Location loc = p.getLocation();
        World w = loc.getWorld();
        Entity e;
        if (f.kind() == Kind.MOB) {
            e = w.spawnEntity(loc, f.type(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        } else if (f.kind() == Kind.BLOCK) {
            e = w.spawn(loc, BlockDisplay.class, d -> {
                d.setBlock(f.material().createBlockData());
                d.setTransformation(new Transformation(new Vector3f(-0.5f, 0f, -0.5f), new AxisAngle4f(),
                        new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            });
        } else {
            e = w.spawn(loc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(f.material()));
                d.setTransformation(new Transformation(new Vector3f(0f, 0.9f, 0f), new AxisAngle4f(),
                        new Vector3f(1.5f, 1.5f, 1.5f), new AxisAngle4f()));
            });
        }
        if (e == null) {
            throw new CmdException("Das Spawnen der Verkleidung wurde abgebrochen (anderes Plugin?).");
        }
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setGravity(false);
        e.setPersistent(false);
        e.getPersistentDataContainer().set(plugin.beKey(), PersistentDataType.STRING, p.getUniqueId().toString());
        if (e instanceof LivingEntity le) {
            le.setAI(false);
            le.setCollidable(false);
            le.setCanPickupItems(false);
            EntityEquipment eq = le.getEquipment();
            if (eq != null) {
                for (EquipmentSlot slot : SLOTS) {
                    eq.setDropChance(slot, 0f);
                }
            }
        }
        if (e instanceof Mob m) {
            m.setTarget(null);
        }
        p.hideEntity(plugin, e);
        mirror(p, e);
        return e;
    }

    private void mirror(Player p, Entity d) {
        if (d instanceof LivingEntity le && le.getEquipment() != null) {
            EntityEquipment eq = le.getEquipment();
            PlayerInventory pi = p.getInventory();
            eq.setItemInMainHand(pi.getItemInMainHand());
            eq.setItemInOffHand(pi.getItemInOffHand());
            eq.setHelmet(pi.getHelmet());
            eq.setChestplate(pi.getChestplate());
            eq.setLeggings(pi.getLeggings());
            eq.setBoots(pi.getBoots());
        }
    }

    void removeDisguise(Session s) {
        if (s.disguise != null) {
            s.disguise.remove();
            s.disguise = null;
        }
    }

    // ---------- Start / Ende ----------
    void begin(Player p, Form f, List<ItemStack> items) throws CmdException {
        Session s = sessions.get(p.getUniqueId());
        boolean fresh = s == null;
        Entity nd;
        try {
            nd = createDisguise(p, f);
        } catch (RuntimeException ex) {
            throw new CmdException("Verkleidung konnte nicht erzeugt werden: " + ex.getMessage());
        }
        if (fresh) {
            s = new Session();
            s.id = p.getUniqueId();
            PlayerInventory pi = p.getInventory();
            s.storage = clone(pi.getStorageContents());
            s.armor = clone(pi.getArmorContents());
            s.offhand = pi.getItemInOffHand().clone();
            if (!saveBackup(s)) {
                nd.remove();
                throw new CmdException("Inventar-Sicherung konnte nicht geschrieben werden – abgebrochen, damit nichts verloren geht.");
            }
        }
        if (s.disguise != null) {
            s.disguise.remove();
        }
        s.form = f;
        s.origin = p.getLocation().clone();
        s.dead = false;
        s.disguise = nd;
        if (fresh) {
            sessions.put(s.id, s);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!o.equals(p)) {
                    o.hidePlayer(plugin, p);
                    o.listPlayer(p);
                }
            }
        }
        PlayerInventory pi = p.getInventory();
        pi.clear();
        equip(pi, items);
        pi.setHeldItemSlot(0);
        p.updateInventory();
        mirror(p, nd);
    }

    private static void equip(PlayerInventory pi, List<ItemStack> items) {
        int hotbar = 0;
        for (ItemStack it : items) {
            Material t = it.getType();
            String n = t.name();
            if ((n.endsWith("_HELMET") || n.endsWith("_HEAD") || n.endsWith("_SKULL") || t == Material.CARVED_PUMPKIN)
                    && pi.getHelmet() == null) {
                pi.setHelmet(it);
            } else if ((n.endsWith("_CHESTPLATE") || t == Material.ELYTRA) && pi.getChestplate() == null) {
                pi.setChestplate(it);
            } else if (n.endsWith("_LEGGINGS") && pi.getLeggings() == null) {
                pi.setLeggings(it);
            } else if (n.endsWith("_BOOTS") && pi.getBoots() == null) {
                pi.setBoots(it);
            } else if (t == Material.SHIELD && pi.getItemInOffHand().getType().isAir()) {
                pi.setItemInOffHand(it);
            } else if (hotbar < 9) {
                pi.setItem(hotbar++, it);
            } else {
                pi.addItem(it);
            }
        }
    }

    /** Beendet die Verkleidung und gibt das Inventar zurück. keepBackup=true: Sicherungsdatei bleibt (Logout/Stopp). */
    void restore(Player p, Session s, boolean keepBackup) {
        removeDisguise(s);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p)) {
                o.showPlayer(plugin, p);
            }
        }
        PlayerInventory pi = p.getInventory();
        pi.clear();
        pi.setStorageContents(s.storage);
        pi.setArmorContents(s.armor);
        pi.setItemInOffHand(s.offhand);
        p.updateInventory();
        sessions.remove(s.id);
        if (!keepBackup) {
            //noinspection ResultOfMethodCallIgnored
            backupFile(s.id).delete();
        }
    }

    void end(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            restore(p, s, false);
        }
    }

    void onQuit(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            restore(p, s, true);
        }
    }

    void onJoin(Player p) {
        File f = backupFile(p.getUniqueId());
        if (f.exists() && !sessions.containsKey(p.getUniqueId())) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
            if (c.contains("storage")) {
                PlayerInventory pi = p.getInventory();
                pi.clear();
                pi.setStorageContents(toArray(c.getList("storage"), 36));
                pi.setArmorContents(toArray(c.getList("armor"), 4));
                ItemStack off = c.getItemStack("offhand");
                pi.setItemInOffHand(off);
                p.updateInventory();
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                Util.info(p, "Dein Inventar aus der /be-Sicherung wurde wiederhergestellt.");
            }
        }
        for (Session s : sessions.values()) {
            Player sp = Bukkit.getPlayer(s.id);
            if (sp != null && !sp.equals(p)) {
                p.hidePlayer(plugin, sp);
                p.listPlayer(sp);
            }
        }
    }

    // ---------- Sicherung ----------
    private File backupFile(UUID id) {
        return new File(dir, id + ".yml");
    }

    private boolean saveBackup(Session s) {
        YamlConfiguration c = new YamlConfiguration();
        c.set("storage", Arrays.asList(s.storage));
        c.set("armor", Arrays.asList(s.armor));
        c.set("offhand", s.offhand);
        try {
            c.save(backupFile(s.id));
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Konnte /be-Sicherung nicht schreiben: " + ex.getMessage());
            return false;
        }
    }

    private static ItemStack[] clone(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] == null ? null : in[i].clone();
        }
        return out;
    }

    private static ItemStack[] toArray(List<?> list, int size) {
        ItemStack[] arr = new ItemStack[size];
        if (list != null) {
            for (int i = 0; i < Math.min(size, list.size()); i++) {
                if (list.get(i) instanceof ItemStack is) {
                    arr[i] = is;
                }
            }
        }
        return arr;
    }
}
