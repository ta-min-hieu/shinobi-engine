package dev.kage.client.core.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.Faction;
import dev.kage.protocol.HealthChangedEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.SkillResultEvent;

/**
 * "HUD controller" cho combat (UI.md §4) — đọc packet server, giữ trạng thái HP/Khí/hồi chiêu/mục
 * tiêu để {@link dev.kage.client.core.ui.hud.CombatHud} vẽ, KHÔNG tự vẽ hay động vào mạng
 * (widget/controller tách biệt — UI.md §4, §10). Không phụ thuộc libGDX/GL nên test được thẳng,
 * không cần context đồ hoạ.
 *
 * <p>Khí và hồi chiêu KHÔNG có gói đồng bộ nào (GAMEPLAY.md §3): chúng bắt đầu ở mức đầy và hồi
 * chiêu là đếm ngược cục bộ hoàn toàn dự đoán, có thể lệch với server (chấp nhận được — server luôn
 * tự kiểm tra lại, lệch chỉ khiến nút bật/tắt sai thời điểm, xem {@link CombatConstants}). Riêng
 * lệch do server BỎ lệnh skill (im lặng) thì được sửa: lần bấm không thấy {@code SkillResultEvent}
 * của chính mình trong cửa sổ chờ được trả lại Khí + hồi chiêu, xem {@link #tick}.
 *
 * <p><b>HP thì CÓ</b>, kể từ ADR-046: ngoài {@link AttackResultEvent}/{@link SkillResultEvent} nhắm
 * vào mình, còn {@link HealthChangedEvent} cho mọi lần máu đổi mà không do đòn đánh (hồi máu ngoài
 * chiến đấu, vùng an toàn, quái lành lại, đan dược). Trước ADR đó, máu hồi mà vạch máu đứng im tới
 * đòn đánh kế tiếp — đúng họ bug ADR-043 đã phải sửa một lần.
 */
final class CombatHudController {

    /** Trạng thái 1 quái đã biết, tối thiểu để vẽ hộp + thanh máu (không phải ECS Component). */
    record MonsterState(String code, float x, float y, int hp, int maxHp) implements PickablePoint {}

    /**
     * Máu đối thủ CHƯA biết — {@code CharacterSpawnedEvent} không mang máu, nên trước gói máu đầu
     * tiên client không có con số thật nào để hiện (Điều 15: không đoán).
     */
    static final int UNKNOWN_HP = -1;

    private final long localCharacterId;

    /**
     * Skill slot 0 theo hệ phái hiện tại. KHÔNG {@code final} kể từ 2026-08-27: hệ phái đổi được
     * giữa phiên chơi ({@code SelectFactionRequest} ở Lv10), và mỗi hệ chỉ cast được skill của hệ
     * mình ({@code server-world.SkillSystem.isCasterEligible}) — giữ nguyên skill cũ là bày ra một
     * nút mà server chắc chắn từ chối. Xem {@link #onFactionChanged}.
     */
    private CombatConstants.FactionSkill skill;

    private final Map<Long, MonsterState> monsters = new HashMap<>();

    /** View chỉ-đọc của {@link #monsters}, dựng MỘT lần — xem {@link #monsters()}. */
    private final Map<Long, MonsterState> monstersView = Collections.unmodifiableMap(monsters);

    private int playerHp = CombatConstants.CHARACTER_MAX_HP;
    /**
     * MÁU TỐI ĐA của chính người chơi. Kể từ ADR-036 con số này KHÔNG còn là hằng số: trang bị có
     * {@code bonus_hp} nâng nó lên ({@code maxHp = 100 + bonus_hp}, server tính). Giá trị khởi tạo
     * chỉ là phỏng đoán ban đầu để vạch máu có mẫu số ngay lúc vào world; nó được THAY bằng số
     * THẬT của server ngay khi {@code CharacterRespawnedEvent} đầu tiên tới.
     *
     * <p>Giá trị thật đến từ HAI chỗ, cả hai đều của server: {@link #onEnterWorld} lúc vào world
     * ({@code SelectCharacterResponse.max_hp}) và {@link #onRespawn} mỗi lần hồi sinh
     * ({@code CharacterRespawnedEvent.max_hp}).
     */
    private int playerMaxHp = CombatConstants.CHARACTER_MAX_HP;

