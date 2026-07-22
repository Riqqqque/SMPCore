package me.rique.smpcore.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCommandsTest {

    @Test
    void spawnDestinationMustStayExactWhileItsChunkLoads() {
        UUID worldId = UUID.randomUUID();
        World spawnWorld = world(worldId);
        Location expected = new Location(spawnWorld, 12.5, 80.0, -3.5, 90.0F, 0.0F);

        assertTrue(PlayerCommands.sameSpawnDestination(expected, expected.clone()));
        assertFalse(PlayerCommands.sameSpawnDestination(expected, expected.clone().add(1.0, 0.0, 0.0)));
        assertFalse(PlayerCommands.sameSpawnDestination(
            expected,
            new Location(world(UUID.randomUUID()), 12.5, 80.0, -3.5, 90.0F, 0.0F)
        ));
        assertFalse(PlayerCommands.sameSpawnDestination(expected, null));
    }

    private static World world(UUID id) {
        return (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[]{World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUID" -> id;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "TestWorld[" + id + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
