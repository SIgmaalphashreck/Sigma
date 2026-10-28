package dev.chunky.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

final class Commands {
    private static final List<String> COUNTS = List.of("1", "5", "10", "20", "50", "100");
    private static final List<String> TIME_VALUES = List.of("day", "noon", "night", "midnight");

    private final ChunkyPlugin p;
    private final Map<String, Cmd> map = new LinkedHashMap<>();
    private final Map<UUID, Boolean> prevFlight = new ConcurrentHashMap<>();

    Commands(ChunkyPlugin plugin) {
        this.p = plugin;
    }

    private void add(String name, Cmd.Run run, Cmd.Tab tab) {
        map.put(name, new Cmd(p, name, false, run, tab));
    }

    private static List<String> none(CommandSender s, String[] a) {
        return List.of();
    }

    private static List<String> optPlayer(String[] a, int idx) {
        return a.length == idx + 1 ? Util.players(a[idx]) : List.of();
    }

    @SuppressWarnings("deprecation")
    void registerAll() {
        build();
        for (Map.Entry<String, Cmd> en : map.entrySet()) {
            PluginCommand pc = p.getCommand(en.getKey());
            if (pc == null) {
                p.getLogger().severe("Befehl '" + en.getKey() + "' fehlt in der plugin.yml!");
                continue;
            }
            pc.setExecutor(en.getValue());
            pc.setTabCompleter(en.getValue());
        }
        for (String declared : p.getDescription().getCommands().keySet()) {
            if (!map.containsKey(declared)) {
                p.getLogger().severe("Befehl '" + declared + "' steht in der plugin.yml, hat aber keinen Executor!");
            }
        }
    }

    private Set<String> blocked() {
        Set<String> s = new HashSet<>();
        for (String v : p.getConfig().getStringList("blocked-entities")) {
            s.add(v.toLowerCase(Locale.ROOT).replace("minecraft:", ""));
        }
        return s;
    }

    private void build() {
        // ---------- /chunky (offen: jeder darf /chunky uuid nutzen) ----------
        map.put("chunky", new Cmd(p, "chunky", true, (s, l, a) -> {
            String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "uuid" -> {
                    Player pl = Util.self(s);
                    Util.info(pl, "Name: " + pl.getName());
                    Util.info(pl, "UUID: " + pl.getUniqueId());
                    Util.info(pl, "Server online-mode: " + Bukkit.getOnlineMode()
                            + (Bukkit.getOnlineMode() ? "" : " (Offline-UUID!)"));
                    Util.info(pl, "Owner laut config: " + (p.access().isOwner(pl.getUniqueId()) ? "ja" : "nein"));
                }
                case "reload" -> {
                    if (!p.access().isAllowed(s)) {
                        throw new CmdException("Du bist nicht als Owner eingetragen.");
                    }
                    p.reloadAll();
                    Util.ok(s, "Konfiguration neu geladen.");
                }
                default -> {
                    Util.info(s, "Chunky " + p.getPluginMeta().getVersion() + " – /chunky uuid zeigt deine UUID.");
                    if (p.access().isAllowed(s)) {
                        Util.info(s, "Befehle: /chunky reload, " + String.join(", ", map.keySet().stream()
                                .filter(n -> !n.equals("chunky")).map(n -> "/" + n).toList()));
                    }
                }
            }
        }, (s, a) -> {
            if (a.length != 1) {
                return List.of();
            }
            return Util.filter(p.access().isAllowed(s) ? List.of("uuid", "reload", "help") : List.of("uuid"), a[0]);
        }));

        // ---------- Inventar / Spieler ----------
        add("invsee", (s, l, a) -> {
            Player v = Util.self(s);
            if (a.length < 1) throw Util.usage("/invsee <Spieler>");
            Player t = Util.player(a[0]);
            if (t.equals(v)) throw new CmdException("Du kannst dein eigenes Inventar nicht per /invsee öffnen.");
            p.invsee().open(v, t);
            Util.info(v, "Inventar von " + t.getName() + " (Änderungen werden live ins echte Inventar übernommen).");
        }, (s, a) -> optPlayer(a, 0));

