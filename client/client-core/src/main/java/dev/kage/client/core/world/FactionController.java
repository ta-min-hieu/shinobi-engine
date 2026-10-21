package dev.kage.client.core.world;

import java.util.List;
import java.util.Map;

import dev.kage.protocol.Faction;

/**
 * Trạng thái của bước "chọn hệ phái ở Lv10" (2026-08-27) — thuần Java, không libGDX/GL, test được
 * thẳng (UI.md §4). Cùng hình dạng {@link GuildController}/{@link DungeonController}.
 *
 * <p>GDD §4 + character.proto: nhân vật mới LUÔN sinh ra ở hệ {@link #STARTING_FACTION}
 * ({@code CreateCharacterUseCase} không có bước chọn hệ), và đủ Lv10 thì được đổi sang một trong 4
 * hệ còn lại qua {@code SelectFactionRequest}.
 *
 * <p><b>⚠️ Client KHÔNG phải nguồn chân lý về hệ phái, và giao thức không cho nó trở thành nguồn
 * chân lý được</b> — ba sự thật kiểm được, không phải suy đoán:
 *
 * <ul>
 *   <li>{@code SelectFactionResponse} chỉ mang {@code success} + {@code error_key}, KHÔNG mang hệ
 *       phái mới. Cái duy nhất làm cho {@link #onSelectionConfirmed} hợp lệ là nó chỉ được gọi cho
 *       ĐÚNG gói trả lời của ĐÚNG yêu cầu vừa gửi (khớp {@code sequenceId}) — tức "server xác nhận
 *       yêu cầu X", chứ không phải client tự đặt X.
 *   <li>{@code error_key} trong {@code SelectFactionResponse} THỰC TẾ luôn rỗng: server trả lỗi qua
 *       {@code ErrorResponse} (opcode 5) chứ không qua field đó — xem
 *       {@code GatewayChannelHandler#handleSelectFactionBlocking}. Vì vậy
 *       {@link FactionCoordinator} phải đọc cả opcode 5 và lọc theo {@code related_sequence_id}.
 *   <li>Không một gói tin nào BÁO hệ phái mới cho ai khác. {@code CharacterSpawnedEvent} mang
 *       {@code faction} nhưng chỉ phát lúc vào tầm nhìn, nên người chơi khác đang đứng cạnh sẽ tiếp
 *       tục thấy model của hệ CŨ cho tới khi mình ra khỏi tầm nhìn của họ rồi quay lại. Đóng lỗ đó
 *       cần một event đẩy phía server, tức cần sửa {@code .proto} — ngoài quyền của client
 *       (/CLAUDE.md §4).
 * </ul>
 *
 * <p>{@link #level} và {@link #faction} ở đây đều đến TỪ SERVER
 * ({@code CharacterSummary} lúc chọn nhân vật, rồi {@code ExperienceChangedEvent} mỗi lần lên cấp),
 * và {@link #canOffer()} CHỈ dùng để ẩn/hiện lối vào cho gọn giao diện — server vẫn kiểm lại cấp độ
 * ({@code SelectFactionUseCase}) và vẫn từ chối nếu client hiểu sai (Điều 24).
 */
final class FactionController {

    /**
     * Hệ khởi đầu của MỌI nhân vật mới — character.proto: "Nhân vật mới tạo mặc định FACTION_HOA
     * ({@code CreateCharacterUseCase}), không có bước chọn hệ lúc tạo".
     */
    static final Faction STARTING_FACTION = Faction.FACTION_HOA;

    /**
     * Bản sao của {@code SelectFactionUseCase.MIN_LEVEL} phía server. CHỈ để quyết định có bày lối
     * vào ra hay không — không phải một luật client tự áp: bấm chọn khi chưa đủ cấp vẫn gửi lệnh
     * được và server vẫn là bên trả {@code error.level_too_low}.
     */
    static final int MIN_LEVEL = 10;

    /**
     * Năm hệ chọn được (ADR-066: đổi lại được, kể cả về Hỏa), đúng thứ tự khai báo trong enum
     * {@code Faction} (character.proto). Chọn đúng hệ đang có ⇒ server từ chối, không tốn gì. Danh sách
     * dựng SẴN một lần: nó không bao giờ đổi, và nó được hỏi mỗi lần cửa sổ vẽ lại.
     */
    static final List<Faction> SELECTABLE = List.of(
            Faction.FACTION_HOA, Faction.FACTION_THUY, Faction.FACTION_PHONG, Faction.FACTION_LOI, Faction.FACTION_THO);

