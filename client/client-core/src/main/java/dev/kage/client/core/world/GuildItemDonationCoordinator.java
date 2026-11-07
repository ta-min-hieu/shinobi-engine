package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DonateItemToGuildCommand;
import dev.kage.protocol.DonateItemToGuildOutcome;
import dev.kage.protocol.DonateItemToGuildResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Góp vật phẩm cho bang (ADR-207) — chọn một món trong túi rồi gõ {@code /gopdo [số]} (hoặc {@code /donateitem});
 * không số = 1. Món biến mất, bang nhận kinh nghiệm và mình nhận cống hiến bằng giá thu của Tàng Bảo Các.
 */
final class GuildItemDonationCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/gopdo";
    private static final String COMMAND_EN = "/donateitem";
    private static final String OUTCOME_PREFIX = "DONATE_ITEM_TO_GUILD_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<Long>> selection = Optional::empty;

    GuildItemDonationCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối món đang chọn trong túi đồ ({@code InventoryCoordinator}). */
    void useSelection(Supplier<Optional<Long>> selection) {
        this.selection = selection;
    }

    /** Số lượng từ đối số; rỗng ⇒ 1; không phải số dương (tối đa 4 chữ số) ⇒ rỗng. Tĩnh để test. */
    static Optional<Integer> parseQuantity(String argument) {
        String trimmed = argument.strip();
        if (trimmed.isEmpty()) {
            return Optional.of(1);
        }
        if (!trimmed.matches("[0-9]{1,4}") || Integer.parseInt(trimmed) == 0) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(trimmed));
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<Integer> quantity = parseQuantity(words.length > 1 ? words[1] : "");
        Optional<Long> item = selection.get();
        if (quantity.isEmpty() || item.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guild_item_donation.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_DONATE_ITEM_TO_GUILD_COMMAND_VALUE,
                DonateItemToGuildCommand.newBuilder()
                        .setInventoryItemId(item.get())
                        .setQuantity(quantity.get())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_DONATE_ITEM_TO_GUILD_RESPONSE_VALUE) {
            return false;
        }
        DonateItemToGuildResponse response = DonateItemToGuildResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, response));
        if (response.getOutcome() == DonateItemToGuildOutcome.DONATE_ITEM_TO_GUILD_OUTCOME_OK) {
            sender.listInventory();
        }
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. Kết quả lạ hiện câu chung. */
    static String describe(I18NBundle i18n, DonateItemToGuildResponse response) {
        DonateItemToGuildOutcome outcome = response.getOutcome();
        if (outcome == DonateItemToGuildOutcome.DONATE_ITEM_TO_GUILD_OUTCOME_OK) {
            String ok = KageI18n.formatOrKey(i18n, "guild_item_donation.ok", response.getContribution());
            return response.getGuildLeveledUp()
                    ? ok + " " + KageI18n.textOrKey(i18n, "guild_item_donation.leveled_up")
                    : ok;
        }
        String key = outcome == DonateItemToGuildOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "guild_item_donation." + key);
    }
}
