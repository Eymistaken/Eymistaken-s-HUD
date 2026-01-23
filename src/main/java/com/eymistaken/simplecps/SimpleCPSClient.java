package com.eymistaken.simplecps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class SimpleCPSClient implements ClientModInitializer {

    // Tıklama verileri
    private static final long[] leftClicks = new long[20];
    private static final long[] rightClicks = new long[20];
    private static int leftIndex = 0;
    private static int rightIndex = 0;
    
    // Basılı tutma kontrolü
    private static boolean wasLeftPressed = false;
    private static boolean wasRightPressed = false;

    @Override
    public void onInitializeClient() {
        // Ekrana çizim yapacak olayı kaydet
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            renderCPS(drawContext);
        });
    }

    private void renderCPS(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Sadece oyun içindeyken ve arayüz gizli değilse (F1) çiz
        if (client.player == null || client.options.hudHidden) return;

        updateClicks(client);
        
        int lCps = getCPS(leftClicks);
        int rCps = getCPS(rightClicks);

        // --- YENİ ÇİZİM AYARLARI ---
        // Metin: "Sol | Sağ" (Örn: 12 | 5)
        String text = lCps + " | " + rCps;
        
        // Konum: X=4, Y=4 (Sol üst köşe, kenara yapışmasın diye 4px boşluk)
        // Renk: Beyaz (0xFFFFFFFF)
        context.drawText(client.textRenderer, text, 4, 4, 0xFFFFFFFF, true);
    }

    private void updateClicks(MinecraftClient client) {
        // Sol Tık
        boolean isLeft = client.mouse.wasLeftButtonClicked();
        if (isLeft && !wasLeftPressed) {
            leftClicks[leftIndex] = System.currentTimeMillis();
            leftIndex = (leftIndex + 1) % leftClicks.length;
        }
        wasLeftPressed = isLeft;

        // Sağ Tık
        boolean isRight = client.mouse.wasRightButtonClicked();
        if (isRight && !wasRightPressed) {
            rightClicks[rightIndex] = System.currentTimeMillis();
            rightIndex = (rightIndex + 1) % rightClicks.length;
        }
        wasRightPressed = isRight;
    }

    private int getCPS(long[] clicks) {
        long time = System.currentTimeMillis();
        int cps = 0;
        for (long t : clicks) {
            if (time - t < 1000) cps++;
        }
        return cps;
    }
}