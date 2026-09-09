package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.samples.g2d.platformer.PlatformerGame;

/** Retained presentation state. Text is rebuilt only when its displayed values change. */
public final class PlatformerHud {
    public String title="LIBFDX TRAILS", score="COINS 0/0", status="LOADING LEVEL", heading="LOADING";
    public boolean menuOpen, loading=true, failed, audioAvailable, audioLocked;
    public int volume=7;
    private int lastCoins=-1,lastTotal=-1;
    public void game(PlatformerGame game) {
        if (game==null) return;
        int coins=game.coinsCollected(),total=game.coinTotal();
        if (coins!=lastCoins || total!=lastTotal) { lastCoins=coins; lastTotal=total; score="COINS "+coins+"/"+total; }
    }
}
