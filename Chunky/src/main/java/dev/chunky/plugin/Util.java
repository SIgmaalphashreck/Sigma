package dev.chunky.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

final class Util {
    private static final Component PREFIX = Component.text("[Chunky] ", NamedTextColor.DARK_GRAY);

    private static List<String> itemNames;
    private static List<String> blockNames;
    private static List<String> entityNames;

    private Util() {
    }

    // ---------- Nachrichten ----------
    static void ok(CommandSender s, String msg) {
        s.sendMessage(PREFIX.append(Component.text(msg, NamedTextColor.GREEN)));
    }

    static void info(CommandSender s, String msg) {
        s.sendMessage(PREFIX.append(Component.text(msg, NamedTextColor.GRAY)));
    }

    static void err(CommandSender s, String msg) {
        s.sendMessage(PREFIX.append(Component.text(msg, NamedTextColor.RED)));
    }

    static CmdException usage(String usage) {
        return new CmdException("Benutzung: " + usage);
    }

    // ---------- Argumente ----------
    static Player self(CommandSender s) throws CmdException {
        if (s instanceof Player p) {
            return p;
        }
        throw new CmdException("Dieser Befehl kann nur von einem Spieler ausgeführt werden.");
    }

    static Player player(String name) throws CmdException {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) {
            throw new CmdException("Spieler '" + name + "' ist nicht online.");
        }
        return p;
    }

    static Player targetOrSelf(CommandSender s, String[] a, int idx) throws CmdException {
        return a.length > idx ? player(a[idx]) : self(s);
    }

    static World world(String name) throws CmdException {
        World w = Bukkit.getWorld(name);
        if (w == null) {
            throw new CmdException("Welt '" + name + "' existiert nicht.");
        }
        return w;
    }

    static int parseInt(String in, String what, int min, int max) throws CmdException {
        long v = parseLong(in, what, min, max);
        return (int) v;
    }

    static long parseLong(String in, String what, long min, long max) throws CmdException {
        long v;
        try {
            v = Long.parseLong(in.trim());
        } catch (NumberFormatException ex) {
            throw new CmdException(what + " muss eine ganze Zahl sein (erhalten: '" + in + "').");
        }
        if (v < min || v > max) {
            throw new CmdException(what + " muss zwischen " + min + " und " + max + " liegen (erhalten: " + v + ").");
        }
        return v;
    }

    static boolean isNumber(String in) {
        try {
            Long.parseLong(in.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    static Material item(String in) throws CmdException {
        Material m = Material.matchMaterial(in.toLowerCase(Locale.ROOT));
        if (m == null || m.isLegacy() || m.isAir() || !m.isItem()) {
            throw new CmdException("Unbekanntes oder ungültiges Item: '" + in + "'.");
        }
        return m;
    }

    static Material itemFuzzy(String in) throws CmdException {
        String n = in.toLowerCase(Locale.ROOT);
        String[][] prefixes = {{"dia_", "diamond_"}, {"gold_", "golden_"}, {"wood_", "wooden_"},
                {"nether_", "netherite_"}, {"chain_", "chainmail_"}};
        for (String[] pr : prefixes) {
            if (n.startsWith(pr[0])) {
                n = pr[1] + n.substring(pr[0].length());
                break;
            }
        }
        return item(n);
    }

    static Material block(String in) throws CmdException {
        Material m = Material.matchMaterial(in.toLowerCase(Locale.ROOT));
        if (m == null || m.isLegacy() || m.isAir() || !m.isBlock()) {
            throw new CmdException("Unbekannter oder ungültiger Block: '" + in + "'.");
        }
        return m;
    }

    static String key(EntityType t) {
        return t.getKey().getKey();
    }

    static boolean spawnable(EntityType t, World w, Set<String> blocked) {
        return t.isSpawnable() && t.getEntityClass() != null && !blocked.contains(key(t));
    }

    static EntityType entity(String in, World w, Set<String> blocked, boolean livingOnly) throws CmdException {
        NamespacedKey k = NamespacedKey.fromString(in.toLowerCase(Locale.ROOT));
        EntityType t = k == null ? null : Registry.ENTITY_TYPE.get(k);
        if (t == null) {
            throw new CmdException("Unbekannter Mob/Entity-Typ: '" + in + "'.");
        }
        if (blocked.contains(key(t))) {
            throw new CmdException("Der Typ '" + key(t) + "' ist in der config.yml gesperrt.");
        }
        if (!spawnable(t, w, blocked)) {
            throw new CmdException("Der Typ '" + key(t) + "' kann nicht gespawnt werden.");
        }
        if (livingEntityRequired(livingOnly) && !org.bukkit.entity.LivingEntity.class.isAssignableFrom(t.getEntityClass())) {
            throw new CmdException("Der Typ '" + key(t) + "' ist keine Kreatur.");
        }
        return t;
    }

    private static boolean livingEntityRequired(boolean b) {
        return b;
    }

    // ---------- Tab-Vervollständigung ----------
    static List<String> filter(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }

    static List<String> players(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return filter(names, prefix);
    }

    static List<String> worlds(String prefix) {
        List<String> names = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            names.add(w.getName());
        }
        return filter(names, prefix);
    }

    static synchronized List<String> items(String prefix) {
        if (itemNames == null) {
            Set<String> s = new TreeSet<>();
            for (Material m : Material.values()) {
                if (!m.isLegacy() && !m.isAir() && m.isItem()) {
                    s.add(m.getKey().getKey());
                }
            }
            itemNames = List.copyOf(s);
        }
        return filter(itemNames, prefix);
    }

    static synchronized List<String> blocks(String prefix) {
        if (blockNames == null) {
            Set<String> s = new TreeSet<>();
            for (Material m : Material.values()) {
                if (!m.isLegacy() && !m.isAir() && m.isBlock()) {
                    s.add(m.getKey().getKey());
                }
            }
            blockNames = List.copyOf(s);
        }
        return filter(blockNames, prefix);
    }

    static synchronized List<String> entities(String prefix, Set<String> blocked, World w, boolean livingOnly) {
        if (entityNames == null) {
            Set<String> s = new TreeSet<>();
            for (EntityType t : Registry.ENTITY_TYPE) {
                if (t.isSpawnable() && t.getEntityClass() != null) {
                    s.add(key(t));
                }
            }
            entityNames = List.copyOf(s);
        }
        List<String> out = new ArrayList<>();
        for (String n : filter(entityNames, prefix)) {
            EntityType t = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(n));
            if (t != null && spawnable(t, w, blocked)
                    && (!livingOnly || org.bukkit.entity.LivingEntity.class.isAssignableFrom(t.getEntityClass()))) {
                out.add(n);
            }
        }
        return out;
    }
}