    private int playerChi = CombatConstants.CHARACTER_MAX_CHI;
    private float skillCooldownRemaining;

    /**
     * Lần bấm skill gần nhất đã trừ Khí + bắt đầu hồi chiêu DỰ ĐOÁN nhưng chưa thấy
     * {@code SkillResultEvent} của chính mình — xem {@link #tick} về việc trả lại khi quá hạn.
     */
    private boolean castAwaitingConfirmation;

    /** Giây đã trôi kể từ lần bấm đang chờ xác nhận, so với {@link CombatConstants#SKILL_CONFIRM_WINDOW_SECONDS}. */
    private float castConfirmElapsedSeconds;

    /** Khí đã trừ cho lần bấm đang chờ — nhớ riêng vì hệ phái (tức giá Khí) đổi được giữa chừng. */
    private int pendingCastChiCost;

    /**
     * Id mục tiêu đang nhắm: ÂM = quái, DƯƠNG = nhân vật khác (đối thủ đấu trường, 2026-10-04),
     * {@code 0} = không nhắm ai — cùng quy ước dấu với {@code target_entity_id} của combat.proto.
     */
    private long currentTargetId;

    /** {@link #currentTargetId} đã đóng hộp ({@code null} khi không nhắm ai) — xem {@link #targetState()}. */
    private Long currentTargetKey;

    /** Nhân vật khác đang thấy — xem {@link #bindCharacterPositions}. Rỗng tới khi được nối. */
    private Map<Long, ? extends PickablePoint> characterPositions = Map.of();

    private String characterTargetName = "";
    private int characterTargetHp = UNKNOWN_HP;
    private int characterTargetMaxHp = UNKNOWN_HP;

    /**
     * @param localCharacterId id nhân vật của chính người chơi — dùng để nhận ra
     *     {@code AttackResultEvent}/{@code SkillResultEvent} nào đang nhắm vào MÌNH thay vì quái.
     * @param localFaction hệ phái của chính người chơi — quyết định skill slot 0 nào được gán cho
     *     nút skill duy nhất của HUD ({@link CombatConstants#FACTION_SKILLS}), vì mỗi hệ phái chỉ
     *     cast được skill của hệ mình (server-world.SkillSystem.isCasterEligible).
     */
    CombatHudController(long localCharacterId, Faction localFaction) {
        this.localCharacterId = localCharacterId;
        this.skill = skillFor(localFaction);
    }

    /**
     * Server vừa XÁC NHẬN nhân vật đã đổi sang {@code faction} (opcode 207, khớp {@code sequenceId})
     * — đổi nút skill của HUD theo. Không có đường nào khác gọi được hàm này: client không tự phong
     * hệ phái cho mình (xem {@link FactionController}).
     *
     * <p>Hồi chiêu đang đếm dở KHÔNG được xoá: nó là bản sao dự đoán của cooldown phía server
     * (GAMEPLAY.md §3), và server chỉ theo dõi {@code SkillCooldownComponent} của nhân vật chứ
     * không reset nó khi đổi hệ. Xoá ở đây sẽ khiến nút sáng lên sớm hơn thật.
     */
    void onFactionChanged(Faction faction) {
        this.skill = skillFor(faction);
    }

