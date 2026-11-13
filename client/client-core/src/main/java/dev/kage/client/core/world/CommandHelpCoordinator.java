package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Danh sách lệnh chat (ADR-267) — {@code /trogiup} (hoặc {@code /help}) in mọi nhóm lệnh; {@code /trogiup <từ khoá>}
 * chỉ in nhóm có tên khớp hoặc những lệnh chứa từ khoá (so không dấu). Bảng tĩnh ở {@link #GROUPS} — thêm lệnh mới
 * thì thêm vào đây (test {@code Lot42ClientTest} giữ bảng không trùng lệnh).
 */
final class CommandHelpCoordinator implements WindowFlow {

    /** Một nhóm lệnh: khoá i18n tên nhóm + các lệnh (tên tiếng Việt trước, tên tiếng Anh nếu có). */
    record Group(String titleKey, List<String> commands) {}

    static final List<Group> GROUPS = List.of(
            new Group(
                    "help.group.chat",
                    List.of(
                            "/tinrieng",
                            "/whispers",
                            "/ghimchat",
                            "/pinchat",
                            "/chatan",
                            "/chatfade",
                            "/cochu",
                            "/fontsize",
                            "/w",
                            "/r",
                            "/reply",
                            "/kenh",
                            "/channels",
                            "/kenhgd",
                            "/tradechat",
                            "/loa",
                            "/shout",
                            "/khoe",
                            "/show",
                            "/locchu",
                            "/mute",
                            "/away",
                            "/vang",
                            "/dnd",
                            "/khonglamphien",
                            "/giochat",
                            "/timestamps",
                            "/alias",
                            "/bietdanh")),
            new Group(
                    "help.group.emote", List.of("/chao", "/cui", "/cuoi", "/khoc", "/gian", "/vui", "/gieo", "/roll")),
            new Group(
                    "help.group.friend",
                    List.of(
                            "/thich",
                            "/like",
                            "/ketban",
                            "/addfriend",
                            "/quanhe",
                            "/ghichu",
                            "/note",
                            "/goiyban",
                            "/suggestfriends",
                            "/xephangban",
                            "/friendrank",
                            "/chucmung",
                            "/congrats",
                            "/cuunhan",
                            "/enemies",
                            "/chanthu",
                            "/blockmail",
                            "/chanloimoi",
                            "/blockinvites",
                            "/yeuthich",
                            "/favorite")),
            new Group(
                    "help.group.party",
                    List.of(
                            "/chiasevitri",
                            "/sharepos",
                            "/tunhannhom",
                            "/autoparty",
                            "/nhuongnhom",
                            "/partyleader",
                            "/danhdau",
                            "/mark",
                            "/theodau",
                            "/assist",
                            "/satthuong",
                            "/dps",
                            "/sansang",
                            "/readycheck",
                            "/demnguoc",
                            "/countdown",
                            "/chiavang",
                            "/splitgold")),
            new Group(
                    "help.group.guild",
                    List.of(
                            "/conghien",
                            "/contrib",
                            "/vaobang",
                            "/moibang",
                            "/ginvite",
                            "/gaccept",
                            "/gdecline",
                            "/tuchoibang",
                            "/thongbaobang",
                            "/guildnotice",
                            "/nhatkybang",
                            "/guildlog",
                            "/thubang",
                            "/guildmail",
                            "/khobang",
                            "/guildbank",
                            "/gopdo",
                            "/donateitem",
                            "/diemdanhbang",
                            "/guildcheckin",
                            "/buffbang",
                            "/guildbuff",
                            "/lichbang",
                            "/guildevent",
                            "/binhchon",
                            "/poll",
                            "/chieumo",
                            "/recruit",
                            "/nhuongbang",
                            "/guildleader",
                            "/phobang",
                            "/vice",
                            "/bophobang",
                            "/unvice",
                            "/doitenbang",
                            "/guildrename",
                            "/lichsuchien",
                            "/warhistory",
                            "/khoett",
                            "/shareach",
                            "/baobang",
                            "/guildonline")),
            new Group(
                    "help.group.bag",
                    List.of(
                            "/timdo",
                            "/find",
                            "/tach",
                            "/split",
                            "/dungnhieu",
                            "/usemany",
                            "/huy",
                            "/destroy",
                            "/phangiai",
                            "/dismantleall",
                            "/banrac",
                            "/selljunk",
                            "/tubanrac",
                            "/autosell",
                            "/mualai",
                            "/buyback",
                            "/xemmon",
                            "/iteminfo",
                            "/ghepngoc",
                            "/gemcombine",
                            "/dutinh",
                            "/enhanceinfo",
                            "/lichsucuonghoa",
                            "/enhancelog",
                            "/preset",
                            "/giatri",
                            "/bagvalue")),
            new Group(
                    "help.group.mail",
                    List.of(
                            "/thudagui",
                            "/sentmail",
                            "/chuyentiep",
                            "/forward",
                            "/thuhengio",
                            "/mailat",
                            "/xoathu",
                            "/cleanmail",
                            "/ghimthu",
                            "/pinmail",
                            "/boghim",
                            "/unpinmail",
                            "/timthu",
                            "/findmail")),
            new Group(
                    "help.group.market",
                    List.of(
                            "/giacho",
                            "/pricecheck",
                            "/caumua",
                            "/theodoi",
                            "/watch",
                            "/lichsupt",
                            "/markethistory",
                            "/thuongnhan",
                            "/lichsugd",
                            "/tradehistory",
                            "/sovang",
                            "/goldlog")),
            new Group(
                    "help.group.quest",
                    List.of(
                            "/tieptheo",
                            "/next",
                            "/xemthuong",
                            "/rewards",
                            "/nhiemvutuan",
                            "/weekly",
                            "/walkto",
                            "/quetbicanh",
                            "/sweep",
                            "/lich",
                            "/calendar",
                            "/reset")),
            new Group(
                    "help.group.info",
                    List.of(
                            "/topboss",
                            "/topthich",
                            "/toplikes",
                            "/vatpham",
                            "/item",
                            "/lichsudau",
                            "/duels",
                            "/online",
                            "/bossdaha",
                            "/bosskills",
                            "/lichsumua",
                            "/purchases",
                            "/thongke",
                            "/stats",
                            "/kyluc",
                            "/records",
                            "/soi",
                            "/inspect",
                            "/xemquai",
                            "/monster",
                            "/lichsunhat",
                            "/lootlog",
                            "/lichsudangnhap",
                            "/loginlog",
                            "/nhatkychien",
                            "/combatlog",
                            "/mocgioi",
                            "/milestones",
                            "/phien",
                            "/session",
                            "/exph")),
            new Group(
                    "help.group.tools",
                    List.of(
                            "/giaodien",
                            "/uiscale",
                            "/fps",
                            "/combo",
                            "/bamgio",
                            "/stopwatch",
                            "/tinh",
                            "/calc",
                            "/trogiup",
                            "/help",
                            "/gio",
                            "/time",
                            "/ping",
                            "/toado",
                            "/coords",
                            "/luuvitri",
                            "/savespot",
                            "/vitri",
                            "/spot",
                            "/hengio",
                            "/remind",
                            "/so",
                            "/caidat",
                            "/settings",
                            "/phimtat",
                            "/keys",
                            "/chupanh",
                            "/photo")));

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    CommandHelpCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /**
     * Dòng in ra cho từ khoá {@code query} (rỗng = mọi nhóm): mỗi nhóm khớp một cặp (tiêu đề, lệnh). Nhóm có tiêu đề
     * khớp in hết lệnh; nhóm khác chỉ in lệnh chứa từ khoá. Tĩnh để test ({@code title} dịch khoá nhóm).
     */
    static List<String[]> matches(String query, java.util.function.UnaryOperator<String> title) {
        String folded = MentionWatch.fold(query.strip()).replace("/", "");
        List<String[]> lines = new ArrayList<>();
        for (Group group : GROUPS) {
            String name = title.apply(group.titleKey());
            List<String> hits = folded.isEmpty() || MentionWatch.fold(name).contains(folded)
                    ? group.commands()
                    : group.commands().stream()
                            .filter(command -> MentionWatch.fold(command).contains(folded))
                            .toList();
            if (!hits.isEmpty()) {
                lines.add(new String[] {name, String.join(" ", hits)});
            }
        }
        return lines;
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/trogiup".equals(command) && !"/help".equals(command)) {
            return false;
        }
        List<String[]> lines = matches(parts.length > 1 ? parts[1] : "", key -> KageI18n.textOrKey(i18n, key));
        if (lines.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "help.none"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "help.header"));
        for (String[] entry : lines) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "help.line", entry[0], entry[1]));
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
