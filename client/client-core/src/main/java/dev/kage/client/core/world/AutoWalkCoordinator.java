package dev.kage.client.core.world;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ListNpcLocationsRequest;
import dev.kage.protocol.ListNpcLocationsResponse;
import dev.kage.protocol.NpcLocation;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tự tìm đường (ADR-206) — {@code /den <tên NPC>} (hoặc {@code /walkto}) hỏi server chỗ đứng NPC ở world chính rồi
 * cho {@link LocalIntentSender} tự đi tới chỗ gần nhất; không kèm tên thì liệt kê NPC. Tên so khớp không dấu, một phần
 * ({@code /den chu hoanh}). Bấm phím đi là huỷ.
 */
final class AutoWalkCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/den";
    private static final String COMMAND_EN = "/walkto";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final AutoWalkTarget target = new AutoWalkTarget();
    private final I18NBundle i18n = KageI18n.load();

    /** Tên đang chờ server trả vị trí; rỗng = chỉ liệt kê. */
    private String pendingQuery = "";

    AutoWalkCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Đích tự đi dùng chung với {@link LocalIntentSender#useAutoWalk}. */
    AutoWalkTarget target() {
        return target;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        pendingQuery = words.length == 1 ? "" : fold(words[1]);
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_NPC_LOCATIONS_REQUEST_VALUE, ListNpcLocationsRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_LIST_NPC_LOCATIONS_RESPONSE_VALUE) {
            return false;
        }
        ListNpcLocationsResponse response = ListNpcLocationsResponse.parseFrom(frame.payload());
        if (!response.getInMainWorld()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "autowalk.not_main_world"));
        } else if (pendingQuery.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autowalk.list", String.join(", ", names(response))));
        } else {
            walkTo(response.getLocationsList());
        }
        return true;
    }

    private void walkTo(List<NpcLocation> locations) {
        Optional<NpcLocation> nearest = nearestMatch(locations, pendingQuery, target.lastKnownX(), this::nameOf);
        if (nearest.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autowalk.not_found", pendingQuery));
            return;
        }
        String name = nameOf(nearest.get().getNpcCode());
        float x = nearest.get().getX();
        target.stop();
        target.start(
                x,
                () -> chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autowalk.arrived", name)),
                () -> chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autowalk.stopped", name)));
        int meters = Math.round(Math.abs(x - target.lastKnownX()));
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autowalk.walking", name, meters));
    }

    private List<String> names(ListNpcLocationsResponse response) {
        Set<String> names = new LinkedHashSet<>();
        response.getLocationsList().forEach(location -> names.add(nameOf(location.getNpcCode())));
        return new ArrayList<>(names);
    }

    private String nameOf(String npcCode) {
        return KageI18n.textOrKey(i18n, "npc." + npcCode + ".name");
    }

    /**
     * Chỗ đứng gần {@code fromX} nhất trong các NPC có tên (đã bỏ dấu) chứa {@code query}, hoặc mã NPC trùng khớp.
     * Tĩnh để kiểm thử không cần libGDX.
     */
    static Optional<NpcLocation> nearestMatch(
            List<NpcLocation> locations, String query, float fromX, java.util.function.Function<String, String> names) {
        NpcLocation best = null;
        for (NpcLocation location : locations) {
            String code = location.getNpcCode();
            boolean matches =
                    code.equalsIgnoreCase(query) || fold(names.apply(code)).contains(query);
            if (matches && (best == null || Math.abs(location.getX() - fromX) < Math.abs(best.getX() - fromX))) {
                best = location;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Chữ thường, bỏ dấu tiếng Việt (kể cả đ), gộp khoảng trắng. */
    static String fold(String text) {
        String decomposed = Normalizer.normalize(text.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("\\s+", " ");
    }
}
