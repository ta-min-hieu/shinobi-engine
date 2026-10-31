package dev.kage.server.world;

/**
 * Command nội bộ trong hàng đợi world (SERVER.md §2-3) — KHÔNG phải {@code Command} của
 * {@code shared-protocol}/NETWORK.md §3 (đó là packet trên dây; đây là việc cần làm ở tick kế
 * tiếp, gateway enqueue rồi trả về ngay, không chờ). Sealed vì tập việc world phải làm đã đóng;
 * lồng record trong interface (thay vì file riêng) để tránh nhiều top-level class/file.
 */
sealed interface WorldCommand {

    /**
     * Gán entity ECS mới cho {@code characterId} — gọi sau khi SelectCharacter thành công.
     * {@code spawnX/spawnY} là vị trí đã LƯU lần cuối (feature-character.PositionSnapshot,
     * SERVER.md §8) — KHÔNG luôn là (0,0), chỉ nhân vật chưa từng lưu vị trí mới ở đó.
     * {@code baseStats} là STR/AGI/INT/VIT thật từ {@code character_stat} DB (gateway đọc qua
     * {@code CharacterFacade.loadStats}) — world derive thành ATK/DEF/... (GDD §5).
     *
     * <p>⚠️ {@code bonus} đi CÙNG lệnh này, không phải một lệnh riêng gửi sau (sửa 2026-09-18).
     * Đo được: attach và {@code ApplyEquipmentBonusCommand} là hai lệnh rời trong cùng hàng đợi,
     * nên entity sống ít nhất một tick với chỉ số NỀN. Đăng nhập cạnh quái thì ăn một nhát tính
     * theo giáp CHƯA mặc, trên thanh máu 100 thay vì 206 — đo thật: 37 sát thương thay vì 27,
     * và nhát đó rơi TRƯỚC cả gói {@code SelectCharacterResponse}.
     *
     * <p>{@link EquipmentBonusSnapshot#NONE} cho chỗ gọi không có trang bị (test, và các đường
     * arena/dungeon chưa nối — chúng còn nguyên cửa sổ này, xem ghi chú ở WorldFacade).
     */
    record AttachCharacterCommand(CharacterEntry entry) implements WorldCommand {}

    /**
     * Xoá entity ECS của {@code characterId} — gọi khi mất kết nối. {@code finalPosition} nhận vị trí
     * thật ngay trước khi xoá (xem {@link FinalPositionListener}).
     */
    record DetachCharacterCommand(long characterId, FinalPositionListener finalPosition) implements WorldCommand {}

    /** Ý định di chuyển — {@code dirX/dirY} là hướng CHƯA chuẩn hoá, world tự clamp + nhân tốc độ. */
    record MoveIntentCommand(long characterId, int sequenceId, float dirX, float dirY) implements WorldCommand {}

    /**
     * Ý định nhảy (ADR-033) — không mang theo lực/độ cao, world tự áp {@code JUMP_VELOCITY_MPS}
     * NẾU đang grounded (bỏ qua an toàn nếu không, xem {@code WorldFacade.handleJumpIntent}).
     */
    record JumpIntentCommand(long characterId, int sequenceId) implements WorldCommand {}

    /**
     * Chọn mục tiêu chiến đấu — chỉ ghi nhận NHẮM AI, KHÔNG tự ra đòn (đổi 2026-08-09, xem
     * {@code CombatSystem}). Muốn đánh phải gửi tiếp {@link AttackIntentCommand}.
     */
    record SetTargetCommand(long characterId, long targetEntityId) implements WorldCommand {}

    /**
     * Ý định ra MỘT đòn đánh thường lên mục tiêu đang nhắm (2026-08-09) — không mang mục tiêu theo
     * gói (world tự đọc {@code TargetComponent}), cùng lý do {@link JumpIntentCommand} rỗng.
     */
    record AttackIntentCommand(long characterId) implements WorldCommand {}

    /**
     * Dùng skill nhắm mục tiêu (GAMEPLAY.md §3-4) — {@code skillCode} do client gửi, server tự
     * kiểm tra có tồn tại/cooldown/Khí/tầm (xem {@code SkillSystem}), KHÔNG tin client.
     */
    record UseSkillCommand(long characterId, String skillCode, long targetEntityId) implements WorldCommand {}

