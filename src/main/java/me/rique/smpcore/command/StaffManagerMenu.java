package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("UnstableApiUsage")
public final class StaffManagerMenu implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final String ACTION_HOME = "home";
    private static final String ACTION_SEARCH = "search";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_PREVIOUS = "previous";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_PREPARE = "prepare";
    private static final String ACTION_RUN = "run";

    private final SMPCore plugin;
    private final NamespacedKey actionKey;

    public StaffManagerMenu(SMPCore plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "staff_manager_action");
    }

    public static void register(Commands commands, SMPCore plugin) {
        var root = Commands.literal("manager")
            .requires(source -> source.getSender() instanceof Player player && player.hasPermission("smpcore.manager"))
            .executes(context -> {
                plugin.getStaffManagerMenu().openHome((Player) context.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.literal("search")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .executes(context -> {
                        String query = StringArgumentType.getString(context, "query").trim();
                        plugin.getStaffManagerMenu().openSearch((Player) context.getSource().getSender(), query, 0);
                        return Command.SINGLE_SUCCESS;
                    })));
        commands.register(root.build(), "Open the SMPCore staff command manager", List.of("staffmanager", "adminmanager"));
    }

    public void openHome(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new HomeHolder(),
            MENU_SIZE,
            title(player, "<gradient:#f97316:#22d3ee><bold>Staff Manager</bold></gradient>", "Staff Manager")
        );
        decorate(inventory);
        inventory.setItem(4, button(Material.COMMAND_BLOCK,
            "<gradient:#f97316:#22d3ee><bold>SMPCore Staff Manager</bold></gradient>",
            List.of(
                "<gray>Every SMPCore staff command, grouped by job.</gray>",
                "<gray>Open a tool to see syntax, access, and safety notes.</gray>",
                "<dark_gray>High-impact commands are never run by accident.</dark_gray>"
            )));

        int[] categorySlots = {10, 12, 14, 16, 28, 30, 32, 34};
        StaffCommandCatalog.Category[] categories = StaffCommandCatalog.Category.values();
        for (int i = 0; i < categories.length; i++) {
            StaffCommandCatalog.Category category = categories[i];
            long available = StaffCommandCatalog.inCategory(category).stream().filter(entry -> hasAccess(player, entry)).count();
            int total = StaffCommandCatalog.inCategory(category).size();
            inventory.setItem(categorySlots[i], button(category.icon(),
                "<aqua><bold>" + mini(category.title()) + "</bold></aqua>",
                List.of(
                    "<gray>" + mini(category.description()) + "</gray>",
                    "<dark_gray>" + available + "/" + total + " tools available to you.</dark_gray>",
                    "<yellow>Click to browse.</yellow>"
                ), "category:" + category.name()));
        }

        inventory.setItem(48, button(Material.OAK_SIGN, "<yellow><bold>Search Commands</bold></yellow>",
            List.of(
                "<gray>Find commands by name, purpose, syntax, or alias.</gray>",
                "<yellow>Click for a ready-to-use search prompt.</yellow>"
            ), ACTION_SEARCH));
        inventory.setItem(49, button(Material.BARRIER, "<red><bold>Close</bold></red>",
            List.of("<gray>Close the staff manager.</gray>"), ACTION_CLOSE));
        inventory.setItem(50, button(Material.BOOK, "<gold><bold>How This Works</bold></gold>",
            List.of(
                "<gray>Green and aqua tools are routine.</gray>",
                "<gray>Yellow tools change live state.</gray>",
                "<gray>Red tools can affect progression or the world.</gray>",
                "<gray>Locked tools show the permission you need.</gray>",
                "<dark_gray>Prepared commands go to chat for review first.</dark_gray>"
            )));
        player.openInventory(inventory);
    }

    private void openCategory(Player player, StaffCommandCatalog.Category category, int page) {
        openList(player, StaffCommandCatalog.inCategory(category), category, null, page);
    }

    private void openSearch(Player player, String query, int page) {
        if (query == null || query.isBlank()) {
            sendSearchPrompt(player);
            return;
        }
        openList(player, StaffCommandCatalog.search(query), null, query, page);
    }

    private void openList(
        Player player,
        List<StaffCommandCatalog.Entry> entries,
        StaffCommandCatalog.Category category,
        String query,
        int requestedPage
    ) {
        int pageCount = Math.max(1, (entries.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        String heading = category == null ? "Search: " + query : category.title();
        Inventory inventory = Bukkit.createInventory(
            new ListHolder(category, query, page),
            MENU_SIZE,
            title(player, "<gradient:#f97316:#22d3ee><bold>" + mini(heading) + "</bold></gradient>", shortTitle(heading))
        );
        decorate(inventory);
        inventory.setItem(4, button(category == null ? Material.SPYGLASS : category.icon(),
            "<aqua><bold>" + mini(heading) + "</bold></aqua>",
            List.of(
                "<gray>" + entries.size() + " matching staff tool" + (entries.size() == 1 ? "" : "s") + ".</gray>",
                "<dark_gray>Page " + (page + 1) + " of " + pageCount + ".</dark_gray>"
            )));

        int start = page * CONTENT_SLOTS.length;
        int end = Math.min(entries.size(), start + CONTENT_SLOTS.length);
        for (int index = start; index < end; index++) {
            StaffCommandCatalog.Entry entry = entries.get(index);
            inventory.setItem(CONTENT_SLOTS[index - start], entryButton(player, entry));
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, button(Material.COBWEB, "<red><bold>No Matches</bold></red>",
                List.of(
                    "<gray>Try a command root, player task, or system name.</gray>",
                    "<dark_gray>Examples: recovery, boss, NPC, warp, Bedrock.</dark_gray>"
                )));
        }

        if (page > 0) {
            inventory.setItem(45, button(Material.ARROW, "<yellow><bold>Previous Page</bold></yellow>",
                List.of("<gray>Go to page " + page + ".</gray>"), ACTION_PREVIOUS));
        }
        inventory.setItem(48, button(Material.OAK_SIGN, "<yellow><bold>Search</bold></yellow>",
            List.of("<gray>Start a different command search.</gray>"), ACTION_SEARCH));
        inventory.setItem(49, button(Material.NETHER_STAR, "<aqua><bold>Manager Home</bold></aqua>",
            List.of("<gray>Return to all categories.</gray>"), ACTION_HOME));
        inventory.setItem(50, button(Material.BARRIER, "<red><bold>Close</bold></red>",
            List.of("<gray>Close this menu.</gray>"), ACTION_CLOSE));
        if (page + 1 < pageCount) {
            inventory.setItem(53, button(Material.ARROW, "<yellow><bold>Next Page</bold></yellow>",
                List.of("<gray>Go to page " + (page + 2) + ".</gray>"), ACTION_NEXT));
        }
        player.openInventory(inventory);
    }

    private void openDetail(Player player, StaffCommandCatalog.Entry entry, ListHolder back) {
        Inventory inventory = Bukkit.createInventory(
            new DetailHolder(entry.id(), back),
            MENU_SIZE,
            title(player, "<gradient:#f97316:#22d3ee><bold>" + mini(entry.title()) + "</bold></gradient>", shortTitle(entry.title()))
        );
        decorate(inventory);
        List<String> overview = new ArrayList<>();
        overview.add("<gray>" + mini(entry.description()) + "</gray>");
        overview.add("");
        overview.add(entry.risk().color() + "<bold>" + entry.risk().label() + "</bold></" + colorName(entry.risk().color()) + ">");
        overview.add(accessLine(player, entry));
        inventory.setItem(4, button(entry.icon(), "<aqua><bold>" + mini(entry.title()) + "</bold></aqua>", overview));

        List<String> syntax = new ArrayList<>();
        syntax.add("<gray>Required parts use <white>&lt;angle brackets&gt;</white>.</gray>");
        syntax.add("<gray>Optional parts use <white>[square brackets]</white>.</gray>");
        syntax.add("");
        entry.usages().forEach(usage -> syntax.add("<yellow>" + mini(usage) + "</yellow>"));
        inventory.setItem(20, button(Material.WRITABLE_BOOK, "<gold><bold>Syntax & Variants</bold></gold>", syntax));

        List<String> accessLore = new ArrayList<>();
        accessLore.add(accessLine(player, entry));
        if (entry.permission().isBlank()) {
            accessLore.add("<dark_gray>This route applies its own owner check.</dark_gray>");
        } else {
            accessLore.add("<gray>Permission:</gray>");
            accessLore.add("<white>" + mini(entry.permission()) + "</white>");
        }
        accessLore.add("");
        accessLore.add(entry.risk().color() + entry.risk().label() + "</" + colorName(entry.risk().color()) + ">");
        inventory.setItem(22, button(accessMaterial(player, entry), "<yellow><bold>Access & Safety</bold></yellow>", accessLore));

        if (hasAccess(player, entry)) {
            inventory.setItem(24, button(Material.OAK_SIGN, "<aqua><bold>Prepare Command</bold></aqua>",
                List.of(
                    "<gray>Put this command in chat so you can fill in</gray>",
                    "<gray>targets, values, or confirmations before sending.</gray>",
                    "<white>" + mini(entry.preparedCommand()) + "</white>",
                    "<yellow>Click to prepare.</yellow>"
                ), ACTION_PREPARE));
            if (entry.directRun()) {
                inventory.setItem(31, button(Material.LIME_DYE, "<green><bold>Run Safe Default</bold></green>",
                    List.of(
                        "<gray>Runs the non-destructive default shown below.</gray>",
                        "<white>" + mini(entry.preparedCommand()) + "</white>",
                        "<yellow>Click to run now.</yellow>"
                    ), ACTION_RUN));
            }
        } else {
            inventory.setItem(24, button(Material.BARRIER, "<red><bold>Tool Locked</bold></red>",
                List.of(
                    "<gray>You do not have this tool's staff access.</gray>",
                    "<dark_gray>Ask an owner instead of bypassing it.</dark_gray>"
                )));
        }

        inventory.setItem(48, button(Material.ARROW, "<yellow><bold>Back</bold></yellow>",
            List.of("<gray>Return to the previous command list.</gray>"), ACTION_BACK));
        inventory.setItem(49, button(Material.NETHER_STAR, "<aqua><bold>Manager Home</bold></aqua>",
            List.of("<gray>Return to all categories.</gray>"), ACTION_HOME));
        inventory.setItem(50, button(Material.BARRIER, "<red><bold>Close</bold></red>",
            List.of("<gray>Close this menu.</gray>"), ACTION_CLOSE));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof ManagerHolder) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        String action = action(top.getItem(event.getRawSlot()));
        if (action == null) return;

        if (ACTION_CLOSE.equals(action)) {
            player.closeInventory();
            return;
        }
        if (ACTION_HOME.equals(action)) {
            openHome(player);
            return;
        }
        if (ACTION_SEARCH.equals(action)) {
            player.closeInventory();
            sendSearchPrompt(player);
            return;
        }
        if (holder instanceof HomeHolder && action.startsWith("category:")) {
            StaffCommandCatalog.Category category = parseCategory(action.substring("category:".length()));
            if (category != null) openCategory(player, category, 0);
            return;
        }
        if (holder instanceof ListHolder listHolder) {
            if (ACTION_PREVIOUS.equals(action)) {
                reopenList(player, listHolder, listHolder.page() - 1);
            } else if (ACTION_NEXT.equals(action)) {
                reopenList(player, listHolder, listHolder.page() + 1);
            } else if (action.startsWith("entry:")) {
                StaffCommandCatalog.Entry entry = StaffCommandCatalog.byId(action.substring("entry:".length()));
                if (entry != null) openDetail(player, entry, listHolder);
            }
            return;
        }
        if (holder instanceof DetailHolder detailHolder) {
            StaffCommandCatalog.Entry entry = StaffCommandCatalog.byId(detailHolder.entryId());
            if (entry == null) {
                openHome(player);
                return;
            }
            if (ACTION_BACK.equals(action)) {
                if (detailHolder.back() == null) openHome(player);
                else reopenList(player, detailHolder.back(), detailHolder.back().page());
            } else if (ACTION_PREPARE.equals(action)) {
                player.closeInventory();
                prepareCommand(player, entry);
            } else if (ACTION_RUN.equals(action)) {
                if (!entry.directRun() || !hasAccess(player, entry)) {
                    player.sendMessage(MessageUtil.error("You cannot run that staff tool."));
                    return;
                }
                player.closeInventory();
                player.performCommand(stripSlash(entry.preparedCommand()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ManagerHolder) {
            event.setCancelled(true);
        }
    }

    private void reopenList(Player player, ListHolder holder, int page) {
        if (holder.category() != null) openCategory(player, holder.category(), page);
        else openSearch(player, holder.query(), page);
    }

    private ItemStack entryButton(Player player, StaffCommandCatalog.Entry entry) {
        boolean access = hasAccess(player, entry);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + mini(entry.description()) + "</gray>");
        lore.add("");
        lore.add(entry.risk().color() + entry.risk().label() + "</" + colorName(entry.risk().color()) + ">");
        lore.add(access
            ? "<green>Available to you</green>"
            : "<red>Locked: " + mini(entry.permission().isBlank() ? "owner only" : entry.permission()) + "</red>");
        lore.add("<yellow>Click for syntax and details.</yellow>");
        return button(access ? entry.icon() : Material.GRAY_DYE,
            (access ? "<aqua>" : "<gray>") + "<bold>" + mini(entry.title()) + "</bold>" + (access ? "</aqua>" : "</gray>"),
            lore, "entry:" + entry.id());
    }

    private void prepareCommand(Player player, StaffCommandCatalog.Entry entry) {
        if (!hasAccess(player, entry)) {
            player.sendMessage(MessageUtil.error("You do not have access to that staff tool."));
            return;
        }
        Component command = Component.text(entry.preparedCommand())
            .clickEvent(ClickEvent.suggestCommand(entry.preparedCommand()))
            .hoverEvent(HoverEvent.showText(Component.text("Click to put this command in your chat box")));
        player.sendMessage(MessageUtil.prefixedRaw("<yellow>Review the targets and values before sending:</yellow>"));
        player.sendMessage(Component.text("  ").append(command));
        player.sendMessage(MessageUtil.info("Click the command above to edit it."));
    }

    private void sendSearchPrompt(Player player) {
        String command = "/manager search ";
        Component prompt = Component.text(command + "<word>")
            .clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text("Click to search the staff manager")));
        player.sendMessage(MessageUtil.prefixedRaw("<aqua><bold>Staff Manager Search</bold></aqua>"));
        player.sendMessage(MessageUtil.info("Search by command, task, system, or alias."));
        player.sendMessage(Component.text("  ").append(prompt));
    }

    private boolean hasAccess(Player player, StaffCommandCatalog.Entry entry) {
        if (entry.risk() == StaffCommandCatalog.Risk.OWNER_ONLY) return AdminCommands.hasAbsoluteOwnerRights(player);
        return entry.permission().isBlank() || player.hasPermission(entry.permission());
    }

    private String accessLine(Player player, StaffCommandCatalog.Entry entry) {
        if (entry.risk() == StaffCommandCatalog.Risk.OWNER_ONLY) {
            return hasAccess(player, entry)
                ? "<green>Verified owner access.</green>"
                : "<dark_red>Owner-only recovery route.</dark_red>";
        }
        if (entry.permission().isBlank()) return "<green>Available.</green>";
        return player.hasPermission(entry.permission())
            ? "<green>You have access.</green>"
            : "<red>Missing " + mini(entry.permission()) + ".</red>";
    }

    private Material accessMaterial(Player player, StaffCommandCatalog.Entry entry) {
        if (entry.risk() == StaffCommandCatalog.Risk.OWNER_ONLY) {
            return hasAccess(player, entry) ? Material.LIME_DYE : Material.REDSTONE_BLOCK;
        }
        if (entry.permission().isBlank() || player.hasPermission(entry.permission())) return Material.LIME_DYE;
        return Material.RED_DYE;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        return button(material, name, lore, null);
    }

    private ItemStack button(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (action != null && !action.isBlank()) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String action(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private void decorate(Inventory inventory) {
        ItemStack dark = button(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of());
        ItemStack orange = button(Material.ORANGE_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) {
                inventory.setItem(slot, (slot + row) % 3 == 0 ? orange : dark);
            }
        }
    }

    private static Component title(Player player, String javaTitle, String bedrockTitle) {
        return BedrockCompat.menuTitle(player, MM.deserialize(javaTitle), shortTitle(bedrockTitle));
    }

    private static String shortTitle(String text) {
        if (text == null) return "Staff Manager";
        String clean = text.length() > 30 ? text.substring(0, 30) : text;
        return clean.isBlank() ? "Staff Manager" : clean;
    }

    private static StaffCommandCatalog.Category parseCategory(String raw) {
        try {
            return StaffCommandCatalog.Category.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String stripSlash(String command) {
        return command != null && command.startsWith("/") ? command.substring(1) : command;
    }

    private static String mini(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static String colorName(String tag) {
        return tag.substring(1, tag.length() - 1);
    }

    private sealed interface ManagerHolder extends InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder
        permits HomeHolder, ListHolder, DetailHolder {
    }

    private record HomeHolder() implements ManagerHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ListHolder(StaffCommandCatalog.Category category, String query, int page) implements ManagerHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record DetailHolder(String entryId, ListHolder back) implements ManagerHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
