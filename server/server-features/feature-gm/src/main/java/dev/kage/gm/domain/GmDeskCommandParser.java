package dev.kage.gm.domain;

import java.util.Arrays;

/**
 * Parse các lệnh "bàn trực" lô 22 ({@link GmCommand.Desk}) — tách khỏi {@link GmCommandParser} để lớp đó không vượt
 * trần độ phức tạp PMD. Hàm THUẦN, chỉ {@link GmCommandParser} gọi.
 */
final class GmDeskCommandParser {

    /** Trần lượt dùng một mã quà. */
    static final int MAX_CODE_USES = 100_000;

    /** Trần số ngày hiệu lực một mã quà. */
    static final int MAX_CODE_DAYS = 365;

    /** Ký hiệu "không có vật phẩm" trong {@code /gm code}. */
    private static final String NO_ITEM = "-";

    private static final int CODE_ARGS = 7;

    private GmDeskCommandParser() {}

    /** {@code motd <chữ>} hoặc {@code motd off}. */
    static GmCommandParser.Parsed motd(String[] words) {
        String usage = "motd <text, 1-300 chars> | motd off";
        if (words.length < 2) {
            return GmCommandParser.invalid(usage);
        }
        String text = String.join(" ", Arrays.copyOfRange(words, 1, words.length));
        if (words.length == 2 && "off".equalsIgnoreCase(words[1])) {
            return new GmCommandParser.Parsed.Ok(new GmCommand.Motd(""));
        }
        return text.length() <= ServerNoticePort.MAX_LENGTH
                ? new GmCommandParser.Parsed.Ok(new GmCommand.Motd(text))
                : GmCommandParser.invalid(usage);
    }

    /** {@code code <MÃ> <vàng> <item_code|-> <số lượng> <lượt> <ngày>}. */
    static GmCommandParser.Parsed code(String[] words) {
        String usage = "code <CODE> <gold> <item_code|-> <quantity 0-" + GmCommandParser.MAX_GIVE_QUANTITY
                + "> <uses 1-" + MAX_CODE_USES + "> <days 1-" + MAX_CODE_DAYS + ">";
        if (words.length != CODE_ARGS) {
            return GmCommandParser.invalid(usage);
        }
        String code = GiftCode.normalize(words[1]);
        long gold = GmCommandParser.parseLong(words[2]);
        String itemCode = NO_ITEM.equals(words[3]) ? "" : words[3];
        int quantity = GmCommandParser.parseInt(words[4]);
        int uses = GmCommandParser.parseInt(words[5]);
        int days = GmCommandParser.parseInt(words[6]);
        boolean amountsValid = gold >= 0
                && gold <= GmCommandParser.MAX_GOLD
                && quantity >= 0
                && quantity <= GmCommandParser.MAX_GIVE_QUANTITY;
        boolean rewardPresent = gold > 0 || (!itemCode.isEmpty() && quantity > 0);
        boolean itemValid = itemCode.isEmpty() || GmCommandParser.isItemCode(itemCode);
        boolean limitsValid = uses >= 1 && uses <= MAX_CODE_USES && days >= 1 && days <= MAX_CODE_DAYS;
        if (!GiftCode.isWellFormed(code) || !amountsValid || !rewardPresent || !itemValid || !limitsValid) {
            return GmCommandParser.invalid(usage);
        }
        return new GmCommandParser.Parsed.Ok(new GmCommand.CreateGiftCode(code, gold, itemCode, quantity, uses, days));
    }

    /** Trần cấp tối thiểu trong {@code /gm compensate}. */
    static final int MAX_LEVEL = 100;

    private static final int COMPENSATE_ARGS = 6;

    /** ADR-165 — {@code compensate <MÃ> <vàng> <item_code|-> <số lượng> <cấp tối thiểu>}. */
    static GmCommandParser.Parsed compensate(String[] words) {
        String usage = "compensate <CODE> <gold> <item_code|-> <quantity 0-" + GmCommandParser.MAX_GIVE_QUANTITY
                + "> <min level 1-" + MAX_LEVEL + ">";
        if (words.length != COMPENSATE_ARGS) {
            return GmCommandParser.invalid(usage);
        }
        String code = GiftCode.normalize(words[1]);
        long gold = GmCommandParser.parseLong(words[2]);
        String itemCode = NO_ITEM.equals(words[3]) ? "" : words[3];
        int quantity = GmCommandParser.parseInt(words[4]);
        int minLevel = GmCommandParser.parseInt(words[5]);
        boolean amountsValid = gold >= 0
                && gold <= GmCommandParser.MAX_GOLD
                && quantity >= 0
                && quantity <= GmCommandParser.MAX_GIVE_QUANTITY;
        boolean rewardPresent = gold > 0 || (!itemCode.isEmpty() && quantity > 0);
        boolean itemValid = itemCode.isEmpty() || GmCommandParser.isItemCode(itemCode);
        boolean levelValid = minLevel >= 1 && minLevel <= MAX_LEVEL;
        if (!GiftCode.isWellFormed(code) || !amountsValid || !rewardPresent || !itemValid || !levelValid) {
            return GmCommandParser.invalid(usage);
        }
        return new GmCommandParser.Parsed.Ok(new GmCommand.Compensate(code, gold, itemCode, quantity, minLevel));
    }

    /** {@code report <id>} / {@code resolve <id>}. */
    static GmCommandParser.Parsed reportId(String[] words, boolean resolve) {
        String usage = (resolve ? "resolve" : "report") + " <report id>";
        long id = words.length == 2 ? GmCommandParser.parseLong(words[1]) : -1L;
        if (id <= 0) {
            return GmCommandParser.invalid(usage);
        }
        return new GmCommandParser.Parsed.Ok(resolve ? new GmCommand.ResolveReport(id) : new GmCommand.ShowReport(id));
    }
}