        add("ecsee", (s, l, a) -> {
            Player v = Util.self(s);
            Player t = a.length > 0 ? Util.player(a[0]) : v;
            v.openInventory(t.getEnderChest());
        }, (s, a) -> optPlayer(a, 0));

        add("locate", (s, l, a) -> {
            if (a.length < 1 || a.length > 2) throw Util.usage("/locate <Spieler> [tp]");
            Player t = Util.player(a[0]);
            Location tl = t.getLocation();
            String msg = t.getName() + ": Welt " + tl.getWorld().getName() + ", X " + tl.getBlockX()
                    + " Y " + tl.getBlockY() + " Z " + tl.getBlockZ();
            if (s instanceof Player pl) {
                if (pl.getWorld().equals(tl.getWorld())) {
                    msg += String.format(Locale.ROOT, " – Entfernung: %.1f Blöcke", pl.getLocation().distance(tl));
                } else {
                    msg += " – (andere Welt als du, keine Entfernung)";
                }
            }
            Util.ok(s, msg);
            if (a.length == 2) {
                if (!a[1].equalsIgnoreCase("tp")) throw Util.usage("/locate <Spieler> [tp]");
                Player pl = Util.self(s);
                if (!pl.teleport(tl)) throw new CmdException("Teleport fehlgeschlagen (von einem anderen Plugin abgebrochen?).");
                Util.ok(s, "Zu " + t.getName() + " teleportiert.");
            }
        }, (s, a) -> a.length == 1 ? Util.players(a[0]) : a.length == 2 ? Util.filter(List.of("tp"), a[1]) : List.of());

        add("heal", (s, l, a) -> {
            Player t = Util.targetOrSelf(s, a, 0);
            AttributeInstance max = t.getAttribute(Attribute.MAX_HEALTH);
            t.setHealth(max != null ? max.getValue() : 20.0);
            t.setFoodLevel(20);
            t.setSaturation(20f);
            t.setExhaustion(0f);
            t.setFireTicks(0);
            Util.ok(s, t.getName() + " wurde geheilt (Gesundheit + Hunger).");
        }, (s, a) -> optPlayer(a, 0));

        add("feed", (s, l, a) -> {
            Player t = Util.targetOrSelf(s, a, 0);
            t.setFoodLevel(20);
            t.setSaturation(20f);
            Util.ok(s, t.getName() + " ist satt.");
        }, (s, a) -> optPlayer(a, 0));

        add("give", (s, l, a) -> {
            if (a.length < 2 || a.length > 3) throw Util.usage("/give <Item> <Anzahl> [Spieler]");
            Material m = Util.item(a[0]);
            long maxAmt = p.getConfig().getLong("limits.give-max-amount", 10000);
            long amount = Util.parseLong(a[1], "Anzahl", 1, maxAmt);
            Player t = Util.targetOrSelf(s, a, 2);
            int maxDrop = p.getConfig().getInt("limits.give-max-dropped-stacks", 64);
            int stackSize = Math.max(1, m.getMaxStackSize());
            long remaining = amount;
            long inInv = 0, dropped = 0, refused = 0;
            int droppedStacks = 0;
            while (remaining > 0) {
                int n = (int) Math.min(stackSize, remaining);
                Map<Integer, ItemStack> left = t.getInventory().addItem(new ItemStack(m, n));
                int notAdded = 0;
                for (ItemStack it : left.values()) notAdded += it.getAmount();
                inInv += n - notAdded;
                if (notAdded > 0) {
                    if (droppedStacks < maxDrop) {
                        t.getWorld().dropItemNaturally(t.getLocation(), new ItemStack(m, notAdded));
                        dropped += notAdded;
                        droppedStacks++;
                    } else {
                        refused = notAdded + (remaining - n);
                        break;
                    }
                }
                remaining -= n;
            }
            String key = m.getKey().getKey();
            String msg = amount + "x " + key + " für " + t.getName() + ": " + inInv + " ins Inventar";
            if (dropped > 0) msg += ", " + dropped + " auf den Boden gedroppt (Inventar voll)";
            if (refused > 0) msg += ", " + refused + " NICHT gegeben (Drop-Limit " + maxDrop + " Stacks erreicht)";
            if (refused > 0) Util.err(s, msg); else Util.ok(s, msg);
        }, (s, a) -> switch (a.length) {
            case 1 -> Util.items(a[0]);
            case 2 -> Util.filter(COUNTS, a[1]);
            case 3 -> Util.players(a[2]);
            default -> List.of();
        });