    /**
     * Hệ phái lạ ({@code UNSPECIFIED}, hoặc {@code UNRECOGNIZED} từ một server mới hơn) rơi về Hoả —
     * đúng cách server tự map ({@code GatewayChannelHandler}: UNSPECIFIED/UNRECOGNIZED → HOA) và
     * cách {@link CharacterModelKey} chọn model. Không ném: giá trị này đến từ MẠNG
     * ({@code CharacterSummary.faction}), và một ngoại lệ ở đây nằm trong constructor của
     * {@code WorldScreen} — libGDX không bắt gì trong vòng render, nên ném là sập cả ứng dụng lúc
     * vào game. Nút chiêu sai hệ thì server vẫn tự từ chối ({@code SkillSystem.isCasterEligible}).
     */
    private static CombatConstants.FactionSkill skillFor(Faction faction) {
        CombatConstants.FactionSkill found = CombatConstants.FACTION_SKILLS.get(faction);
        return found != null ? found : CombatConstants.FACTION_SKILLS.get(Faction.FACTION_HOA);
    }

    /**
     * Quên sạch mọi con quái đang theo dõi — dùng khi nhân vật vừa ĐỔI World (vào/ra hầm ngục), nơi
     * quái của world cũ không còn tồn tại với máy này nữa mà giao thức lại không phát despawn nào
     * (xem {@code DungeonController}). Xoá luôn mục tiêu: nhắm một con quái ở world khác là gửi
     * {@code AttackCommand} vào hư không.
     */
    void clearMonsters() {
        monsters.clear();
        setCurrentTarget(0L);
    }

    /**
     * Ghi nhận quái mới, tự chọn làm mục tiêu nếu chưa có mục tiêu nào.
     *
     * @return {@code monster_id} vừa được TỰ ĐỘNG chọn làm mục tiêu (caller phải gửi
     *     {@code SetTargetCommand} với id này), hoặc {@code 0} nếu đã có mục tiêu từ trước nên
     *     không đổi. Chỉ 1 quái tồn tại ở overworld hiện tại nên "chọn quái đầu tiên thấy được" là
     *     đủ — chưa cần chọn "quái gần nhất" trong nhiều lựa chọn.
     */
    long onMonsterSpawned(MonsterSpawnedEvent event) {
        if (!MalformedPacket.accepts(event)) {
            return 0L; // id không phải quái (dương = nhân vật, kể cả chính mình) hoặc toạ độ hỏng.
        }
        monsters.put(
                event.getMonsterId(),
                new MonsterState(
                        event.getMonsterCode(),
                        event.getX(),
                        event.getY(),
                        MalformedPacket.clampPoints(event.getCurrentHp(), event.getMaxHp()),
                        event.getMaxHp()));
        if (currentTargetId == 0L) {
            setCurrentTarget(event.getMonsterId());
            return currentTargetId;
        }
        return 0L;
    }

    /**
     * Người chơi CHỦ ĐỘNG chọn mục tiêu (click chuột vào quái — chủ dự án yêu cầu 2026-08-09), ghi
     * đè mục tiêu tự chọn lúc quái spawn.
     *
     * @return {@code true} nếu id là quái đang theo dõi và mục tiêu đã đổi — caller phải gửi
     *     {@code SetTargetCommand}. {@code false} khi click vào con đã biến mất, hoặc chọn lại đúng
     *     con đang nhắm (không gửi gói thừa: server đã biết, cùng lý do {@link #useSkill}).
     */
    boolean selectTarget(long monsterId) {
        if (!monsters.containsKey(monsterId) || currentTargetId == monsterId) {
            return false;
        }
        setCurrentTarget(monsterId);
        return true;
    }

    void onMonsterDespawned(MonsterDespawnedEvent event) {
        monsters.remove(event.getMonsterId());
        if (currentTargetId == event.getMonsterId()) {
            setCurrentTarget(0L);
        }
    }

    void onMonsterMoved(MonsterMovedEvent event) {
        MonsterState existing = monsters.get(event.getMonsterId());
        if (existing != null && MalformedPacket.accepts(event)) {
            monsters.put(
                    event.getMonsterId(),
                    new MonsterState(existing.code(), event.getX(), event.getY(), existing.hp(), existing.maxHp()));
        }
    }

