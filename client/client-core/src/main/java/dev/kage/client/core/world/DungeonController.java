package dev.kage.client.core.world;

import java.util.Map;
import java.util.Optional;

import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.ExitDungeonOutcome;

/**
 * Trạng thái "đang ở trong hầm ngục hay không" của chính người chơi (2026-08-27) — thuần Java,
 * không libGDX/GL, test được thẳng (UI.md §4). Cùng hình dạng {@link GuildController}/
 * {@link PartyController}.
 *
 * <p>Lát cắt hầm ngục hiện tại (Phase 8 lát cắt 1, ADR-026): cả nhóm cùng bị chuyển sang một
 * {@code WorldFacade} riêng, tick độc lập world chính. Map trống + 1 boss, chưa có phần thưởng gì.
 *
 * <p><b>⚠️ Ba khoảng trống CÓ THẬT của dungeon.proto</b> (kiểm lại opcode_registry.proto: dải
 * 1500-1503 chỉ có đúng 2 cặp Command/Response, không có một {@code *Event} nào):
 *
 * <ul>
 *   <li><b>Bị kéo vào theo nhóm thì không hay biết gì.</b> {@code DungeonInstanceRegistry
 *       .enterDungeon} kéo MỌI thành viên đang online vào chung một instance, nhưng chỉ người GỬI
 *       lệnh nhận được {@code EnterDungeonResponse}. Máy của những người còn lại bị đổi World mà
 *       không nhận một gói tin nào nói ra chuyện đó — họ sẽ không thấy dải chữ trên HUD, và không
 *       có nút Rời hầm ngục nào bày ra cho họ. Đó là lý do lớp này KHÔNG được coi
 *       {@link #inDungeon()} là sự thật tuyệt đối: nó chỉ đúng cho người vừa tự bấm nút.
 *   <li><b>Không có gói "đổi World" nào.</b> Khi nhân vật rời world chính,
 *       {@code AreaOfInterestTracker.onDetach} chỉ báo despawn cho NHỮNG NGƯỜI KHÁC — bản thân
 *       người đi không nhận được despawn của bất cứ thứ gì họ đang thấy. World mới thì có phát
 *       spawn đầy đủ (tập "đang thấy" của họ bắt đầu rỗng ở world mới), nên hệ quả là ĐỌNG chứ
 *       không phải THIẾU: quái/NPC/người chơi của làng vẫn nằm nguyên trên màn hình bên trong hầm
 *       ngục. Xem {@code WorldScreen#onWorldInstanceChanged} về cách bù.
 *   <li><b>Không có gói nào báo vị trí mới.</b> Server đặt nhân vật ở {@code (0,0)} của instance,
 *       nhưng {@code MoveResponse} chỉ được phát như một ack cho {@code MoveCommand} — không tự
 *       gửi lúc attach.
 * </ul>
 *
 * <p>Đóng ba lỗ trên thật sự cần event đẩy phía server, tức cần sửa {@code .proto} — nằm ngoài
 * quyền của client (/CLAUDE.md §4). Lỗ thứ nhất đã tái hiện qua socket thật và có đề xuất ở
 * ADR-047 (Proposed, 2026-09-11).
 */
final class DungeonController {

    /**
     * Mã hầm ngục DUY NHẤT hợp lệ ở lát cắt này — bản sao của
     * {@code server-gateway.DungeonInstanceRegistry.DUNGEON_CODE} (đọc từ code server thật, không
     * đoán). Nhân đôi hằng số là bắt buộc chứ không phải lười: client-core không được phụ thuộc
     * module server, và luật thật vẫn nằm ở đó — sai mã thì server trả
     * {@link EnterDungeonOutcome#ENTER_DUNGEON_OUTCOME_UNKNOWN_DUNGEON}, đúng thứ lớp này dịch ra
     * chữ được ({@code dungeon.error.unknown_dungeon}).
     *
     * <p>{@code dungeon_code} có mặt trong giao thức để về sau còn nhiều hầm ngục; khi đó danh sách
     * mã phải đến TỪ SERVER (thêm một Request tra cứu), không phải mọc thêm hằng số ở đây.
     */
    static final String DUNGEON_CODE = "sample_dungeon";

    /**
     * Theo lời server, và CHỈ theo lời server — xem javadoc lớp về việc vì sao câu trả lời này vẫn
     * có thể sai (bị bạn cùng nhóm kéo vào mà không ai báo).
     */
    private boolean inDungeon;

