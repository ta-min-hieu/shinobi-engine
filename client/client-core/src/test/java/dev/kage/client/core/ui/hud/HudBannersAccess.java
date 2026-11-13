package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.graphics.Color;

/** Cửa cho test ngoài gói gọi {@code HudBanners.latencyColor} (package-private). */
public final class HudBannersAccess {

    private HudBannersAccess() {}

    /** Màu nhãn độ trễ cho {@code roundTripMs}. */
    public static Color color(long roundTripMs) {
        return HudBanners.latencyColor(roundTripMs);
    }
}
