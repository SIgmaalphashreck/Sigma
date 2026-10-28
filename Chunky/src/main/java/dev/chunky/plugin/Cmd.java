package dev.chunky.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;

/** Gemeinsamer Executor/TabCompleter: prüft Zugriff bei Ausführung UND bei Tab-Vervollständigung. */
final class Cmd implements CommandExecutor, TabCompleter {
    @FunctionalInterface
    interface Run {
        void run(CommandSender sender, String label, String[] args) throws CmdException;
    }

    @FunctionalInterface
    interface Tab {
        List<String> complete(CommandSender sender, String[] args);
    }

    private final ChunkyPlugin plugin;
    private final String name;
    private final Run run;
    private final Tab tab;
    private final boolean open;

    Cmd(ChunkyPlugin plugin, String name, boolean open, Run run, Tab tab) {
        this.plugin = plugin;
        this.name = name;
        this.open = open;
        this.run = run;
        this.tab = tab;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!open && !plugin.access().isAllowed(sender)) {
            deny(sender, label);
            return true;
        }
        try {
            run.run(sender, label, args);
        } catch (CmdException ex) {
            Util.err(sender, ex.getMessage());
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei /" + name, ex);
            Util.err(sender, "Interner Fehler – Details stehen in der Serverkonsole.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!open && !plugin.access().isAllowed(sender)) {
            return List.of();
        }
        try {
            List<String> r = tab.complete(sender, args);
            return r == null ? List.of() : r;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Tab-Fehler bei /" + name, ex);
            return List.of();
        }
    }

    private void deny(CommandSender sender, String label) {
        Util.err(sender, "Du bist nicht als Owner eingetragen. /chunky uuid zeigt deine UUID.");
        if (sender instanceof Player p) {
            plugin.getLogger().warning("Zugriff verweigert: " + p.getName() + " (" + p.getUniqueId() + ") bei /" + label
                    + ". Falls das dein Account ist: UUID in config.yml unter owner-uuids eintragen und /chunky reload ausführen.");
        }
    }
}
