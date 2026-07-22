package me.rique.smpcore.player;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class PrivateGlowPacketSender {

    private static final int GLOWING_FLAG = 1 << 6;

    private final Plugin plugin;
    private final Bindings bindings;
    private final AtomicBoolean sendFailureLogged = new AtomicBoolean();

    PrivateGlowPacketSender(Plugin plugin) {
        this.plugin = plugin;
        Bindings resolved = null;
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> entityDataAccessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            Class<?> syncedEntityDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> metadataPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");

            Method getHandle = craftPlayerClass.getMethod("getHandle");
            Field connection = serverPlayerClass.getField("connection");
            Method getEntityData = entityClass.getMethod("getEntityData");
            Field sharedFlags = entityClass.getDeclaredField("DATA_SHARED_FLAGS_ID");
            sharedFlags.setAccessible(true);
            Object sharedFlagsAccessor = sharedFlags.get(null);
            Method getEntityDataValue = syncedEntityDataClass.getMethod("get", entityDataAccessorClass);
            Method createDataValue = dataValueClass.getMethod("create", entityDataAccessorClass, Object.class);
            Constructor<?> metadataPacket = metadataPacketClass.getConstructor(int.class, List.class);
            Method sendPacket = connection.getType().getMethod("send", packetClass);
            resolved = new Bindings(
                getHandle,
                connection,
                getEntityData,
                sharedFlagsAccessor,
                getEntityDataValue,
                createDataValue,
                metadataPacket,
                sendPacket
            );
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(
                Level.WARNING,
                "Private teammate glow packets are unavailable; using the compatibility fallback.",
                ex
            );
        }
        bindings = resolved;
    }

    boolean send(Player viewer, Player target, boolean privateGlowEnabled) {
        if (bindings == null || viewer == null || target == null || !viewer.isOnline()) {
            return false;
        }
        try {
            Object viewerHandle = bindings.getHandle().invoke(viewer);
            Object targetHandle = bindings.getHandle().invoke(target);
            Object entityData = bindings.getEntityData().invoke(targetHandle);
            byte realFlags = ((Number) bindings.getEntityDataValue().invoke(entityData, bindings.sharedFlagsAccessor())).byteValue();
            byte viewerFlags = flagsForViewer(realFlags, privateGlowEnabled);
            Object packedFlags = bindings.createDataValue().invoke(null, bindings.sharedFlagsAccessor(), viewerFlags);
            Object packet = bindings.metadataPacket().newInstance(target.getEntityId(), List.of(packedFlags));
            bindings.sendPacket().invoke(bindings.connection().get(viewerHandle), packet);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (sendFailureLogged.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.WARNING, "Could not send a private teammate glow update.", ex);
            }
            return false;
        }
    }

    static byte flagsForViewer(byte realFlags, boolean privateGlowEnabled) {
        return privateGlowEnabled ? (byte) (realFlags | GLOWING_FLAG) : realFlags;
    }

    private record Bindings(
        Method getHandle,
        Field connection,
        Method getEntityData,
        Object sharedFlagsAccessor,
        Method getEntityDataValue,
        Method createDataValue,
        Constructor<?> metadataPacket,
        Method sendPacket
    ) {
    }
}
