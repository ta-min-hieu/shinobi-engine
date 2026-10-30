package dev.kage.server.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link WorldOutboundSink} giả của bộ kiểm thuộc tính — dựng lại "client NÀO đang thấy entity NÀO"
 * CHỈ từ luồng sự kiện (đúng thứ một client thật biết), rồi kiểm từng sự kiện NGAY lúc nó được phát.
 *
 * <p>Luật kiểm theo sự kiện:
 *
 * <ul>
 *   <li>Người nhận phải đang gắn vào world lúc nhận (không gói nào gửi cho phiên đã rời).
 *   <li>Spawn khi đang thấy / despawn khi không thấy = lệch sổ AoI.
 *   <li>Sự kiện nhắc tới một entity mà CHÍNH người nhận đó đã nhận despawn (chưa spawn lại) = "bóng
 *       ma" — client không còn gì để gắn sự kiện vào. Riêng NGƯỜI ĐÁNH trong kết quả đòn thì không
 *       (người nhận chọn theo mục tiêu — xem {@link #softReference}).
 *   <li>Máu trong mọi ảnh chụp nằm trong {@code [0, max]}; {@code targetDied} khớp máu 0; hồi sinh
 *       đầy máu tại điểm hồi sinh.
 * </ul>
 *
 * <p>Nhắc tới một entity người nhận CHƯA từng được spawn (không phải bóng ma — ví dụ quái vừa hồi
 * sinh đánh trước lần sync AoI kế) được ĐẾM riêng ({@link #unseenReferenceCount()}), không coi là vi
 * phạm: AoI sync chạy 20Hz có chủ đích (WorldConstants), nên khe ≤ 2 tick đó là thiết kế.
 */
final class InvariantCheckingSink implements WorldOutboundSink {

    private static final int MAX_EXAMPLES = 5;
    private static final long DIGEST_MULTIPLIER = 1_000_003L;

    private final Map<Long, Set<Long>> seen = new HashMap<>();
    private final Map<Long, Set<Long>> despawned = new HashMap<>();
    private final List<String> violations = new ArrayList<>();
    private final List<String> unseenExamples = new ArrayList<>();
    private final Set<Long> attackersThisTick = new HashSet<>();
    private final Set<Long> respawnedThisTick = new HashSet<>();
    private final Map<String, Integer> eventCounts = new HashMap<>();
    private WorldFacade world;
    private long tick;
    private long unseenReferences;
    private long eventDigest = 1L;
    private long eventTotal;

    void bind(WorldFacade boundWorld) {
        this.world = boundWorld;
    }

    /** Gọi trước mỗi tick: đặt số tick cho thông báo lỗi và xoá các tập "trong tick này". */
    void beginTick(long tickNumber) {
        this.tick = tickNumber;
        attackersThisTick.clear();
        respawnedThisTick.clear();
    }

    /** Một phiên mới của {@code characterId} bắt đầu (lệnh attach vừa xếp hàng) — client mới, sổ trắng. */
    void onSessionStarted(long characterId) {
        seen.put(characterId, new HashSet<>());
        despawned.put(characterId, new HashSet<>());
        // Nhân vật quay lại là một "hiện thân" MỚI của cùng id: với người khác, nhắc tới nó trước lần
        // sync AoI kế là "chưa thấy" (khe ≤ 2 tick, thiết kế), không còn là bóng ma của lần trước.
        for (Set<Long> gone : despawned.values()) {
            gone.remove(characterId);
        }
    }

    List<String> violations() {
        return violations;
    }

    long unseenReferenceCount() {
        return unseenReferences;
    }

    List<String> unseenExamples() {
        return unseenExamples;
    }

    Set<Long> attackersThisTick() {
        return attackersThisTick;
    }

    Set<Long> respawnedThisTick() {
        return respawnedThisTick;
    }

    Map<String, Integer> eventCounts() {
        return eventCounts;
    }

    /**
     * Dấu vân tay THEO THỨ TỰ của toàn bộ luồng sự kiện (tick, loại, người nhận, nội dung) — hai lượt
     * chạy cùng seed phải ra cùng số này; khác ⇒ có nguồn bất định lọt vào mô phỏng hoặc bộ kiểm.
     */
    long eventDigest() {
        return eventDigest;
    }

    /** Tổng số sự kiện đã nhận — đi kèm {@link #eventDigest()} cho thông báo đỏ dễ đọc. */
    long eventTotal() {
        return eventTotal;
    }

    /** Sổ "đang thấy" dựng từ sự kiện của {@code observerId} — rỗng nếu chưa từng có phiên. */
    Set<Long> seenBy(long observerId) {
        return seen.getOrDefault(observerId, Set.of());
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public void onMoveAccepted(MoveAcceptedSnapshot accepted) {
        count("moveAccepted", accepted);
        recipient(accepted.characterId(), "MoveAccepted");
        if (!Float.isFinite(accepted.x()) || !Float.isFinite(accepted.z())) {
            violation("MoveAccepted mang toạ độ không hữu hạn: " + accepted);
        }
    }

    @Override
    public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
        count("characterSpawned", targetCharacterId + ">" + spawnedCharacterId + "@" + x + "," + y);
        spawn(targetCharacterId, spawnedCharacterId, "CharacterSpawned");
    }

    @Override
    public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {
        count("characterDespawned", targetCharacterId + ">" + despawnedCharacterId);
        despawn(targetCharacterId, despawnedCharacterId, "CharacterDespawned");
    }

    @Override
    public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {
        count("characterMoved", targetCharacterId + ">" + movedCharacterId + "@" + x + "," + y);
        recipient(targetCharacterId, "CharacterMoved");
        mustBeSeen(targetCharacterId, movedCharacterId, "CharacterMoved");
    }

    @Override
    public void onMonsterSpawned(long targetCharacterId, MonsterSnapshot monster) {
        count("monsterSpawned", targetCharacterId + ">" + monster);
        spawn(targetCharacterId, monster.monsterId(), "MonsterSpawned");
        if (monster.currentHp() <= 0 || monster.currentHp() > monster.maxHp()) {
            violation("MonsterSpawned với máu ngoài (0, max]: " + monster);
        }
    }

    @Override
    public void onMonsterDespawned(long targetCharacterId, long monsterId) {
        count("monsterDespawned", targetCharacterId + ">" + monsterId);
        despawn(targetCharacterId, monsterId, "MonsterDespawned");
    }

    @Override
    public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {
        count("monsterMoved", targetCharacterId + ">" + monsterId + "@" + x + "," + y);
        recipient(targetCharacterId, "MonsterMoved");
        mustBeSeen(targetCharacterId, monsterId, "MonsterMoved");
    }

    @Override
    public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {
        count("monsterKilled", killerCharacterId + ">" + monsterCode + "+" + expReward);
        recipient(killerCharacterId, "MonsterKilled");
    }

    @Override
    public void onNpcSpawned(long targetCharacterId, NpcSnapshot npc) {
        count("npcSpawned", targetCharacterId + ">" + npc);
        spawn(targetCharacterId, npc.npcId(), "NpcSpawned");
    }

    @Override
    public void onNpcDespawned(long targetCharacterId, long npcId) {
        count("npcDespawned", targetCharacterId + ">" + npcId);
        despawn(targetCharacterId, npcId, "NpcDespawned");
    }

    @Override
    public void onAttackResult(long targetCharacterId, AttackOutcome outcome) {
        count("attackResult", targetCharacterId + ">" + outcome);
        recipient(targetCharacterId, "AttackResult");
        softReference(targetCharacterId, outcome.attackerEntityId(), "AttackResult.attacker");
        reference(targetCharacterId, outcome.targetEntityId(), "AttackResult.target");
        noteAttacker(outcome.attackerEntityId());
        outcomeHp(outcome.targetCurrentHp(), outcome.targetDied(), outcome.damage(), outcome.toString());
    }

    @Override
    public void onSkillResult(long targetCharacterId, SkillOutcome outcome) {
        count("skillResult." + outcome.effectKind(), targetCharacterId + ">" + outcome);
        recipient(targetCharacterId, "SkillResult");
        softReference(targetCharacterId, outcome.attackerEntityId(), "SkillResult.attacker");
        reference(targetCharacterId, outcome.targetEntityId(), "SkillResult.target");
        noteAttacker(outcome.attackerEntityId());
        // BUFF/DEBUFF chở "độ lớn" theo phần trăm, có dấu (Phong Toả làm chậm = -40) — thiết kế
        // combat.proto SkillResultEvent, không phải sát thương âm.
        boolean signedMagnitude =
                outcome.effectKind() == SkillEffectKind.BUFF || outcome.effectKind() == SkillEffectKind.DEBUFF;
        int damage = signedMagnitude ? 0 : outcome.damage();
        outcomeHp(outcome.targetCurrentHp(), outcome.targetDied(), damage, outcome.toString());
    }

    @Override
    public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {
        count("areaChat", targetCharacterId + ">" + senderCharacterId + ":" + body);
        recipient(targetCharacterId, "AreaChat");
        reference(targetCharacterId, senderCharacterId, "AreaChat.sender");
    }

    @Override
    public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {
        count("appearanceChanged", targetCharacterId + ">" + changedCharacterId);
        recipient(targetCharacterId, "AppearanceChanged");
        reference(targetCharacterId, changedCharacterId, "AppearanceChanged");
    }

    @Override
    public void onHealthChanged(long targetCharacterId, HealthSnapshot health) {
        count("healthChanged", targetCharacterId + ">" + health);
        recipient(targetCharacterId, "HealthChanged");
        reference(targetCharacterId, health.entityId(), "HealthChanged");
        if (health.maxHp() < 1 || health.currentHp() < 0 || health.currentHp() > health.maxHp()) {
            violation("HealthChanged với máu ngoài [0, max]: " + health);
        }
    }

    @Override
    public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
        count("characterDefeated", defeatedCharacterId + "<" + killerEntityId);
        recipient(defeatedCharacterId, "CharacterDefeated");
    }

    @Override
    public void onCharacterRespawned(RespawnSnapshot respawn) {
        count("characterRespawned", respawn);
        recipient(respawn.characterId(), "CharacterRespawned");
        respawnedThisTick.add(respawn.characterId());
        boolean full = respawn.currentHp() == respawn.maxHp() && respawn.currentChi() == respawn.maxChi();
        boolean atSpawn = respawn.x() == 0f && respawn.y() == 0f;
        // ADR-117 — dạng hợp lệ thứ hai: Hồi Hồn Đan, đứng dậy TẠI CHỖ với đúng 30% máu (Chi giữ nguyên).
        boolean inPlace = respawn.currentHp() == PendingRevivals.inPlaceHp(respawn.maxHp());
        if (!(full && atSpawn) && !inPlace) {
            violation("Hồi sinh không đầy ở điểm hồi sinh, cũng không đúng 30% tại chỗ: " + respawn);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Đếm sự kiện và gộp nó (kèm tick + nội dung) vào {@link #eventDigest()}. */
    private void count(String eventName, Object detail) {
        eventCounts.merge(eventName, 1, Integer::sum);
        String line = tick + " " + eventName + " " + detail;
        eventDigest = eventDigest * DIGEST_MULTIPLIER + line.hashCode();
        eventTotal++;
    }

    private void violation(String message) {
        violations.add("tick " + tick + ": " + message);
    }

    private void recipient(long observerId, String what) {
        if (!world.characterEntitiesView().containsKey(observerId)) {
            violation(what + " gửi cho nhân vật " + observerId + " KHÔNG còn gắn vào world");
        }
    }

    private void spawn(long observerId, long entityId, String what) {
        recipient(observerId, what);
        if (!setOf(seen, observerId).add(entityId)) {
            violation(what + " lặp: " + observerId + " đã đang thấy " + entityId);
        }
        setOf(despawned, observerId).remove(entityId);
    }

    private void despawn(long observerId, long entityId, String what) {
        recipient(observerId, what);
        if (!setOf(seen, observerId).remove(entityId)) {
            violation(what + " cho thứ chưa từng spawn: " + observerId + " không thấy " + entityId);
        }
        setOf(despawned, observerId).add(entityId);
    }

    private static Set<Long> setOf(Map<Long, Set<Long>> sets, long observerId) {
        Set<Long> set = sets.get(observerId);
        if (set == null) {
            set = new HashSet<>();
            sets.put(observerId, set);
        }
        return set;
    }

    private void mustBeSeen(long observerId, long entityId, String what) {
        if (!seenBy(observerId).contains(entityId)) {
            violation(what + " về " + entityId + " gửi cho " + observerId + " — người nhận không thấy nó");
        }
    }

    private void reference(long observerId, long entityId, String what) {
        if (entityId == observerId) {
            return;
        }
        if (despawned.getOrDefault(observerId, Set.of()).contains(entityId)) {
            violation(what + " nhắc tới " + entityId + " — người nhận " + observerId + " đã nhận despawn của nó");
        } else if (!seenBy(observerId).contains(entityId)) {
            unseenReferences++;
            if (unseenExamples.size() < MAX_EXAMPLES) {
                unseenExamples.add("tick " + tick + ": " + what + " " + entityId + " → " + observerId);
            }
        }
    }

    /**
     * Người ĐÁNH trong kết quả đòn: người nhận được chọn theo MỤC TIÊU ({@code observersOf(target)}),
     * nên người đứng xem nạn nhân có thể chưa/không còn thấy kẻ đánh (đứng cách nạn nhân 14m, kẻ đánh ở
     * phía bên kia thêm 2m). Đó là thiết kế AoI, không phải bóng ma — chỉ đếm.
     */
    private void softReference(long observerId, long entityId, String what) {
        if (entityId != observerId && !seenBy(observerId).contains(entityId)) {
            unseenReferences++;
            if (unseenExamples.size() < MAX_EXAMPLES) {
                unseenExamples.add("tick " + tick + ": " + what + " " + entityId + " → " + observerId);
            }
        }
    }

    private void noteAttacker(long attackerEntityId) {
        if (attackerEntityId > 0) {
            attackersThisTick.add(attackerEntityId);
        }
    }

    private void outcomeHp(int targetCurrentHp, boolean targetDied, int damage, String text) {
        if (targetCurrentHp < 0 || damage < 0 || targetDied != (targetCurrentHp == 0)) {
            violation("Kết quả đòn sai máu/sát thương: " + text);
        }
    }
}
