package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockCompatTest {

    @Test
    void gameResultsUseChatAndActionBar() {
        AtomicInteger chatMessages = new AtomicInteger();
        AtomicInteger actionBars = new AtomicInteger();
        Player player = (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (proxy, method, arguments) -> {
                if ("sendMessage".equals(method.getName())) {
                    chatMessages.incrementAndGet();
                } else if ("sendActionBar".equals(method.getName())) {
                    actionBars.incrementAndGet();
                }
                return null;
            }
        );

        BedrockCompat.sendGameMessage(player, Component.text("Winner"));

        assertEquals(1, chatMessages.get());
        assertEquals(1, actionBars.get());
    }
}