        // ---------- Mobs ----------
        add("army", (s, l, a) -> {
            if (a.length != 3) throw Util.usage("/army <Mob> <Anzahl> <Spieler>");
            Player target = Util.player(a[2]);
            EntityType type = Util.entity(a[0], target.getWorld(), blocked(), true);
            int count = Util.parseInt(a[1], "Anzahl", 1, p.getConfig().getInt("limits.army-max", 100));
            spawnGroup(s, target, target, type, count, true);
        }, (s, a) -> switch (a.length) {
            case 1 -> Util.entities(a[0], blocked(), worldOf(s), true);
            case 2 -> Util.filter(COUNTS, a[1]);
            case 3 -> Util.players(a[2]);
            default -> List.of();
        });

        add("spawn", (s, l, a) -> {
            if (a.length < 1 || a.length > 3) throw Util.usage("/spawn <Mob> [Anzahl] [Spieler]");
            Player anchor = Util.targetOrSelf(s, a, 2);
            EntityType type = Util.entity(a[0], anchor.getWorld(), blocked(), false);
            int count = a.length > 1 ? Util.parseInt(a[1], "Anzahl", 1, p.getConfig().getInt("limits.spawn-max", 100)) : 1;
            spawnGroup(s, anchor, null, type, count, false);
        }, (s, a) -> switch (a.length) {
            case 1 -> Util.entities(a[0], blocked(), worldOf(s), false);
            case 2 -> Util.filter(COUNTS, a[1]);
            case 3 -> Util.players(a[2]);
            default -> List.of();
        });

        add("missile", (s, l, a) -> {
            if (a.length != 1) throw Util.usage("/missile <Spieler>");
            Player t = Util.player(a[0]);
            Location spot = findMissileSpot(t);
            if (spot == null) throw new CmdException("Kein sicherer Spawnplatz neben " + t.getName() + " gefunden.");
            int fuse = p.getConfig().getInt("missile.fuse-ticks", 30);
            int radius = p.getConfig().getInt("missile.explosion-radius", 3);
            int life = p.getConfig().getInt("missile.lifetime-seconds", 30);
            Creeper c;
            try {
                c = t.getWorld().spawn(spot, Creeper.class, cr -> {
                    cr.setInvisible(true);
                    cr.setMaxFuseTicks(Math.max(10, fuse));
                    cr.setExplosionRadius(Math.max(1, radius));
                    cr.setTarget(t);
                    cr.setPersistent(false);
                    cr.getPersistentDataContainer().set(p.missileKey(), PersistentDataType.BYTE, (byte) 1);
                });
            } catch (RuntimeException ex) {
                throw new CmdException("Creeper konnte nicht gespawnt werden: " + ex.getMessage());
            }
            Bukkit.getScheduler().runTaskLater(p, () -> {
                if (c.isValid()) c.remove();
            }, Math.max(1, life) * 20L);
            Util.ok(s, "Unsichtbarer Creeper ~6 Blöcke neben " + t.getName() + " gespawnt (wird nach " + life
                    + " s entfernt, falls er nicht explodiert).");
        }, (s, a) -> optPlayer(a, 0));