    void onAttackResult(AttackResultEvent event) {
        applyHpUpdate(event.getTargetEntityId(), event.getTargetCurrentHp(), event.getTargetDied());
    }

    void onSkillResult(SkillResultEvent event) {
        if (event.getAttackerEntityId() == localCharacterId
                && event.getSkillCode().equals(skill.skillCode())) {
            confirmOwnCast();
        }
        applyHpUpdate(event.getTargetEntityId(), event.getTargetCurrentHp(), event.getTargetDied());
    }

    /**
     * Server vừa xác nhận một lần cast của CHÍNH mình ({@code SkillSystem} phát đúng MỘT
     * {@code SkillResultEvent} cho mỗi lần cast thật, và luôn gửi cho người cast — kể cả khi trượt).
     *
     * <p>Đang chờ ⇒ Khí/hồi chiêu đã trừ dự đoán là ĐÚNG, chỉ thôi chờ. Không chờ gì ⇒ lời xác nhận
     * tới SAU khi {@link #tick} đã trả lại (mạng chậm hơn cửa sổ chờ): server ĐÃ trừ thật nên trừ
     * lại, hồi chiêu đặt theo cận dưới của thời gian đã trôi (ít nhất đúng cửa sổ chờ) — thà nút
     * xám lâu hơn thật một chút còn hơn sáng lên đúng lúc server chắc chắn từ chối.
     */
    private void confirmOwnCast() {
        if (castAwaitingConfirmation) {
            castAwaitingConfirmation = false;
            return;
        }
        playerChi = Math.max(0, playerChi - skill.chiCost());
        skillCooldownRemaining = Math.max(
                skillCooldownRemaining, skill.cooldownSeconds() - CombatConstants.SKILL_CONFIRM_WINDOW_SECONDS);
    }

    /**
     * ADR-046 D1 — máu của một entity vừa đổi mà KHÔNG do đòn đánh nào (hồi máu ngoài chiến đấu,
     * vùng an toàn, quái về tới điểm spawn, đan dược).
     *
     * <p>Đi CHUNG {@link #applyHpUpdate} với đòn đánh, chỉ khác một chỗ: {@code died} luôn
     * {@code false} — không nguồn nào ở đây giết ai, và event này KHÔNG bao giờ mang HP = 0.
     *
     * <p>⚠️ Từ 2026-09-19 gói này KHÔNG còn chỉ mang máu TĂNG: đổi trang bị làm máu tối đa đổi thì
     * server dịch máu hiện tại theo độ chênh, nên GỠ đồ +HP phát ra một lần TỤT (vẫn chặn dưới ở 1,
     * nên vẫn không bao giờ là 0). Đó cũng là lý do nhánh này phải tự cập nhật {@code playerMaxHp}:
     * {@code onOwnMaxHpChanged} chỉ đổi mẫu số rồi {@code min(hp, max)}, nên một mình nó vẽ THỪA
     * máu sau khi gỡ đồ.
     *
     * <p>Máu tối đa cũng được cập nhật khi entity là chính mình: đây là gói ĐẦU TIÊN có thể tới sau
     * khi trang bị đổi {@code max}, và vạch máu lấy mẫu số từ đó (cùng họ bug ADR-043 đã sửa).
     */
    void onHealthChanged(HealthChangedEvent event) {
        if (event.getEntityId() == localCharacterId && event.getMaxHp() > 0) {
            playerMaxHp = event.getMaxHp();
        }
        // Gói DUY NHẤT mang máu tối đa của người khác — xem characterTargetMaxHp().
        if (event.getEntityId() == currentTargetId && hasCharacterTarget() && event.getMaxHp() > 0) {
            characterTargetMaxHp = event.getMaxHp();
        }
        applyHpUpdate(event.getEntityId(), event.getCurrentHp(), false);
    }

