package me.rique.smpcore.tpa;

import java.util.UUID;

/**
 * Immutable snapshot of a pending teleport request.
 *
 * @param requesterUuid UUID of the player who typed the command
 * @param targetUuid    UUID of the player who must accept/deny
 * @param type          TO = requester wants to teleport to target;
 *                      HERE = requester wants target to teleport to them
 * @param createdAt     System.currentTimeMillis() at creation
 */
public record TPARequest(
    UUID requesterUuid,
    UUID targetUuid,
    Type type,
    long createdAt
) {
    public enum Type { TO, HERE }

    public boolean isExpired(int timeoutSeconds) {
        return System.currentTimeMillis() - createdAt > timeoutSeconds * 1000L;
    }
}
