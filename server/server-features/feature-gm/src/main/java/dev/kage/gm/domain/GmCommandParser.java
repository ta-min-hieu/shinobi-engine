package dev.kage.gm.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Parse một dòng chat bắt đầu bằng {@code /gm} thành {@link GmCommand} (ADR-052 D1/D4/D8). Hàm THUẦN —
 * không DB, không trạng thái. Số parse chặt trong biên; sai ⇒ {@link Parsed.Invalid} kèm cú pháp đúng.
 */
public final class GmCommandParser {

    /** Tiền tố nhận diện, không phân biệt hoa thường. */
    public static final String PREFIX = "/gm";

    /** Biên D4 (Q5 giữ theo khuyến nghị). */
    public static final int MAX_MUTE_MINUTES = 10_080;

    public static final int MAX_GIVE_QUANTITY = 99;
    public static final long MAX_GOLD = 1_000_000L;
    public static final int MAX_ANNOUNCE_LENGTH = 200;

    private GmCommandParser() {}

    /** Kết quả parse. */
    public sealed interface Parsed {
        record Ok(GmCommand command) implements Parsed {}

        /** {@code usage}: chữ trả cho GM (tiếng Anh, ASCII — D1/Q4). */
        record Invalid(String usage) implements Parsed {}
    }

    /** Dòng này có phải lệnh GM không — {@code /gm} trơn hoặc {@code /gm <...>}. */
    public static boolean isGmLine(String body) {
        String stripped = body.strip();
        if (stripped.length() < PREFIX.length() || !stripped.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return false;
        }
        return stripped.length() == PREFIX.length() || Character.isWhitespace(stripped.charAt(PREFIX.length()));
    }

    public static Parsed parse(String body) {
        String rest = body.strip().substring(PREFIX.length()).strip();
        if (rest.isEmpty()) {
            return new Parsed.Ok(new GmCommand.Help());
        }
        String[] words = rest.split("\\s+");
        String verb = words[0].toLowerCase(Locale.ROOT);
        if ("announce".equals(verb)) {
            return announce(rest);
        }
        Function<String[], Parsed> handler = COMMANDS.get(verb);
        return handler == null
                ? new Parsed.Invalid("Unknown GM command '" + verb + "'. Try /gm help")
                : handler.apply(words);
    }

    // Bảng tra thay switch (trần CyclomaticComplexity của PMD) — đây cũng chính là WHITELIST của D8.
    private static final Map<String, Function<String[], Parsed>> COMMANDS = Map.ofEntries(
            Map.entry("help", words -> new Parsed.Ok(new GmCommand.Help())),
            Map.entry("who", words -> oneTarget(words, "who <name>", GmCommand.Who::new)),
            Map.entry("kick", words -> oneTarget(words, "kick <name>", GmCommand.Kick::new)),
            Map.entry("unmute", words -> oneTarget(words, "unmute <name>", GmCommand.Unmute::new)),
            Map.entry("heal", words -> oneTarget(words, "heal <name>", GmCommand.Heal::new)),
            Map.entry("goto", words -> oneTarget(words, "goto <name>", GmCommand.Goto::new)),
            Map.entry("summon", words -> oneTarget(words, "summon <name>", GmCommand.Summon::new)),
            Map.entry("mute", GmCommandParser::mute),
            Map.entry("give", GmCommandParser::give),
            Map.entry("gold", GmCommandParser::gold),
            Map.entry("inspect", words -> oneTarget(words, "inspect <name>", GmCommand.Inspect::new)),
            Map.entry("event", GmCommandParser::event));

    private static Parsed oneTarget(String[] words, String usage, Function<String, GmCommand> make) {
        return words.length == 2 ? new Parsed.Ok(make.apply(words[1])) : invalid(usage);
    }

    private static Parsed mute(String[] words) {
        if (words.length != 3) {
            return invalid("mute <name> <minutes 1-" + MAX_MUTE_MINUTES + ">");
        }
        int minutes = parseInt(words[2]);
        return minutes >= 1 && minutes <= MAX_MUTE_MINUTES
                ? new Parsed.Ok(new GmCommand.Mute(words[1], minutes))
                : invalid("mute <name> <minutes 1-" + MAX_MUTE_MINUTES + ">");
    }

    private static Parsed give(String[] words) {
        String usage = "give <name> <item_code> <quantity 1-" + MAX_GIVE_QUANTITY + ">";
        if (words.length != 4) {
            return invalid(usage);
        }
        int quantity = parseInt(words[3]);
        return quantity >= 1 && quantity <= MAX_GIVE_QUANTITY && isItemCode(words[2])
                ? new Parsed.Ok(new GmCommand.Give(words[1], words[2], quantity))
                : invalid(usage);
    }

    private static Parsed gold(String[] words) {
        String usage = "gold <name> <amount 1-" + MAX_GOLD + ">";
        if (words.length != 3) {
            return invalid(usage);
        }
        long amount = parseLong(words[2]);
        return amount >= 1 && amount <= MAX_GOLD ? new Parsed.Ok(new GmCommand.Gold(words[1], amount)) : invalid(usage);
    }

    private static Parsed event(String[] words) {
        String usage = "event <exp|drop> <multiplier " + GameEvent.MIN_MULTIPLIER + "-" + GameEvent.MAX_MULTIPLIER
                + "> <minutes 1-" + MAX_MUTE_MINUTES + "> | event <exp|drop> off";
        Optional<GameEventKind> parsedKind = words.length >= 2 ? eventKind(words[1]) : Optional.empty();
        if (parsedKind.isEmpty()) {
            return invalid(usage);
        }
        GameEventKind kind = parsedKind.get();
        if (words.length == 3 && "off".equalsIgnoreCase(words[2])) {
            return new Parsed.Ok(new GmCommand.Event(kind, 0, 0));
        }
        if (words.length != 4) {
            return invalid(usage);
        }
        int multiplier = parseInt(words[2]);
        int minutes = parseInt(words[3]);
        boolean valid = multiplier >= GameEvent.MIN_MULTIPLIER
                && multiplier <= GameEvent.MAX_MULTIPLIER
                && minutes >= 1
                && minutes <= MAX_MUTE_MINUTES;
        return valid ? new Parsed.Ok(new GmCommand.Event(kind, multiplier, minutes)) : invalid(usage);
    }

    private static Optional<GameEventKind> eventKind(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "exp" -> Optional.of(GameEventKind.EXP);
            case "drop" -> Optional.of(GameEventKind.DROP);
            default -> Optional.empty();
        };
    }

    private static Parsed announce(String rest) {
        String text = rest.substring("announce".length()).strip();
        if (text.isEmpty() || text.length() > MAX_ANNOUNCE_LENGTH) {
            return invalid("announce <text, 1-" + MAX_ANNOUNCE_LENGTH + " chars>");
        }
        return new Parsed.Ok(new GmCommand.Announce(text));
    }

    private static boolean isItemCode(String code) {
        return code.length() <= 32
                && code.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_');
    }

    private static Parsed invalid(String usage) {
        return new Parsed.Invalid("Usage: /gm " + usage);
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