    // Dùng chung cho AttackResultEvent/SkillResultEvent — cả 2 mang đúng 3 field cần: ai bị nhắm,
    // HP còn lại, có chết không (SkillResultEvent chỉ thêm skill_code/effect_kind, chưa cần cho
    // HUD tối thiểu này). Gọi 2 lần (Despawned tới sau) vẫn an toàn — remove/clear đã idempotent.
    private void applyHpUpdate(long targetEntityId, int currentHp, boolean died) {
        // Mọi con số máu đi qua đây đều bị KẸP về [0, max] (InboundPacketFuzzTest): server không bao
        // giờ gửi máu âm hay vượt trần, nên gói như thế là gói hỏng — vạch máu "-5 / 100" hay
        // "250 / 100" chỉ làm người chơi tin sai, không giúp ai cả.
        if (targetEntityId == localCharacterId) {
            playerHp = MalformedPacket.clampPoints(currentHp, playerMaxHp);
            return;
        }
        if (targetEntityId > 0L) {
            // Nhân vật khác: chỉ nhớ máu của người đang nhắm (HUD mục tiêu). Gục thì GIỮ mục tiêu —
            // trận đấu do server chốt (DuelResultEvent/308), không phải gói đòn đánh này.
            if (targetEntityId == currentTargetId) {
                characterTargetHp = MalformedPacket.clampPoints(currentHp, characterTargetMaxHp);
            }
            return;
        }
        MonsterState existing = monsters.get(targetEntityId);
        if (existing == null) {
            return; // Không phải mình cũng không phải quái đang theo dõi — bỏ qua an toàn.
        }
        if (died) {
            monsters.remove(targetEntityId);
            if (currentTargetId == targetEntityId) {
                setCurrentTarget(0L);
            }
            return;
        }
        monsters.put(
                targetEntityId,
                new MonsterState(
                        existing.code(),
                        existing.x(),
                        existing.y(),
                        MalformedPacket.clampPoints(currentHp, existing.maxHp()),
                        existing.maxHp()));
    }

    /**
     * Máu của chính mình NGAY khi vào world ({@code SelectCharacterResponse}, ADR-036 hậu kiểm) —
     * gọi một lần trước sự kiện đầu tiên. Trước khi có gói này, mẫu số vạch máu kẹt ở
     * {@code CHARACTER_MAX_HP} trong khi tử số là số thật của server, nên người chơi mặc đồ +HP
     * nhìn thấy "139 / 100" cho tới lần hồi sinh đầu tiên (playtest 2026-08-30).
     *
     * <p>{@code maxHp <= 0} nghĩa là server CŨ chưa gửi field — giữ nguyên phỏng đoán đang có thay
     * vì chia cho 0.
     */
    void onEnterWorld(int currentHp, int maxHp) {
        if (maxHp <= 0) {
            return;
        }
        playerMaxHp = maxHp;
        playerHp = Math.clamp(currentHp, 0, maxHp);
    }

    /**
     * ADR-051 — nhân vật vừa đổi World (vào/ra hầm ngục, vào/ra đấu trường). Phía server đó là một
     * lần ATTACH MỚI ({@code WorldTransfer} → {@code WorldFacade#handleAttach}): entity mới với Khí
     * đầy, {@code SkillCooldownComponent} mới tinh và không mục tiêu. {@code WorldChangedEvent} chỉ
     * chở máu, nên phần còn lại client phải tự đặt về ĐÚNG trạng thái của entity mới — cùng giả định
     * client đã dùng lúc vào world lần đầu (Khí khởi tạo bằng {@link CombatConstants#CHARACTER_MAX_CHI}).
     *
     * <p>Thiếu bước này thì Khí đã tiêu và hồi chiêu đang đếm của world cũ đi theo sang world mới; mà
     * Khí phía client chỉ được nạp lại khi hồi sinh ({@link #onRespawn}), nên người chơi tiêu hết
     * Khí rồi được kéo vào hầm ngục sẽ thấy nút chiêu xám mãi — {@link #useSkill} chặn tại chỗ một
     * lệnh mà server sẵn sàng nhận.
     */
    void onWorldChanged(int currentHp, int maxHp) {
        clearMonsters();
        onEnterWorld(currentHp, maxHp);
        playerChi = CombatConstants.CHARACTER_MAX_CHI;
        skillCooldownRemaining = 0f;
        castAwaitingConfirmation = false;
    }

