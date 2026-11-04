package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.InspectCharacterOutcome;
import dev.kage.protocol.InspectCharacterRequest;
import dev.kage.protocol.InspectCharacterResponse;
import dev.kage.protocol.InspectedEquipment;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetInspectPrivacyCommand;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Soi trang bị người khác (ADR-171) — lệnh chat {@code /soi <tên>} (hoặc {@code /inspect <tên>}) mở cửa sổ: cảnh giới,
 * Lực Chiến, từng món đang mặc (cường hoá, ngọc). Server quyết người đó có cho xem không. Công tắc "cho người khác soi
 * mình" nằm trong {@code /caidat} — {@link #setAllowed} gửi nó lên server.
 */
final class InspectCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/soi", "/inspect");
    private static final String FACTION_PREFIX = "FACTION_";
    private static final String SLOT_PREFIX = "EQUIPMENT_SLOT_";
    private static final int MAX_ROWS = 20;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    InspectCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        chat.addSlashCommands(this::onSlash);
    }

    static InspectCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new InspectCoordinator(
                new ReportPanel(skin, "inspect.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
    }

    ReportPanel panel() {
        return panel;
    }

    /** Công tắc trong {@code /caidat}: {@code true} = cho người khác soi mình. */
    void setAllowed(boolean allowed) {
        sender.sendReadRequest(
                Opcode.OPCODE_SET_INSPECT_PRIVACY_COMMAND_VALUE,
                SetInspectPrivacyCommand.newBuilder().setHidden(!allowed).build());
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(command)) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(text("inspect.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_INSPECT_CHARACTER_REQUEST_VALUE,
                InspectCharacterRequest.newBuilder().setName(words[1].strip()).build());
        return true;
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
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_SET_INSPECT_PRIVACY_RESPONSE_VALUE) {
            return true; // công tắc đã lưu — không cần báo gì
        }
        if (opcode != Opcode.OPCODE_INSPECT_CHARACTER_RESPONSE_VALUE) {
            return false;
        }
        InspectCharacterResponse response = InspectCharacterResponse.parseFrom(frame.payload());
        if (response.getOutcome() != InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_OK) {
            chat.showSystemNotice(describeRefusal(i18n, response));
            return true;
        }
        panel.setStatus(header(i18n, response));
        panel.setRows(rows(response));
        if (!panel.isOpen()) {
            panel.open();
        }
        return true;
    }

    /** Dòng hệ thống khi không xem được — tách ra để test không cần màn hình. */
    static String describeRefusal(I18NBundle i18n, InspectCharacterResponse response) {
        return response.getOutcome() == InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_HIDDEN
                ? KageI18n.formatOrKey(i18n, "inspect.hidden", response.getName())
                : KageI18n.textOrKey(i18n, "inspect.not_found");
    }

    /** "Tên — Trúc Cơ tầng 2 — Hoả — Lực Chiến 1234" — tách ra để test không cần màn hình. */
    static String header(I18NBundle i18n, InspectCharacterResponse response) {
        CultivationRealm realm = CultivationRealm.of(response.getLevel());
        String realmName = KageI18n.textOrKey(i18n, realm.nameKey());
        String realmText = realm.perfected()
                ? KageI18n.formatOrKey(i18n, "realm.perfect", realmName)
                : KageI18n.formatOrKey(i18n, "realm.tier_short", realmName, realm.tier());
        String faction = response.getFaction().name();
        String factionKey = "faction."
                + (faction.startsWith(FACTION_PREFIX) ? faction.substring(FACTION_PREFIX.length()) : faction)
                        .toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(
                i18n,
                "inspect.header",
                response.getName(),
                realmText,
                KageI18n.textOrKey(i18n, factionKey),
                response.getCombatPower());
    }

    private List<ReportPanel.Row> rows(InspectCharacterResponse response) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (InspectedEquipment item : response.getEquipmentList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            rows.add(new ReportPanel.Row(equipmentLine(item), false));
        }
        if (rows.isEmpty()) {
            rows.add(new ReportPanel.Row(text("inspect.no_equipment"), false));
        }
        return rows;
    }

    private String equipmentLine(InspectedEquipment item) {
        EquipmentSlot slot = EquipmentSlot.forNumber(item.getSlot());
        String slotName = slot == null
                ? "?"
                : text("equipment.slot."
                        + slot.name().substring(SLOT_PREFIX.length()).toLowerCase(Locale.ROOT));
        String name =
                nameOf(item.getItemTemplateId()) + (item.getEnhanceLevel() > 0 ? " +" + item.getEnhanceLevel() : "");
        return item.getSocketGemTemplateId() != 0L
                ? KageI18n.formatOrKey(i18n, "inspect.row_gem", slotName, name, nameOf(item.getSocketGemTemplateId()))
                : KageI18n.formatOrKey(i18n, "inspect.row", slotName, name);
    }

    private String nameOf(long itemTemplateId) {
        return itemCatalog.nameKey(itemTemplateId).map(this::text).orElseGet(() -> "#" + itemTemplateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
    }
}
