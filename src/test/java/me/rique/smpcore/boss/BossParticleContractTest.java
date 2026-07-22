package me.rique.smpcore.boss;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BossParticleContractTest {

    @Test
    void marshalStackFlashSuppliesThePaperRequiredColorPayload() {
        assertEquals(Color.class, Particle.FLASH.getDataType());
        assertInstanceOf(Particle.FLASH.getDataType(), BossManager.marshalStackFlashColor());
    }
}
