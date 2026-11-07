package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BuybackCommand;
import dev.kage.protocol.BuybackOutcome;
import dev.kage.protocol.BuybackResponse;
import dev.kage.protocol.BuybackView;
import dev.kage.protocol.ListBuybackRequest;
import dev.kage.protocol.ListBuybackResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Mua lại đồ đã bán (ADR-202) — {@code /mualai} (hoặc {@code /buyback}) liệt kê món bán trong 1 giờ qua, đánh số;
 * {@code /mualai <số>} mua lại món số đó với đúng giá đã nhận. Server giữ số liệu thật (giá, hạn, cấp cường hoá).
 */
final class BuybackCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/mualai";
    private static final String COMMAND_EN = "/buyback";
    private static final String OUTCOME_PREFIX = "BUYBACK_OUTCOME_";
    private static final long MINUTE = 60L;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator catalog;
    private final I18NBundle i18n = KageI18n.load();
    /** Danh sách lần hỏi gần nhất — số thứ tự người chơi gõ ứng với vị trí ở đây. */
    private final List<BuybackView> shown = new ArrayList<>();

    BuybackCoordinator(WorldCommandSender sender, ChatCoordinator chat, ItemCatalogCoordinator catalog) {
        this.sender = sender;
        this.chat = chat;
        this.catalog = catalog;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        if (words.length == 1) {
            sender.sendReadRequest(Opcode.OPCODE_LIST_BUYBACK_REQUEST_VALUE, ListBuybackRequest.getDefaultInstance());
            return true;
        }
        int index = parseIndex(words[1].strip());
        if (index < 1 || index > shown.size()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "buyback.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_BUYBACK_COMMAND_VALUE,
                BuybackCommand.newBuilder()
                        .setEntryId(shown.get(index - 1).getEntryId())
                        .build());
        return true;
    }

    private static int parseIndex(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_LIST_BUYBACK_RESPONSE_VALUE) {
            show(ListBuybackResponse.parseFrom(frame.payload()).getEntriesList());
            return true;
        }
        if (opcode == Opcode.OPCODE_BUYBACK_RESPONSE_VALUE) {
            BuybackResponse response = BuybackResponse.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.textOrKey(i18n, outcomeKey(response.getOutcome())));
            show(response.getEntriesList());
            if (response.getOutcome() == BuybackOutcome.BUYBACK_OUTCOME_OK) {
                sender.listInventory(); // món vừa về túi
            }
            return true;
        }
        return false;
    }

    private void show(List<BuybackView> entries) {
        shown.clear();
        shown.addAll(entries);
        if (entries.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "buyback.empty"));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            BuybackView entry = entries.get(i);
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "buyback.row",
                    i + 1,
                    describe(entry),
                    entry.getPrice(),
                    Math.max(1L, entry.getSecondsLeft() / MINUTE)));
        }
    }

    private String describe(BuybackView entry) {
        String name = catalog.nameKey(entry.getItemTemplateId())
                .map(key -> KageI18n.textOrKey(i18n, key))
                .orElseGet(() -> "#" + entry.getItemTemplateId());
        String enhance = entry.getEnhanceLevel() > 0 ? " +" + entry.getEnhanceLevel() : "";
        String count = entry.getQuantity() > 1 ? " x" + entry.getQuantity() : "";
        return name + enhance + count;
    }

    /** Khoá i18n của một kết quả — tĩnh để test đối chiếu bundle. */
    static String outcomeKey(BuybackOutcome outcome) {
        String suffix = outcome == BuybackOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return "buyback.outcome." + suffix;
    }
}