    /**
     * Tính lại chỉ số dẫn xuất theo trang bị (GAMEPLAY.md §2/§7) — cả 4 chỉ số là TỔNG cộng dồn
     * hiện tại từ mọi item đang mặc (ĐÃ tính hệ số cường hoá từng món, ADR-036 D2/D3), gateway
     * (feature-inventory) tự tính lại từ đầu mỗi lần. {@code bonusHp} cộng vào MÁU TỐI ĐA.
     */
    record ApplyEquipmentBonusCommand(long characterId, EquipmentBonusSnapshot bonus) implements WorldCommand {}

    /**
     * Chat kênh Khu vực (GAMEPLAY.md §10, Phase 6 lát cắt 6) — CHỈ kênh này cần world tick (đọc
     * {@code AreaOfInterestTracker}, không thread-safe ngoài tick thread); World/Riêng tư gateway
     * tự định tuyến thẳng qua {@code ConnectedCharacterRegistry}, không đi qua hàng đợi này.
     */
    record AreaChatMessageCommand(long characterId, String senderName, String body) implements WorldCommand {}

    /** ADR-089 — Độ Kiếp Chuyển Thế: đặt cấp về {@code level} (lần DUY NHẤT cấp được lùi). */
    record ResetCharacterLevelCommand(long characterId, int level) implements WorldCommand {}

    /**
     * Spawn một boss theo MÃ LOÀI (ADR-048 D6).
     *
     * <p>Trước đây lệnh này không có tham số vì cả dự án chỉ có một boss hardcode. Nay mã đi kèm
     * lệnh, nên hầm ngục chọn được boss của nó và world chính spawn boss qua `monster_spawn` như
     * mọi loài khác — {@code server-world} vẫn không biết khái niệm "dungeon", chỉ biết "có ai bảo
     * spawn con nào".
     *
     * @param bossCode mã trong {@code monster_template}, ví dụ {@code sample_boss}
     */
    record SpawnBossCommand(String bossCode, float x, float y, boolean worldBoss) implements WorldCommand {}

    /** Một con quái thường tại {@code (x, y)}, KHÔNG thuộc điểm spawn nào nên không hồi sinh (ADR-113). */
    record SpawnOnceCommand(String monsterCode, float x, float y) implements WorldCommand {}

    /**
     * Thu phục yêu thú (ADR-124): kiểm tầm/máu, hỏi {@code chanceOf} tỉ lệ của loài (rỗng = không thu phục được), tung
     * và gỡ quái nếu trúng — tất cả trong một lượt tick. {@code reply} chạy trên tick thread, phải trả về ngay.
     */
    record CaptureMonsterCommand(
            long characterId, long monsterId, CaptureRule rule, java.util.function.Consumer<CaptureResult> reply)
            implements WorldCommand {}

    /** Pháp bảo (ADR-127): chiêu chủ động của pháp bảo đang mặc — gateway đã kiểm món + hồi chiêu. */
    record ArtifactEffectCommand(long characterId, ArtifactEffect effect, int magnitudePercent, float durationSeconds)
            implements WorldCommand {}

    /** ADR-131: đặt (hoặc dời) NPC động {@code npcCode} tới (x, y) — mỗi mã một thực thể trong World. */
    record PlaceNpcCommand(String npcCode, float x, float y) implements WorldCommand {}

    /** ADR-131: gỡ NPC động {@code npcCode} (không có thì thôi). */
    record RemoveNpcCommand(String npcCode) implements WorldCommand {}

    /** Thiên Kiếp (ADR-126): một đợt lôi kích trừ {@code percentOfMax}% máu tối đa của nhân vật. */
    record StrikeCharacterCommand(long characterId, int percentOfMax) implements WorldCommand {}

    /** Ngày đêm (ADR-123): đêm hay ngày + phần trăm sát thương đòn thường của quái lúc này. */
    record SetNightCommand(boolean night, int monsterDamagePercent) implements WorldCommand {}

    /** Bắt đầu đoàn hộ tống cho {@code characterId} (ADR-114). */
    record StartEscortCommand(long characterId, EscortRoute route) implements WorldCommand {}

    /** Huỷ đoàn hộ tống của {@code characterId} nếu có (ADR-114). */
    record CancelEscortCommand(long characterId) implements WorldCommand {}

