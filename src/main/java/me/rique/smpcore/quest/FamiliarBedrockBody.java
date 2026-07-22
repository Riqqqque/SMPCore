package me.rique.smpcore.quest;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

final class FamiliarBedrockBody {

    private static final double BODY_Y_OFFSET = -0.68D;

    private FamiliarBedrockBody() {
    }

    static ArmorStand spawn(
        Location visualLocation,
        ItemStack head,
        Consumer<ArmorStand> configure
    ) {
        Location bodyLocation = entityLocation(visualLocation);
        return bodyLocation.getWorld().spawn(bodyLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setCollidable(false);
            stand.setCanPickupItems(false);
            stand.setCanTick(false);
            stand.getEquipment().setHelmet(head.clone());
            stand.setDisabledSlots(
                EquipmentSlot.HAND,
                EquipmentSlot.OFF_HAND,
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD
            );
            stand.customName(null);
            stand.setCustomNameVisible(false);
            configure.accept(stand);
        });
    }

    static void move(Entity entity, Location visualTarget) {
        FamiliarMotion.move(entity, entityLocation(visualTarget));
    }

    static Location entityLocation(Location visualLocation) {
        Location location = visualLocation.clone().add(0.0D, BODY_Y_OFFSET, 0.0D);
        location.setPitch(0.0F);
        return location;
    }
}
