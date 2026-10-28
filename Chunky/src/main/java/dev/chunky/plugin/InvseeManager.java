package dev.chunky.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /invsee: Chest-GUI (54 Slots) als Live-Ansicht des Ziel-Inventars.
 * Layout: Slots 0-35 = Inventar (0-8 Hotbar), 36 Helm, 37 Brustplatte, 38 Hose, 39 Stiefel, 41 Offhand,
 * Rest = gesperrte Platzhalter.
 * Änderungen des Betrachters werden einen Tick nach dem Klick in das echte Inventar des Ziels geschrieben;
 * solange keine Änderung ansteht, wird die Ansicht alle 10 Ticks aus dem echten Inventar aktualisiert.
 */
final class InvseeManager implements Listener {
    private static final int SIZE = 54;
    private static final int HELMET = 36;
    private static final int CHEST = 37;
    private static final int LEGS = 38;
    private static final int BOOTS = 39;
    private static final int OFFHAND = 41;
    private static final Set<Integer> EDITABLE = new HashSet<>();

    static {
        for (int i = 0; i <= 39; i++) {
            EDITABLE.add(i);
        }
        EDITABLE.add(OFFHAND);
    }

    static final class Holder implements InventoryHolder {
        private Inventory inventory;
        final UUID target;
        boolean dirty;

        Holder(UUID target) {
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ChunkyPlugin plugin;
    private final List<Holder> sessions = new ArrayList<>();
    private BukkitTask refreshTask;

    InvseeManager(ChunkyPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Holder h : new ArrayList<>(sessions)) {
                Player t = Bukkit.getPlayer(h.target);
                if (t == null) {
                    closeViewers(h);
                } else if (!h.dirty) {
                    pull(h, t);
                }
            }
        }, 10L, 10L);
    }

    void open(Player viewer, Player target) {
        Holder h = new Holder(target.getUniqueId());
        h.inventory = Bukkit.createInventory(h, SIZE, Component.text("Inventar: " + target.getName()));
        pull(h, target);
        sessions.add(h);
        viewer.openInventory(h.inventory);
    }

    void closeAll() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        for (Holder h : new ArrayList<>(sessions)) {
            Player t = Bukkit.getPlayer(h.target);
            if (t != null && h.dirty) {
                push(h, t);
            }
            closeViewers(h);
        }
        sessions.clear();
    }

    void closeFor(Player target) {
        for (Holder h : new ArrayList<>(sessions)) {
            if (h.target.equals(target.getUniqueId())) {
                closeViewers(h);
            }
        }
    }

    private void closeViewers(Holder h) {
        for (HumanEntity v : new ArrayList<>(h.inventory.getViewers())) {
            v.closeInventory();
        }
        sessions.remove(h);
    }

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        it.setItemMeta(meta);
        return it;
    }

    private void pull(Holder h, Player t) {
        PlayerInventory pi = t.getInventory();
        Inventory inv = h.inventory;
        for (int i = 0; i <= 35; i++) {
            inv.setItem(i, pi.getItem(i));
        }
        inv.setItem(HELMET, pi.getHelmet());
        inv.setItem(CHEST, pi.getChestplate());
        inv.setItem(LEGS, pi.getLeggings());
        inv.setItem(BOOTS, pi.getBoots());
        inv.setItem(OFFHAND, pi.getItemInOffHand());
        ItemStack f = filler();
        for (int i = 0; i < SIZE; i++) {
            if (!EDITABLE.contains(i)) {
                inv.setItem(i, f);
            }
        }
    }

    private void push(Holder h, Player t) {
        PlayerInventory pi = t.getInventory();
        Inventory inv = h.inventory;
        for (int i = 0; i <= 35; i++) {
            pi.setItem(i, inv.getItem(i));
        }
        pi.setHelmet(inv.getItem(HELMET));
        pi.setChestplate(inv.getItem(CHEST));
        pi.setLeggings(inv.getItem(LEGS));
        pi.setBoots(inv.getItem(BOOTS));
        pi.setItemInOffHand(inv.getItem(OFFHAND));
        h.dirty = false;
    }

    private void markDirty(Holder h) {
        if (h.dirty) {
            return;
        }
        h.dirty = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player t = Bukkit.getPlayer(h.target);
            if (t != null) {
                push(h, t);
            } else {
                h.dirty = false;
            }
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) {
            return;
        }
        // Shift-Klick und Doppelklick-Sammeln könnten Platzhalter betreffen -> sicherheitshalber gesperrt
        if (e.isShiftClick() || e.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            return;
        }
        boolean topClicked = e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory());
        if (topClicked && !EDITABLE.contains(e.getRawSlot())) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.NUMBER_KEY && topClicked && !EDITABLE.contains(e.getRawSlot())) {
            e.setCancelled(true);
            return;
        }
        markDirty(h);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw < SIZE && !EDITABLE.contains(raw)) {
                e.setCancelled(true);
                return;
            }
        }
        markDirty(h);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (h.inventory.getViewers().isEmpty()) {
                Player t = Bukkit.getPlayer(h.target);
                if (t != null && h.dirty) {
                    push(h, t);
                }
                sessions.remove(h);
            }
        });
    }
}