        // ---------- Blitz ----------
        add("bolt", (s, l, a) -> {
            if (a.length > 1) throw Util.usage("/bolt [Spieler]");
            Location loc;
            if (a.length == 1) {
                loc = Util.player(a[0]).getLocation();
            } else {
                Player pl = Util.self(s);
                Block b = pl.getTargetBlockExact(120);
                if (b == null) throw new CmdException("Kein Block im Blickfeld (max. 120 Blöcke).");
                loc = b.getLocation().add(0.5, 1, 0.5);
            }
            loc.getWorld().strikeLightning(loc);
            Util.ok(s, "Blitz ausgelöst.");
        }, (s, a) -> optPlayer(a, 0));

        add("lightningstick", (s, l, a) -> {
            Player pl = Util.self(s);
            ItemStack st = new ItemStack(Material.STICK);
            ItemMeta meta = st.getItemMeta();
            meta.displayName(Component.text("Blitzstab", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Rechtsklick: Blitz am anvisierten Block", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(p.stickKey(), PersistentDataType.BYTE, (byte) 1);
            st.setItemMeta(meta);
            Map<Integer, ItemStack> left = pl.getInventory().addItem(st);
            if (!left.isEmpty()) {
                pl.getWorld().dropItemNaturally(pl.getLocation(), left.values().iterator().next());
                Util.ok(s, "Inventar voll – Blitzstab wurde vor dir fallen gelassen.");
            } else {
                Util.ok(s, "Blitzstab erhalten.");
            }
        }, Commands::none);

        // ---------- Welt ----------
        add("sphere", (s, l, a) -> {
            if (a.length != 2) throw Util.usage("/sphere <Block> <Radius>");
            Player pl = Util.self(s);
            Material m = Util.block(a[0]);
            int cfgMax = Math.min(100, p.getConfig().getInt("limits.sphere-max-radius", 30));
            int r = Util.parseInt(a[1], "Radius", 1, cfgMax);
            int maxBlocks = p.getConfig().getInt("limits.sphere-max-blocks", 50000);
            List<int[]> offsets = new ArrayList<>();
            for (int x = -r; x <= r; x++)
                for (int y = -r; y <= r; y++)
                    for (int z = -r; z <= r; z++) {
                        double d = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
                        if (d <= r + 0.5 && d > r - 0.5) offsets.add(new int[]{x, y, z});
                    }
            if (offsets.size() > maxBlocks) {
                throw new CmdException("Die Kugel hätte " + offsets.size() + " Blöcke (Limit: " + maxBlocks + ").");
            }
            Location c = pl.getLocation();
            World w = c.getWorld();
            int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
            int perTick = Math.max(100, p.getConfig().getInt("limits.sphere-blocks-per-tick", 5000));
            boolean onlyReplaceable = p.getConfig().getBoolean("limits.sphere-only-replace-replaceable", true);
            UUID owner = pl.getUniqueId();
            Util.info(s, "Baue Kugel: " + offsets.size() + " Blöcke " + m.getKey().getKey() + ", Radius " + r + " …");
            new BukkitRunnable() {
                int idx = 0, placed = 0, skipped = 0;

                @Override
                public void run() {
                    int end = Math.min(idx + perTick, offsets.size());
                    for (; idx < end; idx++) {
                        int[] o = offsets.get(idx);
                        int y = cy + o[1];
                        if (y < w.getMinHeight() || y >= w.getMaxHeight()) {
                            skipped++;
                            continue;
                        }
                        Block b = w.getBlockAt(cx + o[0], y, cz + o[2]);
                        if (onlyReplaceable && !b.isReplaceable()) {
                            skipped++;
                            continue;
                        }
                        b.setType(m, false);
                        placed++;
                    }
                    if (idx >= offsets.size()) {
                        cancel();
                        Player o = Bukkit.getPlayer(owner);
                        String msg = "Kugel fertig: " + placed + " Blöcke gesetzt, " + skipped + " übersprungen"
                                + (onlyReplaceable ? " (nicht ersetzbare Blöcke bzw. außerhalb der Welthöhe)." : " (außerhalb der Welthöhe).");
                        if (o != null) Util.ok(o, msg);
                        p.getLogger().info("/sphere: " + msg);
                    }
                }
            }.runTaskTimer(p, 1L, 1L);
        }, (s, a) -> switch (a.length) {
            case 1 -> Util.blocks(a[0]);
            case 2 -> Util.filter(List.of("5", "10", "15", "20", "30"), a[1]);
            default -> List.of();
        });

        add("time", (s, l, a) -> {
            int i = (a.length > 0 && a[0].equalsIgnoreCase("set")) ? 1 : 0;
            if (a.length <= i || a.length > i + 2) throw Util.usage("/time [set] <day|noon|night|midnight|Ticks> [Welt]");
            long ticks = switch (a[i].toLowerCase(Locale.ROOT)) {
                case "day" -> 1000L;
                case "noon" -> 6000L;
                case "night" -> 13000L;
                case "midnight" -> 18000L;
                default -> Util.parseLong(a[i], "Ticks", 0, 24000);
            };
            World w = a.length > i + 1 ? Util.world(a[i + 1]) : (s instanceof Player pl ? pl.getWorld() : null);
            if (w == null) throw new CmdException("Als Konsole bitte eine Welt angeben: /time <Wert> <Welt>");
            w.setTime(ticks);
            Util.ok(s, "Zeit in '" + w.getName() + "' auf " + ticks + " Ticks gesetzt.");
        }, (s, a) -> {
            int i = (a.length > 0 && a[0].equalsIgnoreCase("set") && a.length > 1) ? 1 : 0;
            int pos = a.length - 1;
            if (pos == 0) {
                List<String> o = new ArrayList<>(TIME_VALUES);
                o.add("set");
                return Util.filter(o, a[0]);
            }
            if (pos == i) return Util.filter(TIME_VALUES, a[pos]);
            if (pos == i + 1) return Util.worlds(a[pos]);
            return List.of();
        });

        add("weather", (s, l, a) -> {
            if (a.length < 1 || a.length > 3) throw Util.usage("/weather <clear|rain|thunder> [Sekunden] [Welt]");
            String type = a[0].toLowerCase(Locale.ROOT);
            if (!List.of("clear", "rain", "thunder").contains(type)) throw Util.usage("/weather <clear|rain|thunder> [Sekunden] [Welt]");
            int secs = a.length > 1 ? Util.parseInt(a[1], "Sekunden", 1, 1_000_000) : 300;
            World w = a.length > 2 ? Util.world(a[2]) : (s instanceof Player pl ? pl.getWorld() : null);
            if (w == null) throw new CmdException("Als Konsole bitte eine Welt angeben.");
            int ticks = secs * 20;
            switch (type) {
                case "clear" -> {
                    w.setStorm(false);
                    w.setThundering(false);
                    w.setClearWeatherDuration(ticks);
                }
                case "rain" -> {
                    w.setStorm(true);
                    w.setThundering(false);
                    w.setWeatherDuration(ticks);
                }
                default -> {
                    w.setStorm(true);
                    w.setThundering(true);
                    w.setWeatherDuration(ticks);
                    w.setThunderDuration(ticks);
                }
            }
            Util.ok(s, "Wetter in '" + w.getName() + "': " + type + " für " + secs + " s.");
        }, (s, a) -> switch (a.length) {
            case 1 -> Util.filter(List.of("clear", "rain", "thunder"), a[0]);
            case 2 -> Util.filter(List.of("60", "300", "600"), a[1]);
            case 3 -> Util.worlds(a[2]);
            default -> List.of();
        });

        // ---------- Spielerzustand ----------
        add("vanish", (s, l, a) -> {
            Player pl = Util.self(s);
            UUID id = pl.getUniqueId();
            if (p.vanished().add(id)) {
                prevFlight.put(id, pl.getAllowFlight());
                pl.setAllowFlight(true);
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (!o.equals(pl) && !p.access().isAllowed(o)) {
                        o.hidePlayer(p, pl);
                        o.listPlayer(pl);
                    }
                }
                Util.ok(s, "Vanish an – unsichtbar, aber in der Tab-Liste sichtbar. Fliegen ist aktiv.");
            } else {
                p.vanished().remove(id);
                for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(p, pl);
                Boolean prev = prevFlight.remove(id);
                GameMode gm = pl.getGameMode();
                if (prev != null && !prev && (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE)) {
                    pl.setFlying(false);
                    pl.setAllowFlight(false);
                }
                Util.ok(s, "Vanish aus.");
            }
        }, Commands::none);

        add("god", (s, l, a) -> {
            Player t = Util.targetOrSelf(s, a, 0);
            if (p.god().add(t.getUniqueId())) Util.ok(s, "Gottmodus für " + t.getName() + " an (bis Logout/Neustart).");
            else {
                p.god().remove(t.getUniqueId());
                Util.ok(s, "Gottmodus für " + t.getName() + " aus.");
            }
        }, (s, a) -> optPlayer(a, 0));

        add("fly", (s, l, a) -> {
            Player t = Util.targetOrSelf(s, a, 0);
            boolean now = !t.getAllowFlight();
            t.setAllowFlight(now);
            if (!now) t.setFlying(false);
            Util.ok(s, "Fliegen für " + t.getName() + (now ? " an." : " aus."));
        }, (s, a) -> optPlayer(a, 0));

        add("speed", (s, l, a) -> {
            if (a.length < 1 || a.length > 2) throw Util.usage("/speed <0-10|reset> [Spieler]");
            Player t = Util.targetOrSelf(s, a, 1);
            if (a[0].equalsIgnoreCase("reset")) {
                t.setWalkSpeed(0.2f);
                t.setFlySpeed(0.1f);
                Util.ok(s, "Geschwindigkeit von " + t.getName() + " zurückgesetzt.");
                return;
            }
            double v;
            try {
                v = Double.parseDouble(a[0]);
            } catch (NumberFormatException ex) {
                throw new CmdException("Geschwindigkeit muss eine Zahl 0-10 oder 'reset' sein.");
            }
            if (Double.isNaN(v) || v < 0 || v > 10) throw new CmdException("Geschwindigkeit muss zwischen 0 und 10 liegen.");
            float f = (float) (v / 10.0);
            t.setWalkSpeed(f);
            t.setFlySpeed(f);
            Util.ok(s, "Geh- und Fluggeschwindigkeit von " + t.getName() + " auf " + v + " gesetzt (Standard: Gehen 2, Fliegen 1).");
        }, (s, a) -> switch (a.length) {
            case 1 -> Util.filter(List.of("reset", "1", "2", "5", "10"), a[0]);
            case 2 -> Util.players(a[1]);
            default -> List.of();
        });

        add("be", (s, l, a) -> {
            Player pl = Util.self(s);
            String usage = "/be <mob|item|block> <Name> [Items…]  |  /be <Name> [Items…]  |  /be off";
            if (a.length < 1) throw Util.usage(usage);
            if (a[0].equalsIgnoreCase("off")) {
                if (!p.be().isActive(pl)) throw new CmdException("Du bist gerade nichts (/be off nicht nötig).");
                p.be().end(pl);
                Util.ok(s, "Du bist wieder du. Dein Inventar wurde zurückgegeben.");
                return;
            }
            String kw = a[0].toLowerCase(Locale.ROOT);
            int i = 0;
            if (kw.equals("mob") || kw.equals("item") || kw.equals("block")) {
                i = 1;
            } else {
                kw = null;
            }
            if (a.length <= i) throw Util.usage(usage);
            BeManager.Form form = BeManager.resolve(kw, a[i]);
            if (a.length - i - 1 > 15) throw new CmdException("Maximal 15 Items.");
            List<ItemStack> items = new ArrayList<>();
            for (int j = i + 1; j < a.length; j++) {
                Material m = Util.itemFuzzy(a[j]);
                items.add(new ItemStack(m, Math.max(1, m.getMaxStackSize() > 1 ? m.getMaxStackSize() : 1)));
            }
            p.be().begin(pl, form, items);
            Util.ok(s, "Du bist jetzt: " + form.label() + (items.isEmpty() ? "" : " mit " + items.size() + " Item(s)")
                    + ". Bei Tod oder /be off kommst du zurück (Tod: zum Ort des letzten /be) und bekommst dein Inventar wieder.");
        }, (s, a) -> {
            List<String> kws = List.of("mob", "item", "block");
            if (a.length == 1) {
                List<String> o = new ArrayList<>(kws);
                o.add("off");
                o.addAll(Util.entities("", Set.of(), worldOf(s), true));
                return Util.filter(o, a[0]);
            }
            boolean kw = kws.contains(a[0].toLowerCase(Locale.ROOT));
            int nameIdx = kw ? 1 : 0;
            if (kw && a.length - 1 == nameIdx) {
                String t = a[0].toLowerCase(Locale.ROOT);
                return t.equals("mob") ? Util.entities(a[nameIdx], Set.of(), worldOf(s), true)
                        : t.equals("block") ? Util.blocks(a[nameIdx]) : Util.items(a[nameIdx]);
            }
            if (a[0].equalsIgnoreCase("off")) return List.of();
            return Util.items(a[a.length - 1]);
        });

        addGameMode("creative", GameMode.CREATIVE);
        addGameMode("survival", GameMode.SURVIVAL);
        addGameMode("spectator", GameMode.SPECTATOR);

        add("tp", (s, l, a) -> {
            switch (a.length) {
                case 1 -> {
                    Player pl = Util.self(s);
                    Player t = Util.player(a[0]);
                    teleport(pl, t.getLocation());
                    Util.ok(s, "Zu " + t.getName() + " teleportiert.");
                }
                case 2 -> {
                    Player who = Util.player(a[0]);
                    Player dest = Util.player(a[1]);
                    teleport(who, dest.getLocation());
                    Util.ok(s, who.getName() + " zu " + dest.getName() + " teleportiert.");
                }
                case 3 -> {
                    Player pl = Util.self(s);
                    teleport(pl, coords(pl.getLocation(), a[0], a[1], a[2]));
                    Util.ok(s, "Teleportiert.");
                }
                case 4 -> {
                    Player who = Util.player(a[0]);
                    teleport(who, coords(who.getLocation(), a[1], a[2], a[3]));
                    Util.ok(s, who.getName() + " teleportiert.");
                }
                default -> throw Util.usage("/tp <Spieler> | <Spieler> <Ziel> | <x> <y> <z> | <Spieler> <x> <y> <z>");
            }
        }, (s, a) -> a.length <= 2 ? Util.players(a[a.length - 1]) : List.of());
    }