    /**
     * ADR-043 — máu tối đa mới sau khi mặc/gỡ/tôi luyện trang bị. Máu HIỆN TẠI không đi kèm: server
     * dịch nó theo độ chênh nhưng gateway không đọc được (xem javadoc
     * {@code GatewayChannelHandler#applyEquipmentBonusAndComputeMaxHp}), nên ở đây chỉ CẮT tử số cho
     * khỏi vượt mẫu số mới — gỡ đồ +HP không được làm vạch máu tràn ra ngoài khung.
     */
    void onOwnMaxHpChanged(int maxHp) {
        if (maxHp <= 0) {
            return;
        }
        playerMaxHp = maxHp;
        playerHp = Math.min(playerHp, maxHp);
    }

    /**
     * HP/Khí vừa được server đặt lại (luôn về đầy) sau khi hồi sinh — xem
     * {@code CharacterRespawnedEvent}. Xoá luôn mục tiêu đang nhắm: server cũng tự xoá
     * {@code TargetComponent} của kẻ vừa hồi sinh (WorldFacade#respawnCharacter), nên client giữ
     * mục tiêu cũ chỉ gây lệch hiển thị vô ích.
     */
    void onRespawn(int currentHp, int maxHp, int currentChi) {
        // ADR-036: máu tối đa phụ thuộc trang bị, nên LẤY của server thay vì đọc hằng số client.
        // maxHp <= 0 nghĩa là server cũ chưa gửi field (mặc định proto3) — giữ giá trị đang có.
        if (maxHp > 0) {
            playerMaxHp = maxHp;
        }
        // Kẹp SAU khi đã có mẫu số mới — xem applyHpUpdate.
        playerHp = MalformedPacket.clampPoints(currentHp, playerMaxHp);
        playerChi = MalformedPacket.clampPoints(currentChi, CombatConstants.CHARACTER_MAX_CHI);
        // Khí vừa lấy THẲNG của server — lần bấm đang chờ (nếu có) không còn gì để trả lại.
        castAwaitingConfirmation = false;
        setCurrentTarget(0L);
    }

    /**
     * Đếm ngược hồi chiêu, và trả lại Khí + hồi chiêu của lần bấm skill mà server không xác nhận
     * trong {@link CombatConstants#SKILL_CONFIRM_WINDOW_SECONDS}.
     *
     * <p>Server bỏ IM LẶNG mọi lệnh skill sai ({@code SkillSystem.tryCast}: ngoài tầm, bị choáng,
     * mục tiêu vừa chết, hồi chiêu phía server chưa xong...) và chỉ trừ Khí khi cast THẬT, còn Khí
     * phía client không có gói đồng bộ nào ngoài hồi sinh/đổi World. Không trả lại ở đây thì mỗi lần
     * bấm hụt là một lần Khí client tụt xa hơn Khí thật, tới khi nút chiêu xám hẳn dù server vẫn
     * đầy Khí (CLIENT.md §6: dự đoán sai thì sửa theo server — im lặng chính là câu trả lời).
     */
    void tick(float deltaTime) {
        if (skillCooldownRemaining > 0f) {
            skillCooldownRemaining = Math.max(0f, skillCooldownRemaining - deltaTime);
        }
        if (castAwaitingConfirmation) {
            castConfirmElapsedSeconds += deltaTime;
            if (castConfirmElapsedSeconds >= CombatConstants.SKILL_CONFIRM_WINDOW_SECONDS) {
                castAwaitingConfirmation = false;
                playerChi = Math.min(CombatConstants.CHARACTER_MAX_CHI, playerChi + pendingCastChiCost);
                skillCooldownRemaining = 0f;
            }
        }
    }

