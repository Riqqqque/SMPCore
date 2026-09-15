package me.rique.smpcore.event;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitTask;

public final class TimedLocatorBarEvent implements Listener {

    static final ZoneId EVENT_ZONE = ZoneId.of("America/Denver");
    static final Instant START = ZonedDateTime.of(
        LocalDate.of(2026, 7, 28),
        LocalTime.of(17, 0),
        EVENT_ZONE
    ).toInstant();
    static final Instant END = ZonedDateTime.of(
        LocalDate.of(2026, 7, 28),
        LocalTime.of(23, 0),
        EVENT_ZONE
    ).toInstant();

    private final SMPCore plugin;
    private final Clock clock;
    private BukkitTask task;
    private Boolean appliedState;

    public TimedLocatorBarEvent(SMPCore plugin) {
        this(plugin, Clock.systemUTC());
    }

    TimedLocatorBarEvent(SMPCore plugin, Clock clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    public void start() {
        applyCurrentState(false);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> applyCurrentState(true), 20L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setGameRule(GameRules.LOCATOR_BAR, isActive(clock.instant()));
    }

    private void applyCurrentState(boolean announceTransition) {
        boolean active = isActive(clock.instant());
        if (appliedState != null && appliedState == active) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.LOCATOR_BAR, active);
        }
        boolean previous = Boolean.TRUE.equals(appliedState);
        appliedState = active;

        if (!announceTransition || previous == active) {
            return;
        }
        if (active) {
            Bukkit.broadcast(MessageUtil.success(
                "<gold>The Locator Bar event is live until <white>11:00 PM Mountain Time</white>.</gold>"
            ));
            Bukkit.getOnlinePlayers().forEach(player ->
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.15F)
            );
        } else {
            Bukkit.broadcast(MessageUtil.info("The Locator Bar event has ended."));
        }
    }

    static boolean isActive(Instant instant) {
        return !instant.isBefore(START) && instant.isBefore(END);
    }
}
