package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.DisplaySettings;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cài đặt hiển thị (ADR-143) — cửa sổ mở bằng lệnh chat {@code /caidat} (hoặc {@code /settings}), mỗi công tắc một dòng
 * kèm nút "Bật/Tắt". Thuần client: không gói nào, lưu trên máy qua {@link DisplaySettings}.
 */
final class DisplaySettingsCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/caidat", "/settings");

    private final ReportPanel panel;
    private final DisplaySettings settings;
    private final I18NBundle i18n = KageI18n.load();

    DisplaySettingsCoordinator(ReportPanel panel, DisplaySettings settings, ChatCoordinator chat) {
        this.panel = panel;
        this.settings = settings;
        panel.setOnRefresh(this::showRows);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(KageI18n.textOrKey(i18n, "settings.hint"));
        chat.addSlashCommands(this::onSlash);
    }

    DisplaySettings settings() {
        return settings;
    }

    /** Nối công tắc vào từng lớp vẽ — mỗi lớp tự hỏi công tắc của mình mỗi khung. */
    void applyTo(RealmTitles titles, CombatFeedbackPresenter feedback, HorizonBackdrop backdrop) {
        titles.useSwitch(settings.switchFor(DisplaySettings.Option.REALM_TITLES));
        feedback.useSwitch(settings.switchFor(DisplaySettings.Option.DAMAGE_NUMBERS));
        backdrop.useSwitch(settings.switchFor(DisplaySettings.Option.PARALLAX));
    }

    private boolean onSlash(String line) {
        if (!COMMANDS.contains(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        panel.open();
        showRows();
        return true;
    }

    private void onRowAction(int rowIndex) {
        DisplaySettings.Option[] options = DisplaySettings.Option.values();
        if (rowIndex < 0 || rowIndex >= options.length) {
            return;
        }
        settings.toggle(options[rowIndex]);
        showRows();
    }

    private void showRows() {
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (DisplaySettings.Option option : DisplaySettings.Option.values()) {
            boolean on = settings.isOn(option);
            String line = KageI18n.formatOrKey(
                    i18n,
                    "settings.row",
                    KageI18n.textOrKey(i18n, "settings." + option.key()),
                    KageI18n.textOrKey(i18n, on ? "settings.state.on" : "settings.state.off"));
            rows.add(new ReportPanel.Row(line, false, on ? "settings.action.off" : "settings.action.on"));
        }
        panel.setRows(rows);
    }

    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
