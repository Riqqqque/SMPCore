package me.rique.smpcore.spawn;

import me.rique.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnAmbienceManager {

    private static final long TASK_PERIOD_TICKS = 2L;
    private static final long LISTENER_REFRESH_TICKS = 10L;
    private static final long FIRST_SONG_DELAY_TICKS = 20L * 15L;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final Particle.DustOptions VEIL_DUST = new Particle.DustOptions(Color.fromRGB(111, 214, 255), 0.85f);
    private static final Particle.DustOptions RUNE_DUST = new Particle.DustOptions(Color.fromRGB(180, 105, 255), 1.05f);

    private final SMPCore plugin;
    private BukkitTask task;
    private List<Player> listeners = List.of();
    private List<Player> songListeners = List.of();
    private final Map<UUID, WelcomeSongPlayback> welcomeSongs = new HashMap<>();
    private Location spawn;
    private long elapsedTicks;
    private long nextSongAt;
    private long nextRunePulseAt;
    private long nextSoundscapeAt;
    private int songTick = -1;
    private int songCueIndex;

    private boolean enabled;
    private boolean songEnabled;
    private boolean wispsEnabled;
    private boolean motesEnabled;
    private boolean runePulseEnabled;
    private boolean soundscapeEnabled;
    private double radius;
    private double radiusSquared;
    private double songRadius;
    private double songFadeDistance;
    private double songOuterRadiusSquared;
    private float songVolume;
    private long songIntervalTicks;
    private long runePulseIntervalTicks;
    private long soundscapeMinimumTicks;
    private long soundscapeMaximumTicks;

    public SpawnAmbienceManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reloadConfig();
    }

    public void reloadConfig() {
        shutdown();
        enabled = plugin.getConfig().getBoolean("spawn.ambience.enabled", true);
        if (!enabled) {
            return;
        }

        radius = clamp(plugin.getConfig().getDouble("spawn.ambience.radius", 100.0), 8.0, 160.0);
        radiusSquared = radius * radius;
        songEnabled = plugin.getConfig().getBoolean("spawn.ambience.song.enabled", true);
        songRadius = clamp(plugin.getConfig().getDouble("spawn.ambience.song.radius", 100.0), 16.0, 256.0);
        songFadeDistance = clamp(plugin.getConfig().getDouble("spawn.ambience.song.fade-distance", 20.0), 0.0, 128.0);
        double songOuterRadius = songRadius + songFadeDistance;
        songOuterRadiusSquared = songOuterRadius * songOuterRadius;
        songVolume = (float) clamp(plugin.getConfig().getDouble("spawn.ambience.song.volume", 0.72), 0.10, 1.0);
        songIntervalTicks = secondsToTicks(plugin.getConfig().getLong("spawn.ambience.song.interval-seconds", 300L), 60L, 3600L);
        wispsEnabled = plugin.getConfig().getBoolean("spawn.ambience.wisps.enabled", true);
        motesEnabled = plugin.getConfig().getBoolean("spawn.ambience.motes.enabled", true);
        runePulseEnabled = plugin.getConfig().getBoolean("spawn.ambience.rune-pulse.enabled", true);
        runePulseIntervalTicks = secondsToTicks(plugin.getConfig().getLong("spawn.ambience.rune-pulse.interval-seconds", 35L), 10L, 600L);
        soundscapeEnabled = plugin.getConfig().getBoolean("spawn.ambience.soundscape.enabled", true);
        soundscapeMinimumTicks = secondsToTicks(plugin.getConfig().getLong("spawn.ambience.soundscape.minimum-seconds", 18L), 8L, 600L);
        soundscapeMaximumTicks = Math.max(
            soundscapeMinimumTicks,
            secondsToTicks(plugin.getConfig().getLong("spawn.ambience.soundscape.maximum-seconds", 32L), 8L, 900L)
        );

        elapsedTicks = 0L;
        nextSongAt = FIRST_SONG_DELAY_TICKS;
        nextRunePulseAt = runePulseIntervalTicks;
        scheduleNextSoundscape();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TASK_PERIOD_TICKS, TASK_PERIOD_TICKS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        listeners = List.of();
        songListeners = List.of();
        welcomeSongs.clear();
        spawn = null;
        songTick = -1;
        songCueIndex = 0;
    }

    private void tick() {
        elapsedTicks += TASK_PERIOD_TICKS;
        if (spawn == null || elapsedTicks % LISTENER_REFRESH_TICKS == 0L) {
            refreshListeners();
        }
        if (spawn == null) {
            return;
        }

        if (songEnabled) {
            tickSong();
            tickWelcomeSongs();
        }
        if (listeners.isEmpty()) {
            return;
        }
        if (wispsEnabled && elapsedTicks % 10L == 0L) {
            playWisps();
        }
        if (motesEnabled && elapsedTicks % 20L == 0L) {
            playLocalMotes();
        }
        if (runePulseEnabled && elapsedTicks >= nextRunePulseAt) {
            playRunePulse();
            nextRunePulseAt = elapsedTicks + runePulseIntervalTicks;
        }
        if (soundscapeEnabled && elapsedTicks >= nextSoundscapeAt) {
            playSoundscape();
            scheduleNextSoundscape();
        }
    }

    private void refreshListeners() {
        Location latest = plugin.getExactSpawnListener() == null
            ? plugin.getConfigManager().exactSpawnLocation()
            : plugin.getExactSpawnListener().exactSpawnLocation();
        if (latest == null || latest.getWorld() == null) {
            spawn = null;
            listeners = List.of();
            songListeners = List.of();
            return;
        }

        spawn = latest.clone();
        List<Player> nearby = new ArrayList<>();
        List<Player> songNearby = new ArrayList<>();
        for (Player player : latest.getWorld().getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            double distanceSquared = player.getLocation().distanceSquared(latest);
            if (distanceSquared <= radiusSquared) {
                nearby.add(player);
            }
            if (songEnabled && distanceSquared <= songOuterRadiusSquared && isSpawnMusicEnabled(player)) {
                songNearby.add(player);
            }
        }
        listeners = List.copyOf(nearby);
        songListeners = List.copyOf(songNearby);
    }

    private void tickSong() {
        if (songTick < 0) {
            if (elapsedTicks >= nextSongAt && !songListeners.isEmpty()) {
                songTick = 0;
                songCueIndex = 0;
            }
            return;
        }

        List<SpawnSong.Cue> cues = SpawnSong.veilwardWelcome();
        while (songCueIndex < cues.size() && cues.get(songCueIndex).tick() <= songTick) {
            playSongCue(cues.get(songCueIndex));
            songCueIndex++;
        }
        songTick += TASK_PERIOD_TICKS;
        if (songTick > SpawnSong.lengthTicks()) {
            songTick = -1;
            songCueIndex = 0;
            nextSongAt = elapsedTicks + songIntervalTicks;
        }
    }

    /**
     * Starts the normal spawn theme just for a newly joined player. The shared
     * broadcast skips this player until their welcome playback has finished.
     */
    public void playWelcomeSong(Player player) {
        if (!enabled || !songEnabled || player == null || !player.isOnline()) {
            return;
        }
        if (spawn == null) {
            refreshListeners();
        }
        if (!isCurrentSongListener(player)) {
            return;
        }
        welcomeSongs.put(player.getUniqueId(), new WelcomeSongPlayback());
    }

    public void onMusicPreferenceChanged(Player player, boolean enabledForPlayer) {
        if (player == null || !enabled || !songEnabled) {
            return;
        }
        if (enabledForPlayer) {
            refreshListeners();
            return;
        }

        UUID playerId = player.getUniqueId();
        boolean welcomeWasPlaying = welcomeSongs.remove(playerId) != null;
        boolean sharedSongWasPlaying = songTick >= 0 && songListeners.stream()
            .anyMatch(listener -> listener.getUniqueId().equals(playerId));
        boolean bossMusicIsPlaying = plugin.getBossMusicManager() != null
            && plugin.getBossMusicManager().isPlaying(player);
        if ((welcomeWasPlaying || sharedSongWasPlaying) && !bossMusicIsPlaying) {
            stopSongSounds(player);
        }
    }

    private void tickWelcomeSongs() {
        if (welcomeSongs.isEmpty()) {
            return;
        }
        List<SpawnSong.Cue> cues = SpawnSong.veilwardWelcome();
        Iterator<Map.Entry<UUID, WelcomeSongPlayback>> iterator = welcomeSongs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, WelcomeSongPlayback> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (!isCurrentSongListener(player)) {
                iterator.remove();
                continue;
            }

            WelcomeSongPlayback playback = entry.getValue();
            while (playback.cueIndex < cues.size() && cues.get(playback.cueIndex).tick() <= playback.songTick) {
                playSongCue(player, cues.get(playback.cueIndex));
                playback.cueIndex++;
            }
            playback.songTick += TASK_PERIOD_TICKS;
            if (playback.songTick > SpawnSong.lengthTicks()) {
                iterator.remove();
            }
        }
    }

    private void playSongCue(SpawnSong.Cue cue) {
        for (Player player : songListeners) {
            if (!isCurrentSongListener(player) || welcomeSongs.containsKey(player.getUniqueId())) {
                continue;
            }
            playSongCue(player, cue);
        }
    }

    private void playSongCue(Player player, SpawnSong.Cue cue) {
        Sound sound = soundFor(cue.voice());
        float falloff = (float) songFalloff(
            Math.sqrt(player.getLocation().distanceSquared(spawn)),
            songRadius,
            songFadeDistance
        );
        if (falloff <= 0.001F) {
            return;
        }
        player.playSound(
            player.getLocation(),
            sound,
            SoundCategory.RECORDS,
            songVolume * cue.volume() * falloff,
            SpawnSong.pitchForNote(cue.note())
        );
    }

    private void playWisps() {
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }
        double motion = elapsedTicks * 0.035;
        for (int index = 0; index < 3; index++) {
            double angle = motion + (TWO_PI * index / 3.0);
            double orbit = 3.4 + index * 1.35;
            double y = 1.15 + Math.sin(motion * 1.4 + index) * 0.55 + index * 0.18;
            Location point = visibleAir(spawn.clone().add(Math.cos(angle) * orbit, y, Math.sin(angle) * orbit));
            if (point == null) {
                continue;
            }
            for (Player player : listeners) {
                if (!isCurrentListener(player)) {
                    continue;
                }
                player.spawnParticle(Particle.END_ROD, point, 1, 0.025, 0.025, 0.025, 0.002);
                player.spawnParticle(Particle.DUST, point, 1, 0.03, 0.03, 0.03, 0.0, VEIL_DUST);
                if (elapsedTicks % 30L == 0L) {
                    player.spawnParticle(Particle.WITCH, point, 1, 0.06, 0.08, 0.06, 0.005);
                }
            }
        }
    }

    private void playLocalMotes() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : listeners) {
            if (!isCurrentListener(player)) {
                continue;
            }
            double angle = random.nextDouble(TWO_PI);
            double distance = 1.25D + random.nextDouble(4.75D);
            Location point = visibleAir(player.getLocation().clone().add(
                Math.cos(angle) * distance,
                0.35D + random.nextDouble(1.8D),
                Math.sin(angle) * distance
            ));
            if (point == null || point.getWorld() != spawn.getWorld()
                || point.distanceSquared(spawn) > radiusSquared) {
                continue;
            }
            player.spawnParticle(Particle.FIREFLY, point, 1, 0.18D, 0.22D, 0.18D, 0.002D);
            if (random.nextInt(4) == 0) {
                player.spawnParticle(Particle.SPORE_BLOSSOM_AIR, point, 1, 0.3D, 0.2D, 0.3D, 0.001D);
            } else if (random.nextInt(6) == 0) {
                player.spawnParticle(Particle.CHERRY_LEAVES, point, 1, 0.2D, 0.1D, 0.2D, 0.01D);
            }
        }
    }

    private void playRunePulse() {
        Location center = spawn.clone().add(0.0, 0.18, 0.0);
        for (Player player : listeners) {
            if (!isCurrentListener(player)) {
                continue;
            }
            for (int point = 0; point < 32; point++) {
                double angle = TWO_PI * point / 32.0;
                Location outer = center.clone().add(Math.cos(angle) * 3.2, 0.12, Math.sin(angle) * 3.2);
                Location inner = center.clone().add(Math.cos(-angle) * 1.65, 0.48, Math.sin(-angle) * 1.65);
                player.spawnParticle(Particle.DUST, outer, 1, 0.0, 0.0, 0.0, 0.0, RUNE_DUST);
                if (point % 2 == 0) {
                    player.spawnParticle(Particle.ENCHANT, inner, 1, 0.02, 0.02, 0.02, 0.04);
                }
            }
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.34f, 1.55f);
        }
    }

    private void playSoundscape() {
        int selection = ThreadLocalRandom.current().nextInt(4);
        Sound sound = switch (selection) {
            case 0 -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case 1 -> Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM;
            case 2 -> Sound.BLOCK_PORTAL_AMBIENT;
            default -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
        };
        float pitch = switch (selection) {
            case 0 -> 1.45f;
            case 1 -> 1.18f;
            case 2 -> 0.72f;
            default -> 1.25f;
        };
        for (Player player : listeners) {
            if (!isCurrentListener(player)) {
                continue;
            }
            player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, 0.18f, pitch);
        }
    }

    private boolean isCurrentListener(Player player) {
        if (player == null || !player.isOnline() || spawn == null || spawn.getWorld() == null
            || player.getWorld() != spawn.getWorld()) {
            return false;
        }
        if (plugin.getBossMusicManager() != null && plugin.getBossMusicManager().isPlaying(player)) {
            return false;
        }
        return player.getLocation().distanceSquared(spawn) <= radiusSquared;
    }

    private boolean isCurrentSongListener(Player player) {
        if (player == null || !player.isOnline() || spawn == null || spawn.getWorld() == null
            || player.getWorld() != spawn.getWorld()) {
            return false;
        }
        if (plugin.getBossMusicManager() != null && plugin.getBossMusicManager().isPlaying(player)) {
            return false;
        }
        return isSpawnMusicEnabled(player) && player.getLocation().distanceSquared(spawn) <= songOuterRadiusSquared;
    }

    private boolean isSpawnMusicEnabled(Player player) {
        return plugin.getPlayerSettingsManager() == null || plugin.getPlayerSettingsManager().isSpawnMusicEnabled(player);
    }

    private void stopSongSounds(Player player) {
        for (SpawnSong.Voice voice : SpawnSong.Voice.values()) {
            player.stopSound(soundFor(voice), SoundCategory.RECORDS);
        }
    }

    private Sound soundFor(SpawnSong.Voice voice) {
        return switch (voice) {
            case HARP -> Sound.BLOCK_NOTE_BLOCK_HARP;
            case CHIME -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case FLUTE -> Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case BASS -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case BELL -> Sound.BLOCK_NOTE_BLOCK_BELL;
        };
    }

    static double songFalloff(double distance, double fullVolumeRadius, double fadeDistance) {
        if (!Double.isFinite(distance) || !Double.isFinite(fullVolumeRadius) || !Double.isFinite(fadeDistance)
            || fullVolumeRadius <= 0.0 || distance < 0.0) {
            return 0.0;
        }
        if (distance <= fullVolumeRadius) {
            return 1.0;
        }
        if (fadeDistance <= 0.0 || distance >= fullVolumeRadius + fadeDistance) {
            return 0.0;
        }
        double progress = (distance - fullVolumeRadius) / fadeDistance;
        double smoothProgress = progress * progress * (3.0 - 2.0 * progress);
        return 1.0 - smoothProgress;
    }

    private Location visibleAir(Location seed) {
        World world = seed.getWorld();
        if (world == null) {
            return null;
        }
        for (int offset = 0; offset <= 3; offset++) {
            Location candidate = seed.clone().add(0.0, offset, 0.0);
            if (candidate.getBlock().isPassable()) {
                return candidate;
            }
        }
        return null;
    }

    private void scheduleNextSoundscape() {
        long spread = Math.max(0L, soundscapeMaximumTicks - soundscapeMinimumTicks);
        long offset = spread == 0L ? 0L : ThreadLocalRandom.current().nextLong(spread + 1L);
        nextSoundscapeAt = elapsedTicks + soundscapeMinimumTicks + offset;
    }

    private long secondsToTicks(long seconds, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, seconds)) * 20L;
    }

    private double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class WelcomeSongPlayback {
        private int songTick;
        private int cueIndex;
    }
}