    /** Chọn cách hồi sinh khi đang nằm chờ (ADR-117); {@code reply} chạy trên tick thread — phải trả về ngay. */
    record ReviveCommand(long characterId, boolean inPlace, java.util.function.Consumer<ReviveOutcome> reply)
            implements WorldCommand {}

    /**
     * Ngoại hình của {@code characterId} vừa đổi (đổi hệ phái — ADR-045). Command này CHỈ mang id:
     * server-world không biết "hệ phái"/"bậc trang phục" là gì và không được biết (ngoại hình sống
     * ở {@code ConnectedCharacterRegistry}, xem javadoc {@code appearanceByCharacterId}). Việc duy
     * nhất của world là trả lời "ai đang nhìn thấy nhân vật này" — thứ chỉ đọc được trên tick
     * thread, cùng lý do {@link AreaChatMessageCommand} tồn tại.
     */
    record AppearanceChangedCommand(long characterId) implements WorldCommand {}

    /**
     * Hồi {@code amount} máu cho {@code characterId} (ADR-046 D5 — đan dược). World KHÔNG biết
     * "vật phẩm" là gì: {@code feature-inventory} đã trừ số lượng trong transaction của nó rồi,
     * việc còn lại chỉ là cộng máu — đúng hình dạng {@link ApplyEquipmentBonusCommand} (world nhận
     * SỐ đã tính sẵn, không nhận trang bị).
     *
     * <p>Vào qua hàng đợi chứ không gọi thẳng: mọi thứ sửa ECS state đều phải chạy trên tick thread
     * (server/CLAUDE.md, ba ranh giới thread), còn lệnh này đến từ Virtual Thread xử lý gói.
     */
    record HealCharacterCommand(long characterId, int amount) implements WorldCommand {}

    /**
     * Cấp của {@code characterId} vừa lên GIỮA PHIÊN (EXP hạ quái hoặc thưởng quest) — world chỉ
     * NÂNG {@code CharacterLevelComponent}, không bao giờ hạ: các lượt cộng EXP chạy trên những
     * Virtual Thread riêng nên hai lệnh có thể về ngược thứ tự, và lệnh mang cấp thấp hơn cấp đang
     * có chắc chắn là lệnh đến muộn. World KHÔNG biết EXP hay đường cong cấp — nhận SỐ đã tính
     * sẵn, đúng hình dạng {@link HealCharacterCommand}.
     */
    record RaiseCharacterLevelCommand(long characterId, int level) implements WorldCommand {}

    /**
     * {@code characterId} vừa đổi hệ phái GIỮA PHIÊN — world tính lại mọi chỉ số chiến đấu phụ thuộc
     * hệ (nguyên tố, passive của hệ) tại chỗ, không chờ lần attach sau. World KHÔNG biết "hệ phái"
     * hay cấp tối thiểu để đổi — nhận {@link Element} đã dịch sẵn, đúng hình dạng
     * {@link RaiseCharacterLevelCommand}.
     */
    record ChangeCharacterElementCommand(long characterId, Element element) implements WorldCommand {}

    /** ADR-071 — bậc chiêu theo ô của linh căn hiện tại vừa đổi (nâng bậc, đổi linh căn). */
    record SetSkillRanksCommand(long characterId, SkillRankSnapshot ranks) implements WorldCommand {}

    /**
     * ADR-092 — gọi {@code action} với id từng người đang NHÌN THẤY {@code characterId} (kèm chính họ). Chỉ đọc được
     * trên tick thread, cùng lý do {@link AreaChatMessageCommand}; world không biết action làm gì (gateway gửi gói).
     */
    record ObserversCommand(long characterId, java.util.function.LongConsumer action) implements WorldCommand {}

    /**
     * Mốc chặn hàng đợi — không đổi gì trong ECS, chỉ gọi {@code onReached} khi tick thread đã xử lý
     * xong mọi lệnh xếp TRƯỚC nó. Xem {@link WorldFacade#inboxBarrier()}. Mang callback chứ không
     * mang thẳng future: record không giữ đối tượng mutable của người gọi (SpotBugs EI).
     */
    record InboxBarrierCommand(Runnable onReached) implements WorldCommand {}
}
