package dev.kage.client.core.world;

import java.util.List;

import com.badlogic.gdx.Input;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageSkin;

/** Cửa sổ/luồng lô 23 (ADR-115…126) — tách khỏi {@link CommunityWindows} để nó không phình thêm. */
record ExpansionWindows(
        SecurityPinCoordinator securityPin,
        ReportPanel buyOrderPanel,
        BuyOrderCoordinator buyOrders,
        ReportPanel revivalPanel,
        RevivalCoordinator revival,
        ReportPanel merchantPanel,
        MerchantCoordinator merchant,
        ReportPanel fieldPanel,
        SpiritFieldCoordinator field,
        ReportPanel forgePanel,
        ForgeCoordinator forge) {

    static ExpansionWindows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        // Cầu mua (ADR-116) — không phím, mở bằng lệnh chat /caumua.
        ReportPanel buyOrderPanel =
                new ReportPanel(skin, "buyorder.title", Input.Keys.UNKNOWN, BuyOrderCoordinator.TAB_KEYS);
        // Hồi sinh (ADR-117) — không phím, server mở khi mình gục.
        ReportPanel revivalPanel = new ReportPanel(skin, "revive.title", Input.Keys.UNKNOWN, List.of());
        // Thương nhân du hành (ADR-118) — không phím, mở bằng lệnh chat /thuongnhan.
        ReportPanel merchantPanel = new ReportPanel(skin, "merchant.title", Input.Keys.UNKNOWN, List.of());
        // Linh điền (ADR-119) — không phím, mở bằng lệnh chat /linhdien.
        ReportPanel fieldPanel =
                new ReportPanel(skin, "field.title", Input.Keys.UNKNOWN, SpiritFieldCoordinator.TAB_KEYS);
        // Luyện khí (ADR-120) — không phím, mở bằng lệnh chat /luyenkhi.
        ReportPanel forgePanel = new ReportPanel(skin, "forge.title", Input.Keys.UNKNOWN, List.of());
        return new ExpansionWindows(
                new SecurityPinCoordinator(sender, feeds.chat()),
                buyOrderPanel,
                new BuyOrderCoordinator(buyOrderPanel, sender, feeds),
                revivalPanel,
                new RevivalCoordinator(revivalPanel, sender),
                merchantPanel,
                new MerchantCoordinator(merchantPanel, sender, feeds),
                fieldPanel,
                new SpiritFieldCoordinator(fieldPanel, sender, feeds),
                forgePanel,
                new ForgeCoordinator(forgePanel, sender, feeds));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(securityPin);
        panels.register(buyOrderPanel, buyOrders);
        panels.register(revivalPanel, revival);
        panels.register(merchantPanel, merchant);
        panels.register(fieldPanel, field);
        panels.register(forgePanel, forge);
    }

    /** Cổng phím chung ({@code PanelHub#noneOpen}). */
    void gateKeyOpening(java.util.function.BooleanSupplier noneOpen) {
        // chưa có cửa sổ mở bằng phím
    }
}
