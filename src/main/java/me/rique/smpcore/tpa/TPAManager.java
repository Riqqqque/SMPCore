package me.rique.smpcore.tpa;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages all in-flight TPA requests.
 */
public final class TPAManager {

    private final SMPCore plugin;
    private final Map<UUID, List<TPARequest>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> warmupTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TPAManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean sendRequest(Player requester, Player target, TPARequest.Type type) {
        int cooldownSecs = plugin.getConfigManager().tpaCooldown;
        long lastUsed = cooldowns.getOrDefault(requester.getUniqueId(), 0L);
        long elapsed = (System.currentTimeMillis() - lastUsed) / 1000;

        if (elapsed < cooldownSecs) {
            requester.sendMessage(MessageUtil.warn(
                "Please wait <yellow>" + (cooldownSecs - elapsed) + "s</yellow> before sending another request."));
            return false;
        }

        TPARequest request = new TPARequest(requester.getUniqueId(), target.getUniqueId(), type, System.currentTimeMillis());
        List<TPARequest> requests = pending.computeIfAbsent(target.getUniqueId(), ignored -> new ArrayList<>());
        requests.removeIf(r -> r.requesterUuid().equals(requester.getUniqueId()));
        requests.add(request);
        cooldowns.put(requester.getUniqueId(), System.currentTimeMillis());

        if (type == TPARequest.Type.TO) {
            requester.sendMessage(MessageUtil.info(
                "Teleport request sent to <white>" + target.getName() + "</white>."));
            sendInteractiveRequestMessage(
                target,
                "<white>" + requester.getName() + "</white> wants to teleport to you. ",
                requester.getName()
            );
        } else {
            requester.sendMessage(MessageUtil.info(
                "Requested <white>" + target.getName() + "</white> to teleport to you."));
            sendInteractiveRequestMessage(
                target,
                "<white>" + requester.getName() + "</white> wants you to teleport to them. ",
                requester.getName()
            );
        }

        int timeoutSecs = plugin.getConfigManager().tpaTimeout;
        Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> expireRequest(request.requesterUuid(), request.targetUuid(), request.createdAt()),
            timeoutSecs * 20L
        );