    boolean canUseSkill() {
        return skillCooldownRemaining <= 0f && playerChi >= skill.chiCost() && currentTargetId != 0L;
    }

    /**
     * Gọi khi người chơi bấm nút skill.
     *
     * @return {@code true} nếu hợp lệ CỤC BỘ (caller phải gửi {@code UseSkillCommand} ngay);
     *     {@code false} nếu chưa đủ điều kiện (đang hồi chiêu/thiếu Khí/chưa có mục tiêu) — không
     *     gửi gì, vì server sẽ âm thầm bỏ qua lệnh sai (SECURITY.md), gửi thừa chỉ tốn băng thông.
     */
    boolean useSkill() {
        if (!canUseSkill()) {
            return false;
        }
        skillCooldownRemaining = skill.cooldownSeconds();
        playerChi -= skill.chiCost();
        // Trừ NGAY để nút phản hồi tức thì, nhưng chỉ là dự đoán — xem tick() về lúc trả lại.
        castAwaitingConfirmation = true;
        castConfirmElapsedSeconds = 0f;
        pendingCastChiCost = skill.chiCost();
        return true;
    }

    /** {@code skill_code} của skill slot 0 thuộc hệ phái người chơi — dùng để gửi {@code UseSkillCommand}. */
    String skillCode() {
        return skill.skillCode();
    }

    /** i18n key tên hiển thị của skill slot 0 thuộc hệ phái người chơi — dùng cho nhãn nút skill. */
    String skillNameKey() {
        return skill.nameKey();
    }

    int playerHp() {
        return playerHp;
    }

    /** Khí hiện tại — HUD kiểu Ninja School hiện thanh Khí ngay dưới thanh Máu. */
    int playerChi() {
        return playerChi;
    }

    int playerMaxChi() {
        return CombatConstants.CHARACTER_MAX_CHI;
    }

    int playerMaxHp() {
        return playerMaxHp;
    }

    long currentTargetId() {
        return currentTargetId;
    }

    MonsterState targetState() {
        // Tra bằng khoá đã đóng hộp sẵn — gọi MỖI KHUNG (TargetHudSync), và monsters.get(long) đóng hộp
        // một Long mới mỗi lần (id quái âm, ngoài vùng cache của Long.valueOf): 24 B/khung (dòng 57).
        return currentTargetKey == null ? null : monsters.get(currentTargetKey);
    }

    /** Đổi mục tiêu — giữ {@link #currentTargetKey} khớp {@link #currentTargetId}, đóng hộp đúng một lần. */
    private void setCurrentTarget(long targetId) {
        currentTargetId = targetId;
        currentTargetKey = targetId == 0L ? null : Long.valueOf(targetId);
    }

    /**
     * Kho quái dạng CHỈ ĐỌC — luôn cùng một view dựng sẵn, vì người gọi đọc nó nhiều lần mỗi khung
     * render (bọc mới mỗi lần là cấp phát trong game loop, /CLAUDE.md §6).
     */
    Map<Long, MonsterState> monsters() {
        return monstersView;
    }

    float skillCooldownRemaining() {
        return skillCooldownRemaining;
    }

    /**
     * Nối kho vị trí nhân vật khác đang thấy ({@code RemoteCharacterPresenter#characters()}) — để
     * {@link #selectCharacterTarget} chỉ nhắm người đang thật sự có trên màn hình, và để
     * {@link #targetPoint()} biết mục tiêu đang đứng đâu (kiểm tầm cục bộ, neo số sát thương).
     * Giữ THAM CHIẾU, không sao chép: kho đó tự cập nhật theo spawn/move/despawn.
     */
    void bindCharacterPositions(Map<Long, ? extends PickablePoint> positions) {
        this.characterPositions = positions;
    }