    // ---------- Hilfsmethoden ----------
    private void addGameMode(String name, GameMode mode) {
        add(name, (s, l, a) -> {
            Player t = Util.targetOrSelf(s, a, 0);
            t.setGameMode(mode);
            Util.ok(s, t.getName() + " ist jetzt im " + mode.name().toLowerCase(Locale.ROOT) + "-Modus.");
        }, (s, a) -> optPlayer(a, 0));
    }

    private static World worldOf(CommandSender s) {
        return s instanceof Player pl ? pl.getWorld() : Bukkit.getWorlds().get(0);
    }

    private static void teleport(Player who, Location to) throws CmdException {
        if (!to.getWorld().getWorldBorder().isInside(to)) {
            throw new CmdException("Das Ziel liegt außerhalb der Weltgrenze.");
        }
        if (!who.teleport(to)) throw new CmdException("Teleport fehlgeschlagen (von einem anderen Plugin abgebrochen?).");
    }

    private static Location coords(Location base, String xs, String ys, String zs) throws CmdException {
        double x = coord(xs, base.getX());
        double y = coord(ys, base.getY());
        double z = coord(zs, base.getZ());
        World w = base.getWorld();
        if (y < w.getMinHeight() || y > w.getMaxHeight()) {
            throw new CmdException("Y muss zwischen " + w.getMinHeight() + " und " + w.getMaxHeight() + " liegen.");
        }
        return new Location(w, x, y, z, base.getYaw(), base.getPitch());
    }

