package me.rique.smpcore.changelog;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ChangelogManager {

    private static final String FILE_NAME = "changelog.yml";
    private static final int DEFAULT_MAX_ENTRIES = 10;
    private static final String DEFAULT_TITLE = "<gradient:#f97316:#22d3ee><bold>Latest Changes</bold></gradient>";
    private static final String DEFAULT_ENTRY = "<gray>No player-facing changes have been posted yet.</gray>";

    private final SMPCore plugin;
    private final File file;
    private String title = DEFAULT_TITLE;
    private String updated = "Unknown";
    private int maxEntries = DEFAULT_MAX_ENTRIES;
    private List<String> entries = List.of(DEFAULT_ENTRY);

    public ChangelogManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        ensureFile();
        reload();
    }

    public boolean reload() {
        ensureFile();
        YamlConfiguration config = loadDiskConfig();
        if (config == null) {
            plugin.getLogger().warning("Could not reload " + FILE_NAME + "; keeping the last valid changelog.");
            return false;
        }

        ChangelogData loaded = parse(config);
        if (loaded == null) {
            plugin.getLogger().warning("Could not reload " + FILE_NAME + "; it needs a non-empty entries list.");
            return false;
        }

        title = loaded.title();
        updated = loaded.updated();
        maxEntries = loaded.maxEntries();
        entries = loaded.entries();
        return true;
    }

    private ChangelogData parse(YamlConfiguration config) {
        String loadedTitle = config.getString("title");
        String parsedTitle = loadedTitle == null || loadedTitle.isBlank()
            ? DEFAULT_TITLE
            : loadedTitle;

        String loadedUpdated = config.getString("updated");
        String parsedUpdated = loadedUpdated == null || loadedUpdated.isBlank() ? "Unknown" : loadedUpdated;
        int parsedMaxEntries = Math.max(1, config.getInt("max-shown", DEFAULT_MAX_ENTRIES));
        if (!config.isList("entries")) {
            return null;
        }

        List<String> loadedEntries = new ArrayList<>();
        for (String entry : config.getStringList("entries")) {
            if (entry != null && !entry.isBlank()) {
                loadedEntries.add(entry);
            }
        }
        if (loadedEntries.isEmpty()) {
            return null;
        }
        return new ChangelogData(parsedTitle, parsedUpdated, parsedMaxEntries, List.copyOf(loadedEntries));
    }

    public void send(CommandSender sender) {
        sender.sendMessage(MessageUtil.prefixedRaw(title + " <dark_gray>(updated <white>" + updated + "</white>)</dark_gray>"));
        int shown = Math.min(maxEntries, entries.size());
        for (int i = 0; i < shown; i++) {
            String entry = entries.get(i);
            if (sender instanceof Player player && plugin.getSeasonRelicManager() != null) {
                entry = entry.replace("Soul Imprint", plugin.getSeasonRelicManager().soulImprintDisplayName(player));
            }
            sender.sendMessage(MessageUtil.prefixedRaw("<dark_gray>-</dark_gray> " + entry));
        }
        if (entries.size() > shown) {
            sender.sendMessage(MessageUtil.prefixedRaw("<dark_gray>+" + (entries.size() - shown) + " older changes hidden.</dark_gray>"));
        }
        sender.sendMessage(MessageUtil.prefixedRaw("<dark_gray>Use <white>/wiki</white> for the full guide.</dark_gray>"));
    }

    public File getFile() {
        return file;
    }

    private void ensureFile() {
        if (!file.exists()) {
            copyBundledChangelog();
            return;
        }

        YamlConfiguration bundled = loadBundledConfig();
        YamlConfiguration current = loadDiskConfig();
        if (bundled == null || current == null) {
            return;
        }

        long bundledRevision = bundled.getLong("revision", 0L);
        long currentRevision = current.getLong("revision", 0L);
        if (bundledRevision > currentRevision) {
            copyBundledChangelog();
        }
    }

    private YamlConfiguration loadDiskConfig() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private YamlConfiguration loadBundledConfig() {
        try (InputStream input = plugin.getResource(FILE_NAME)) {
            if (input == null) {
                plugin.getLogger().warning("Missing bundled " + FILE_NAME + "; changelog updates cannot be installed.");
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read bundled " + FILE_NAME + ": " + e.getMessage());
            return null;
        }
    }

    private void copyBundledChangelog() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for " + FILE_NAME + ".");
            return;
        }

        try (InputStream input = plugin.getResource(FILE_NAME)) {
            if (input == null) {
                plugin.getLogger().warning("Missing bundled " + FILE_NAME + "; changelog file was not updated.");
                return;
            }
            Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to update " + FILE_NAME + ": " + e.getMessage());
        }
    }

    private record ChangelogData(String title, String updated, int maxEntries, List<String> entries) {
    }
}