    /** Khoá i18n tên hiển thị từng hệ — dùng chung với màn hình chọn nhân vật, không đặt tên mới. */
    private static final Map<Faction, String> NAME_KEYS = Map.of(
            Faction.FACTION_HOA, "faction.hoa",
            Faction.FACTION_THUY, "faction.thuy",
            Faction.FACTION_PHONG, "faction.phong",
            Faction.FACTION_LOI, "faction.loi",
            Faction.FACTION_THO, "faction.tho");

    private int level;

    private Faction faction = Faction.FACTION_UNSPECIFIED;

    /**
     * Cấp + hệ phái mới nhất theo lời server.
     *
     * @param level từ {@code CharacterSummary} lúc vào world, sau đó từ {@code ExperienceChangedEvent}.
     * @param faction từ {@code CharacterSummary} — server không phát lại giá trị này bao giờ nữa
     *     trong cả phiên chơi (xem javadoc lớp).
     */
    void onCharacterState(int level, Faction faction) {
        this.level = level;
        this.faction = faction;
    }

    /** Chỉ cấp độ vừa đổi (lên cấp giữa trận) — hệ phái giữ nguyên. */
    void onLevel(int level) {
        this.level = level;
    }

    /**
     * Server vừa xác nhận {@code success=true} cho ĐÚNG yêu cầu đổi sang {@code faction}.
     *
     * <p>Đây là chỗ DUY NHẤT {@link #faction} đổi ngoài {@link #onCharacterState}, và nó chỉ được
     * gọi sau khi {@link FactionCoordinator} đã khớp {@code sequenceId} của gói trả lời với yêu cầu
     * đang treo — xem javadoc lớp về vì sao bước khớp đó là thứ giữ cho việc này không thành "client
     * tự phong hệ phái cho mình".
     */
    void onSelectionConfirmed(Faction faction) {
        this.faction = faction;
    }

    /**
     * Có cho mở cửa sổ chọn linh căn không: đủ cấp. ADR-066 — đổi lại được (lần thứ hai miễn phí dưới
     * Lv30, sau đó tốn Tẩy Căn Đan); server quyết chi phí, client chỉ ẩn lối vào khi chưa đủ cấp.
     */
    boolean canOffer() {
        return level >= MIN_LEVEL;
    }

    /** Lời mời "nhấn H" chỉ dành cho người CHƯA thức tỉnh — người đã chọn không cần được nhắc lại. */
    boolean shouldAnnounce() {
        return canOffer() && faction == STARTING_FACTION;
    }

    int level() {
        return level;
    }

    Faction faction() {
        return faction;
    }

    /** Khoá i18n tên hệ phái — {@code faction.unspecified} cho giá trị client không nhận ra. */
    static String nameKey(Faction faction) {
        return NAME_KEYS.getOrDefault(faction, "faction.unspecified");
    }

    /**
     * Khoá i18n giải thích một lần đổi hệ thất bại.
     *
     * @param errorKey đúng chuỗi server gửi trong {@code ErrorResponse.error_key} (VD
     *     {@code error.level_too_low}) — dùng thẳng làm khoá i18n, không dịch tên lại. Chuỗi rỗng
     *     (server cũ, hoặc một nhánh lỗi chưa đặt khoá) rơi về {@code faction.error.unknown}: nói
     *     ra là không hiểu, thay vì im lặng.
     *     <p>⚠️ Câu "vì mọi khoá server sinh ra đều đã có mặt trong bundle" từng đứng ở đây là
     *     SAI: rà bằng máy 2026-09-19 thì server sinh 25 khoá {@code error.*} mà bundle chỉ có 10.
     *     Nay đủ cả 25, và bất biến ấy đã có cổng canh: task Gradle {@code verifyServerErrorKeys}
     *     (nằm trong {@code check}) đối chiếu mọi literal {@code "error.*"} trong nguồn của
     *     {@code server/} với CẢ HAI bundle và fail build nếu thiếu. Chỗ nào vẫn không chắc thì đi
     *     qua {@code KageI18n#errorText}, nó có câu dự phòng.
     */
    static String errorMessageKey(String errorKey) {
        return errorKey == null || errorKey.isBlank() ? "faction.error.unknown" : errorKey;
    }

    /** Rời màn hình/mất kết nối — trạng thái của một phiên chơi không sống sót sang phiên sau. */
    void clear() {
        level = 0;
        faction = Faction.FACTION_UNSPECIFIED;
    }
}