    /**
     * Kết quả một lệnh vào hầm ngục.
     *
     * <p>{@code ALREADY_IN_DUNGEON} cũng ghi {@link #inDungeon} thành {@code true}: đó là server nói
     * thẳng ra "nhân vật này đang ở trong một instance", và nó là đường DUY NHẤT một máy bị kéo vào
     * theo nhóm có thể biết sự thật — bấm Vào hầm ngục lần nữa rồi đọc câu từ chối.
     */
    void onEnterOutcome(EnterDungeonOutcome outcome) {
        if (outcome == EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK
                || outcome == EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_ALREADY_IN_DUNGEON) {
            inDungeon = true;
        }
    }

    /**
     * Kết quả một lệnh rời hầm ngục. Cả hai outcome đều kết luận "không còn ở trong hầm ngục nữa" —
     * {@code NOT_IN_DUNGEON} nghĩa là server chưa từng thấy nhân vật này ở instance nào, tức màn
     * hình đang hiện sai và phải sửa lại theo server (Điều 15).
     */
    void onExitOutcome(ExitDungeonOutcome outcome) {
        if (outcome == ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK
                || outcome == ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_NOT_IN_DUNGEON) {
            inDungeon = false;
        }
    }

    boolean inDungeon() {
        return inDungeon;
    }

    /**
     * Lệnh vừa rồi có làm nhân vật ĐỔI World hay không — chỗ gọi phải dọn mọi thực thể đang hiển thị
     * khi câu này đúng (xem khoảng trống thứ hai ở javadoc lớp).
     *
     * <p>Chỉ {@code OK} mới đổi World: {@code ALREADY_IN_DUNGEON} nghĩa là đã ở sẵn trong đó, không
     * có cú chuyển nào xảy ra, và dọn màn hình lúc đó chỉ xoá đi những thứ vừa nhận đúng.
     */
    static boolean changesWorld(EnterDungeonOutcome outcome) {
        return outcome == EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK;
    }

    /** Cùng lập luận {@link #changesWorld(EnterDungeonOutcome)} cho chiều đi ra. */
    static boolean changesWorld(ExitDungeonOutcome outcome) {
        return outcome == ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK;
    }

    /** Rời màn hình/mất kết nối — trạng thái của một phiên chơi không sống sót sang phiên sau. */
    void clear() {
        inDungeon = false;
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n cho từng outcome.
    //
    // Bảng tra thay cho `switch` phủ hết enum, cùng lý do GuildController.OUTCOME_MESSAGE_KEYS: một
    // switch phủ hết một enum protobuf tự nó đã vượt trần CyclomaticComplexity của PMD.
    //
    // UNSPECIFIED và UNRECOGNIZED CỐ TÌNH vắng mặt: cả hai rơi vào Optional.empty(), và chỗ gọi dịch
    // nó thành "dungeon.error.unknown". Cái sau là kết quả của một server MỚI HƠN client — không
    // liệt kê trước được, chỉ có thể nói ra là không hiểu.
    //
    // OK CÓ khoá riêng chứ không im lặng: vào/ra hầm ngục là một cú chuyển world mà người chơi
    // không nhìn thấy ngay được ở lát cắt map trống này (ADR-026) — không nói ra thì màn hình trông
    // y hệt như lúc nút vừa bấm không ăn.
    // ---------------------------------------------------------------------------------------------

    private static final Map<EnterDungeonOutcome, String> ENTER_MESSAGE_KEYS = Map.of(
            EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK,
            "dungeon.notice.entered",
            EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_NOT_IN_PARTY,
            "dungeon.error.not_in_party",
            EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_PARTY_MEMBER_OFFLINE,
            "dungeon.error.member_offline",
            EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_ALREADY_IN_DUNGEON,
            "dungeon.error.already_in_dungeon",
            EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_UNKNOWN_DUNGEON,
            "dungeon.error.unknown_dungeon");

    private static final Map<ExitDungeonOutcome, String> EXIT_MESSAGE_KEYS = Map.of(
            ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK, "dungeon.notice.exited",
            ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_NOT_IN_DUNGEON, "dungeon.error.not_in_dungeon");

    /** Khoá i18n của một outcome vào hầm ngục, rỗng khi client không nhận ra giá trị đó. */
    static Optional<String> enterMessageKey(EnterDungeonOutcome outcome) {
        return Optional.ofNullable(ENTER_MESSAGE_KEYS.get(outcome));
    }

    /** Khoá i18n của một outcome rời hầm ngục — xem {@link #enterMessageKey}. */
    static Optional<String> exitMessageKey(ExitDungeonOutcome outcome) {
        return Optional.ofNullable(EXIT_MESSAGE_KEYS.get(outcome));
    }
}
