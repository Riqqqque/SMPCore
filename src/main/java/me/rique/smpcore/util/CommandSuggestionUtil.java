package me.rique.smpcore.util;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CommandSuggestionUtil {

    private CommandSuggestionUtil() {
    }

    public static CompletableFuture<Suggestions> suggestMatching(SuggestionsBuilder builder, Collection<String> options) {
        if (options == null || options.isEmpty()) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemainingLowerCase();
        for (String option : options) {
            if (matches(option, remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> suggestMatching(SuggestionsBuilder builder, String... options) {
        String remaining = builder.getRemainingLowerCase();
        for (String option : options) {
            if (matches(option, remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (matches(player.getName(), remaining)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> suggestLoadedWorlds(SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (World world : Bukkit.getWorlds()) {
            if (matches(world.getName(), remaining)) {
                builder.suggest(world.getName());
            }
            String key = world.key().asString();
            if (matches(key, remaining)) {
                builder.suggest(key);
            }
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> suggestNumbers(SuggestionsBuilder builder, long... values) {
        String remaining = builder.getRemainingLowerCase();
        for (long value : values) {
            String option = Long.toString(value);
            if (option.startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    private static boolean matches(String option, String remaining) {
        return option != null
            && !option.isBlank()
            && option.toLowerCase(Locale.ROOT).startsWith(remaining);
    }
}
