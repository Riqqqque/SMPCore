package me.rique.smpcore.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanishListenerTest {

    @Test
    void listCommandAliasesAreNormalizedWithoutArguments() {
        assertEquals("list", VanishListener.commandRoot("/list"));
        assertEquals("minecraft:list", VanishListener.commandRoot(" /minecraft:list uuids "));
        assertEquals("bukkit:list", VanishListener.commandRoot("bukkit:list"));
        assertEquals("", VanishListener.commandRoot(null));
    }
}