    /**
     * Nhắm một NHÂN VẬT khác (đối thủ đấu trường — dòng 32 bảng chờ, phương án a, 2026-10-04).
     *
     * <p>Chỉ là Ý ĐỊNH: server tự kiểm lại ({@code WorldFacade#handleSetTarget} bỏ qua mục tiêu không
     * tồn tại trong World đó; {@code CombatSystem} quyết PvP có được phép không).
     *
     * @param displayName tên đã sanitize, chỉ để hiện trên HUD mục tiêu
     * @return {@code true} nếu mục tiêu vừa ĐỔI — caller phải gửi {@code SetTargetCommand}.
     *     {@code false} khi đó là chính mình, người đó không có trên màn hình, hoặc đã đang nhắm
     *     đúng người đó (không gửi gói thừa, cùng lý do {@link #selectTarget}).
     */
    boolean selectCharacterTarget(long characterId, String displayName) {
        if (characterId <= 0L
                || characterId == localCharacterId
                || characterId == currentTargetId
                || !characterPositions.containsKey(characterId)) {
            return false;
        }
        setCurrentTarget(characterId);
        characterTargetName = displayName;
        characterTargetHp = UNKNOWN_HP;
        characterTargetMaxHp = UNKNOWN_HP;
        return true;
    }

    /** Một nhân vật khác vừa rời tầm nhìn — đang nhắm người đó thì thôi nhắm. */
    void onCharacterDespawned(long characterId) {
        clearCharacterTarget(characterId);
    }

    /** Bỏ mục tiêu nếu (và chỉ nếu) đang nhắm đúng nhân vật này — hết trận, đối thủ biến mất. */
    void clearCharacterTarget(long characterId) {
        if (characterId > 0L && currentTargetId == characterId) {
            setCurrentTarget(0L);
        }
    }

    /** Mục tiêu hiện tại là một NHÂN VẬT (id dương) chứ không phải quái (id âm). */
    boolean hasCharacterTarget() {
        return currentTargetId > 0L;
    }

    /** Tên hiển thị của nhân vật đang nhắm — chỉ có nghĩa khi {@link #hasCharacterTarget()}. */
    String characterTargetName() {
        return characterTargetName;
    }

    /** Máu hiện tại của nhân vật đang nhắm theo gói server gần nhất, hoặc {@link #UNKNOWN_HP}. */
    int characterTargetHp() {
        return characterTargetHp;
    }

    /**
     * Máu tối đa của nhân vật đang nhắm, hoặc {@link #UNKNOWN_HP}. Chỉ {@code HealthChangedEvent}
     * mang con số này cho người KHÁC — {@code AttackResultEvent}/{@code SkillResultEvent} chỉ mang
     * máu còn lại, {@code CharacterSpawnedEvent} không mang máu. Từ 2026-10-18 (dòng 32) server gửi
     * một gói đó cho người nhắm ngay khi nhận {@code SetTargetCommand} vào một nhân vật, nên con số
     * này có ngay sau một vòng mạng — trước đó HUD đấu trường đứng ở "máu / ?" cả trận.
     */
    int characterTargetMaxHp() {
        return characterTargetMaxHp;
    }

    /**
     * Vị trí mục tiêu hiện tại, dù là quái hay nhân vật — rỗng khi không nhắm ai hoặc mục tiêu không
     * còn trên màn hình. Gọi theo nhịp sự kiện/bấm nút, KHÔNG mỗi khung (tra {@code Map<Long, …>} là
     * boxing).
     */
    Optional<PickablePoint> targetPoint() {
        if (currentTargetId == 0L) {
            return Optional.empty();
        }
        PickablePoint point =
                currentTargetId < 0L ? monsters.get(currentTargetId) : characterPositions.get(currentTargetId);
        return Optional.ofNullable(point);
    }
}
