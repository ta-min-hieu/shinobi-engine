package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BuyReputationItemCommand;
import dev.kage.protocol.BuyReputationItemResponse;
import dev.kage.protocol.GetReputationRequest;
import dev.kage.protocol.GetReputationResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ReputationShopItemView;

/**
 * Tab "Danh vọng" của cửa sổ Hằng ngày (ADR-109 D5) — bậc + điểm, mỗi món cửa hàng danh vọng một dòng có nút Đổi khi
 * đủ bậc. Server quyết bậc, điểm và trao vật phẩm; client chỉ hiển thị. Cùng khuôn {@link ActivityTab}.
 */
final class ReputationTab {

    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    ReputationTab(WorldCommandSender sender) {
        this.sender = sender;
    }

    void request() {
        sender.sendReadRequest(Opcode.OPCODE_GET_REPUTATION_REQUEST_VALUE, GetReputationRequest.getDefaultInstance());
    }

    /** Gói thuộc tab này; {@code true} nếu đã xử lý. */
    boolean dispatch(int opcode, byte[] payload, Consumer<ActivityTab.Rendered> show, Consumer<String> notice)
            throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_GET_REPUTATION_RESPONSE_VALUE) {
            show.accept(render(GetReputationResponse.parseFrom(payload)));
            return true;
        }
        if (opcode == Opcode.OPCODE_BUY_REPUTATION_ITEM_RESPONSE_VALUE) {
            BuyReputationItemResponse response = BuyReputationItemResponse.parseFrom(payload);
            String name = response.getOutcome().name();
            notice.accept(text("reputation.buy."
                    + name.substring(name.indexOf("OUTCOME_") + "OUTCOME_".length())
                            .toLowerCase(Locale.ROOT)));
            show.accept(render(response.getStatus()));
            return true;
        }
        return false;
    }

    private ActivityTab.Rendered render(GetReputationResponse status) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (ReputationShopItemView item : status.getItemsList()) {
            boolean unlocked = status.getTier() >= item.getRequiredTier();
            long templateId = item.getItemTemplateId();
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            "reputation.row",
                            text("item." + item.getItemCode() + ".name"),
                            item.getPrice(),
                            text("reputation.tier." + item.getRequiredTier())),
                    false,
                    unlocked ? "reputation.action.buy" : ""));
            actions.add(() -> sender.sendReadRequest(
                    Opcode.OPCODE_BUY_REPUTATION_ITEM_COMMAND_VALUE,
                    BuyReputationItemCommand.newBuilder()
                            .setItemTemplateId(templateId)
                            .build()));
        }
        String next = status.getNextTierAt() == 0L
                ? text("reputation.max_tier")
                : KageI18n.formatOrKey(i18n, "reputation.next_tier", status.getNextTierAt());
        String hint = KageI18n.formatOrKey(
                i18n,
                "reputation.hint",
                text("reputation.tier." + status.getTier()),
                status.getEarned(),
                status.getAvailable(),
                next);
        return new ActivityTab.Rendered(rows, actions, hint);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }
}