        return true;
    }

    public void accept(Player target, String requesterName) {
        TPARequest request = resolveRequest(target, requesterName);
        if (request == null) {
            target.sendMessage(MessageUtil.error("No pending teleport request" +
                (requesterName != null ? " from <white>" + requesterName + "</white>" : "") + "."));
            return;
        }

        Player requester = Bukkit.getPlayer(request.requesterUuid());
        if (requester == null || !requester.isOnline()) {
            removeRequest(request);
            target.sendMessage(MessageUtil.error("That player is no longer online."));
            return;
        }

        removeRequest(request);

        int delaySecs = plugin.getConfigManager().tpaTeleportDelay;
        Player teleportPlayer = request.type() == TPARequest.Type.TO ? requester : target;
        Player destination = request.type() == TPARequest.Type.TO ? target : requester;

        if (delaySecs <= 0) {
            executeTeleport(teleportPlayer, destination, requester, target, request.type());
            return;
        }

        if (request.type() == TPARequest.Type.TO) {
            requester.sendMessage(MessageUtil.success(
                "Request accepted! Teleporting in <yellow>" + delaySecs + "s</yellow>..."));
            target.sendMessage(MessageUtil.success(
                "Accepted request from <white>" + requester.getName() + "</white>."));
        } else {
            requester.sendMessage(MessageUtil.success(
                "<white>" + target.getName() + "</white> accepted. They will teleport in <yellow>" + delaySecs + "s</yellow>..."));
            target.sendMessage(MessageUtil.success(
                "Accepted request from <white>" + requester.getName() + "</white>. Teleporting in <yellow>" + delaySecs + "s</yellow>..."));
        }

        Location fromLocation = teleportPlayer.getLocation().clone();
        BukkitTask previous = warmupTasks.remove(teleportPlayer.getUniqueId());
        if (previous != null) previous.cancel();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            warmupTasks.remove(teleportPlayer.getUniqueId());
            if (!teleportPlayer.isOnline()) return;
            if (!destination.isOnline()) {
                teleportPlayer.sendMessage(MessageUtil.error("Teleport cancelled - destination went offline."));
                return;
            }

            if (plugin.getConfigManager().tpaMoveCancel) {
                Location current = teleportPlayer.getLocation();
                boolean changedWorld = current.getWorld() == null
                    || fromLocation.getWorld() == null
                    || !current.getWorld().equals(fromLocation.getWorld());
                if (changedWorld || current.distanceSquared(fromLocation) > 0.25) {
                    teleportPlayer.sendMessage(MessageUtil.error("Teleport cancelled - you moved!"));
                    return;
                }
            }

            executeTeleport(teleportPlayer, destination, requester, target, request.type());
        }, delaySecs * 20L);

        warmupTasks.put(teleportPlayer.getUniqueId(), task);
    }

    public void deny(Player target, String requesterName) {
        TPARequest request = resolveRequest(target, requesterName);
        if (request == null) {
            target.sendMessage(MessageUtil.error("No pending teleport request" +
                (requesterName != null ? " from <white>" + requesterName + "</white>" : "") + "."));
            return;
        }

        removeRequest(request);
        Player requester = Bukkit.getPlayer(request.requesterUuid());
        if (requester != null) {
            requester.sendMessage(MessageUtil.error(
                "<white>" + target.getName() + "</white> denied your teleport request."));
        }
        target.sendMessage(MessageUtil.info(
            "Denied request from <white>" + (requester != null ? requester.getName() : "unknown") + "</white>."));
    }

    public void cancel(Player requester) {
        UUID rid = requester.getUniqueId();
        AtomicBoolean found = new AtomicBoolean(false);
        pending.entrySet().removeIf(entry -> {
            boolean removed = entry.getValue().removeIf(r -> r.requesterUuid().equals(rid));
            if (removed) found.set(true);
            return entry.getValue().isEmpty();
        });
        BukkitTask task = warmupTasks.remove(rid);
        if (task != null) task.cancel();

        if (found.get()) {
            requester.sendMessage(MessageUtil.success("Teleport request cancelled."));
        } else {
            requester.sendMessage(MessageUtil.error("You have no active outgoing request."));
        }
    }

    public void onDisconnect(UUID uuid) {
        pending.remove(uuid);
        pending.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(r -> r.requesterUuid().equals(uuid));
            return entry.getValue().isEmpty();
        });
        BukkitTask task = warmupTasks.remove(uuid);
        if (task != null) task.cancel();
        cooldowns.remove(uuid);
    }

    private void executeTeleport(Player who, Player to, Player requester, Player target, TPARequest.Type type) {
        if (!who.isOnline() || !to.isOnline()) {
            if (who.isOnline()) who.sendMessage(MessageUtil.error("Teleport cancelled - player went offline."));
            return;
        }
        plugin.getPlayerManager().saveBackLocation(who);
        who.teleportAsync(to.getLocation()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!success) {
                    if (who.isOnline()) who.sendMessage(MessageUtil.error("Teleport failed."));
                    return;
                }

                if (type == TPARequest.Type.TO) {
                    if (requester.isOnline()) {
                        requester.sendMessage(MessageUtil.success("Teleported to <white>" + target.getName() + "</white>."));
                    }
                    if (target.isOnline()) {
                        target.sendMessage(MessageUtil.info("<white>" + requester.getName() + "</white> teleported to you."));
                    }
                } else {
                    if (target.isOnline()) {
                        target.sendMessage(MessageUtil.success("Teleported to <white>" + requester.getName() + "</white>."));
                    }
                    if (requester.isOnline()) {
                        requester.sendMessage(MessageUtil.info("<white>" + target.getName() + "</white> teleported to you."));
                    }
                }
            });
        });
    }

    private TPARequest resolveRequest(Player target, String requesterName) {
        List<TPARequest> list = pending.get(target.getUniqueId());
        if (list == null || list.isEmpty()) return null;

        int timeoutSecs = plugin.getConfigManager().tpaTimeout;
        list.removeIf(r -> r.isExpired(timeoutSecs));
        if (list.isEmpty()) return null;

        if (requesterName == null) {
            return list.get(list.size() - 1);
        }

        return list.stream()
            .filter(r -> {
                Player p = Bukkit.getPlayer(r.requesterUuid());
                return p != null && p.getName().equalsIgnoreCase(requesterName);
            })
            .findFirst()
            .orElse(null);
    }

    private void removeRequest(TPARequest request) {
        List<TPARequest> list = pending.get(request.targetUuid());
        if (list == null) return;
        list.remove(request);
        if (list.isEmpty()) pending.remove(request.targetUuid());
    }

    private void expireRequest(UUID requesterUuid, UUID targetUuid, long createdAt) {
        List<TPARequest> list = pending.get(targetUuid);
        if (list == null) return;
        list.removeIf(r -> r.requesterUuid().equals(requesterUuid) && r.createdAt() == createdAt);
        if (list.isEmpty()) pending.remove(targetUuid);
    }

    private void sendInteractiveRequestMessage(Player target, String textPrefix, String requesterName) {
        target.sendMessage(MessageUtil.prefixedRaw(
            textPrefix
                + "<gray>Use <white>/tpaccept " + requesterName + "</white> or "
                + "<white>/tpdeny " + requesterName + "</white>.</gray>"
        ));
    }
}