    private static double coord(String in, double base) throws CmdException {
        try {
            double v;
            if (in.startsWith("~")) {
                v = base + (in.length() > 1 ? Double.parseDouble(in.substring(1)) : 0);
            } else {
                v = Double.parseDouble(in);
            }
            if (Double.isNaN(v) || Double.isInfinite(v) || Math.abs(v) > 30_000_000) {
                throw new CmdException("Ungültige Koordinate: " + in);
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new CmdException("Ungültige Koordinate: '" + in + "'.");
        }
    }

    private void spawnGroup(CommandSender s, Player anchor, Player victim, EntityType type, int count, boolean army) {
        World w = anchor.getWorld();
        int spawned = 0, enemies = 0, others = 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double ang = rnd.nextDouble(Math.PI * 2);
            double dist = 2.0 + rnd.nextDouble(3.0);
            Location loc = anchor.getLocation().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
            if (!loc.getBlock().isPassable() || !loc.clone().add(0, 1, 0).getBlock().isPassable()) {
                loc = anchor.getLocation();
            }
            try {
                Entity e = w.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                if (e == null) continue;
                spawned++;
                if (victim != null && e instanceof Mob mob) {
                    mob.setTarget(victim);
                    if (e instanceof Enemy) enemies++; else others++;
                }
            } catch (RuntimeException ex) {
                p.getLogger().warning("Spawn von " + Util.key(type) + " fehlgeschlagen: " + ex.getMessage());
            }
        }
        String name = Util.key(type);
        if (spawned == 0) {
            Util.err(s, "Es konnte kein '" + name + "' gespawnt werden (Spawn abgebrochen oder fehlgeschlagen).");
            return;
        }
        String msg = spawned + " von " + count + " x " + name + " gespawnt";
        if (army) {
            msg += " (Ziel: " + victim.getName() + "). Feindliche Mobs mit gesetztem Ziel: " + enemies
                    + ". Nicht-feindliche (z. B. Schafe, Axolotl): " + others
                    + " – sie haben keine Angriffs-KI gegen Spieler und verhalten sich normal.";
        } else {
            msg += " bei " + anchor.getName() + ".";
        }
        Util.ok(s, msg);
    }

    private Location findMissileSpot(Player t) {
        World w = t.getWorld();
        Location base = t.getLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] dys = {0, 1, -1, 2, -2};
        for (int attempt = 0; attempt < 16; attempt++) {
            double ang = rnd.nextDouble(Math.PI * 2);
            int x = (int) Math.floor(base.getX() + Math.cos(ang) * 6);
            int z = (int) Math.floor(base.getZ() + Math.sin(ang) * 6);
            for (int dy : dys) {
                Block feet = w.getBlockAt(x, base.getBlockY() + dy, z);
                if (feet.isPassable() && !feet.isLiquid()
                        && feet.getRelative(0, 1, 0).isPassable()
                        && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }
}
