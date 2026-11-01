package dev.kage.tools.loadtest.probe;

import java.util.Locale;
import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.AcceptDuelResponse;
import dev.kage.protocol.AcceptQuestResponse;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.ChallengeDuelResponse;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.CreatePartyResponse;
import dev.kage.protocol.DialogueNodeResponse;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ExitDuelResponse;
import dev.kage.protocol.ExitDungeonResponse;
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.GoldChangedEvent;
import dev.kage.protocol.HealthChangedEvent;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.ListQuestsResponse;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.MovementModifierChangedEvent;
import dev.kage.protocol.NpcDespawnedEvent;
import dev.kage.protocol.NpcSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestStatusChangedEvent;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Biến 1 frame nhận được thành MỘT DÒNG có cấu trúc (TSV) — đây là toàn bộ giá trị của probe:
 * ROADMAP mục 58 ghi rõ ba lần trong ngày 2026-09-05 kết luận từ ảnh chụp màn hình đã sai hoặc
 * không phân giải được, nên câu trả lời phải là SỐ đọc thẳng từ dây.
 *
 * <p>Định dạng: {@code <ms từ lúc bắt đầu>\t<opcode>\t<TÊN>\t<k=v k=v ...>}. TSV chứ không phải
 * JSON vì công cụ tiêu thụ chính là {@code grep}/mắt người, và mỗi dòng phải đọc được không cần
 * parser.
 *
 * <p>Hàm THUẦN, không I/O — vì thế test được không cần server (xem {@code ProbeEventLineTest}).
 */
final class ProbeEventLine {

    private static final String SEPARATOR = "\t";
    private static final String OPCODE_NAME_PREFIX = "OPCODE_";

    private ProbeEventLine() {}

    /**
     * @param elapsedMillis mốc thời gian nhận, tính từ lúc probe bắt đầu
     * @throws InvalidProtocolBufferException nếu payload không khớp message của opcode đó — KHÔNG
     *     nuốt: opcode nói một đằng payload một nẻo là bug giao thức thật, phải nổ ra
     */
    static String format(long elapsedMillis, PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        return elapsedMillis
                + SEPARATOR
                + opcode
                + SEPARATOR
                + name(opcode)
                + SEPARATOR
                + describe(opcode, frame.payload());
    }

    /** Tên opcode đã bỏ tiền tố {@code OPCODE_}; {@code UNKNOWN} nếu số không có trong registry. */
    static String name(int opcode) {
        Opcode known = Opcode.forNumber(opcode);
        if (known == null) {
            return "UNKNOWN";
        }
        String rawName = known.name();
        return rawName.startsWith(OPCODE_NAME_PREFIX) ? rawName.substring(OPCODE_NAME_PREFIX.length()) : rawName;
    }

    private static String describe(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        Optional<String> combat = describeCombat(opcode, payload);
        if (combat.isPresent()) {
            return combat.get();
        }
        Optional<String> movement = describeMovement(opcode, payload);
        if (movement.isPresent()) {
            return movement.get();
        }
        Optional<String> selfState = describeSelfState(opcode, payload);
        if (selfState.isPresent()) {
            return selfState.get();
        }
        Optional<String> worldChange = describeWorldChange(opcode, payload);
        if (worldChange.isPresent()) {
            return worldChange.get();
        }
        Optional<String> duel = describeDuel(opcode, payload);
        if (duel.isPresent()) {
            return duel.get();
        }
        Optional<String> social = SocialEventLine.describe(opcode, payload);
        if (social.isPresent()) {
            return social.get();
        }
        // Opcode chưa giải mã vẫn được ghi ra (tên + số byte) thay vì bỏ im lặng — probe phải cho
        // thấy CÓ GÌ ĐÓ đã tới, kể cả khi nó chưa biết đọc.
        return describeQuest(opcode, payload).orElseGet(() -> "payloadBytes=" + payload.length);
    }

    /**
     * Vòng đời nhiệm vụ + túi đồ ({@link QuestScenario}) — hội thoại 603/605/606, nhật ký 607/611,
     * lĩnh thưởng 609, túi đồ 508. Chỉ SỐ và KHOÁ: nội dung chữ nằm ở bundle i18n phía client, server
     * không gửi và probe không bịa.
     */
    private static Optional<String> describeQuest(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        return switch (opcode) {
            case Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE ->
                Optional.of(dialogueNode(DialogueNodeResponse.parseFrom(payload)));
            case Opcode.OPCODE_DIALOGUE_ENDED_RESPONSE_VALUE -> Optional.of("dialogueEnded=true");
            case Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE ->
                Optional.of("outcome="
                        + AcceptQuestResponse.parseFrom(payload).getOutcome().name());
            case Opcode.OPCODE_QUEST_STATUS_CHANGED_EVENT_VALUE ->
                Optional.of(questStatus(QuestStatusChangedEvent.parseFrom(payload)));
            case Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE ->
                Optional.of(claimReward(ClaimQuestRewardResponse.parseFrom(payload)));
            case Opcode.OPCODE_LIST_QUESTS_RESPONSE_VALUE ->
                Optional.of("quests=" + ListQuestsResponse.parseFrom(payload).getQuestsCount());
            case Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE ->
                Optional.of(inventory(ListInventoryResponse.parseFrom(payload)));
            default -> Optional.empty();
        };
    }

    /**
     * Đổi World (ADR-051) — tạo nhóm 1301, vào/ra hầm ngục 1501/1503, và {@code WorldChangedEvent}
     * 308: gói DUY NHẤT nói người chơi đang ở World nào, đứng đâu, còn bao nhiêu máu sau khi đổi.
     */
    private static Optional<String> describeWorldChange(int opcode, byte[] payload)
            throws InvalidProtocolBufferException {
        return switch (opcode) {
            case Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE ->
                Optional.of(worldChanged(WorldChangedEvent.parseFrom(payload)));
            case Opcode.OPCODE_CREATE_PARTY_RESPONSE_VALUE ->
                Optional.of("outcome="
                        + CreatePartyResponse.parseFrom(payload).getOutcome().name());
            case Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE ->
                Optional.of("outcome="
                        + EnterDungeonResponse.parseFrom(payload).getOutcome().name());
            case Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE ->
                Optional.of("outcome="
                        + ExitDungeonResponse.parseFrom(payload).getOutcome().name());
            case Opcode.OPCODE_FORMATION_CHANGED_EVENT_VALUE -> { // ADR-128
                dev.kage.protocol.FormationChangedEvent f = dev.kage.protocol.FormationChangedEvent.parseFrom(payload);
                yield Optional.of("members=" + f.getMembers() + " tier="
                        + f.getTier().name() + " factions="
                        + f.getFactionsList() + " atk/def/res/hp=+" + f.getBonusAtk() + "/+" + f.getBonusDef() + "/+"
                        + f.getBonusRes() + "/+" + f.getBonusHp());
            }
            default -> Optional.empty();
        };
    }

    /** Arena 1v1 (1601/1603/1607/1608/1610/1611) — vào trận và chốt trận, cặp với 308 (ADR-051). */
    private static Optional<String> describeDuel(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        return switch (opcode) {
            case Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE -> {
                ChallengeDuelResponse response = ChallengeDuelResponse.parseFrom(payload);
                yield Optional.of(
                        "outcome=" + response.getOutcome().name() + " challengeId=" + response.getChallengeId());
            }
            case Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE ->
                Optional.of("outcome="
                        + AcceptDuelResponse.parseFrom(payload).getOutcome().name());
            case Opcode.OPCODE_EXIT_DUEL_RESPONSE_VALUE ->
                Optional.of("outcome="
                        + ExitDuelResponse.parseFrom(payload).getOutcome().name());
            case Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE -> {
                DuelChallengeReceivedEvent event = DuelChallengeReceivedEvent.parseFrom(payload);
                yield Optional.of("challengeId=" + event.getChallengeId() + " from=" + event.getChallengerCharacterId()
                        + " name=" + event.getChallengerName());
            }
            case Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE -> {
                DuelStartedEvent event = DuelStartedEvent.parseFrom(payload);
                yield Optional.of("opponent=" + event.getOpponentCharacterId() + " name=" + event.getOpponentName());
            }
            case Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE -> {
                DuelResultEvent event = DuelResultEvent.parseFrom(payload);
                yield Optional.of("winner=" + event.getWinnerCharacterId() + " loser=" + event.getLoserCharacterId()
                        + " winnerRating=" + event.getWinnerNewRating() + " loserRating="
                        + event.getLoserNewRating());
            }
            default -> Optional.empty();
        };
    }

    private static String worldChanged(WorldChangedEvent event) {
        return "world=" + event.getWorldKind().name() + " reason="
                + event.getReason().name() + " x="
                + meters(event.getX()) + " y=" + meters(event.getY()) + " hp=" + event.getCurrentHp() + "/"
                + event.getMaxHp();
    }

    private static String dialogueNode(DialogueNodeResponse response) {
        return "node=" + response.getNodeId() + " text=" + response.getTextKey() + " options="
                + response.getOptionsCount();
    }

    private static String questStatus(QuestStatusChangedEvent event) {
        return "quest=" + event.getQuestCode() + " status=" + event.getStatus().name();
    }

    private static String claimReward(ClaimQuestRewardResponse response) {
        return "outcome=" + response.getOutcome().name() + " expGranted=" + response.getExpGranted() + " items="
                + response.getItemsGrantedCount();
    }

    private static String inventory(ListInventoryResponse response) {
        return "items=" + response.getItemsCount() + " capacity=" + response.getCapacity() + " equipped="
                + response.getEquippedCount();
    }

    private static Optional<String> describeCombat(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        return switch (opcode) {
            case Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE -> Optional.of(attack(AttackResultEvent.parseFrom(payload)));
            case Opcode.OPCODE_HEALTH_CHANGED_EVENT_VALUE -> Optional.of(health(HealthChangedEvent.parseFrom(payload)));
            case Opcode.OPCODE_MOVEMENT_MODIFIER_CHANGED_EVENT_VALUE -> Optional.of(movementModifier(payload));
            case Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE -> Optional.of(skill(SkillResultEvent.parseFrom(payload)));
            case Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE ->
                Optional.of(monsterSpawn(MonsterSpawnedEvent.parseFrom(payload)));
            case Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE ->
                Optional.of(monsterMove(MonsterMovedEvent.parseFrom(payload)));
            case Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE ->
                Optional.of(
                        "monsterId=" + MonsterDespawnedEvent.parseFrom(payload).getMonsterId());
            default -> Optional.empty();
        };
    }

    private static Optional<String> describeMovement(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        return switch (opcode) {
            case Opcode.OPCODE_MOVE_RESPONSE_VALUE -> Optional.of(moveResponse(MoveResponse.parseFrom(payload)));
            case Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE ->
                Optional.of(characterSpawn(CharacterSpawnedEvent.parseFrom(payload)));
            case Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE ->
                Optional.of(characterMove(CharacterMovedEvent.parseFrom(payload)));
            case Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE ->
                Optional.of("characterId="
                        + CharacterDespawnedEvent.parseFrom(payload).getCharacterId());
            case Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE ->
                Optional.of(respawn(CharacterRespawnedEvent.parseFrom(payload)));
            default -> Optional.empty();
        };
    }

    private static Optional<String> describeSelfState(int opcode, byte[] payload)
            throws InvalidProtocolBufferException {
        return switch (opcode) {
            case Opcode.OPCODE_NPC_SPAWNED_EVENT_VALUE -> Optional.of(npcSpawn(NpcSpawnedEvent.parseFrom(payload)));
            case Opcode.OPCODE_NPC_DESPAWNED_EVENT_VALUE ->
                Optional.of("npcId=" + NpcDespawnedEvent.parseFrom(payload).getNpcId());
            case Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE ->
                Optional.of(experience(ExperienceChangedEvent.parseFrom(payload)));
            case Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE -> Optional.of(gold(GoldChangedEvent.parseFrom(payload)));
            case Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE -> Optional.of(item(ItemGrantedEvent.parseFrom(payload)));
            case Opcode.OPCODE_ERROR_VALUE -> Optional.of(error(ErrorResponse.parseFrom(payload)));
            case Opcode.OPCODE_WEATHER_EVENT_VALUE -> // ADR-144
                Optional.of("zones="
                        + dev.kage.protocol.WeatherEvent.parseFrom(payload).getZonesList().stream()
                                .map(z -> z.getCode() + "[" + z.getMinX() + "," + z.getMaxX() + "]:" + z.getKind())
                                .toList());
            default -> Optional.empty();
        };
    }

    private static String attack(AttackResultEvent event) {
        return "attacker=" + event.getAttackerEntityId() + " target=" + event.getTargetEntityId() + " damage="
                + event.getDamage() + " crit=" + event.getIsCrit() + " miss=" + event.getIsMiss() + " targetHp="
                + event.getTargetCurrentHp() + " targetDied=" + event.getTargetDied();
    }

    /** ADR-046 — máu đổi mà không do đòn đánh nào. Không có "ai gây ra": đó là chủ ý của D1. */
    private static String health(HealthChangedEvent event) {
        return "entity=" + event.getEntityId() + " hp=" + event.getCurrentHp() + "/" + event.getMaxHp();
    }

    private static String skill(SkillResultEvent event) {
        return "skill=" + event.getSkillCode() + " effect=" + event.getEffectKind() + " attacker="
                + event.getAttackerEntityId() + " target=" + event.getTargetEntityId() + " magnitude="
                + event.getDamage() + " crit=" + event.getIsCrit() + " miss=" + event.getIsMiss() + " targetHp="
                + event.getTargetCurrentHp() + " targetDied=" + event.getTargetDied();
    }

    private static String monsterSpawn(MonsterSpawnedEvent event) {
        return "monsterId=" + event.getMonsterId() + " code=" + event.getMonsterCode() + " model="
                + event.getModelKey() + " x=" + meters(event.getX()) + " y=" + meters(event.getY()) + " hp="
                + event.getCurrentHp() + "/" + event.getMaxHp();
    }

    private static String monsterMove(MonsterMovedEvent event) {
        return "monsterId=" + event.getMonsterId() + " x=" + meters(event.getX()) + " y=" + meters(event.getY());
    }

    private static String moveResponse(MoveResponse response) {
        return "x=" + meters(response.getX()) + " y=" + meters(response.getY()) + " z=" + meters(response.getZ())
                + " grounded=" + response.getGrounded();
    }

    private static String characterSpawn(CharacterSpawnedEvent event) {
        return "characterId=" + event.getCharacterId() + " x=" + meters(event.getX()) + " y=" + meters(event.getY())
                + " faction=" + event.getFaction() + " costume=" + event.getCostumeTier() + " level="
                + event.getLevel() + " outlaw=" + event.getOutlaw();
    }

    private static String characterMove(CharacterMovedEvent event) {
        return "characterId=" + event.getCharacterId() + " x=" + meters(event.getX()) + " y=" + meters(event.getY());
    }

    private static String respawn(CharacterRespawnedEvent event) {
        return "x=" + meters(event.getX()) + " y=" + meters(event.getY()) + " hp=" + event.getCurrentHp() + "/"
                + event.getMaxHp() + " chi=" + event.getCurrentChi() + "/" + event.getMaxChi();
    }

    private static String npcSpawn(NpcSpawnedEvent event) {
        return "npcId=" + event.getNpcId() + " code=" + event.getNpcCode() + " x=" + meters(event.getX()) + " y="
                + meters(event.getY());
    }

    private static String experience(ExperienceChangedEvent event) {
        return "level=" + event.getLevel() + " totalExp=" + event.getTotalExp() + " intoLevel="
                + event.getExpIntoLevel() + " forNext=" + event.getExpForNextLevel() + " leveledUp="
                + event.getLeveledUp();
    }

    private static String gold(GoldChangedEvent event) {
        return "gold=" + event.getGold() + " delta=" + event.getDelta() + " reason=" + event.getReasonKey();
    }

    private static String item(ItemGrantedEvent event) {
        return "itemId=" + event.getItemId() + " quantity=" + event.getQuantity();
    }

    private static String error(ErrorResponse response) {
        return "code=" + response.getErrorCode() + " key=" + response.getErrorKey();
    }

    /**
     * {@link Locale#ROOT} là bắt buộc, không phải thói quen: máy dev của dự án chạy locale Việt
     * Nam, {@code %.2f} mặc định in "24,00" — dấu phẩy sẽ phá luôn định dạng của dòng và làm mọi
     * phép so sánh số sau đó thành rác.
     */
    static String meters(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** ADR-073 — hệ số tốc độ do hiệu ứng + đang choáng/gục của chính mình. */
    private static String movementModifier(byte[] payload) throws InvalidProtocolBufferException {
        MovementModifierChangedEvent event = MovementModifierChangedEvent.parseFrom(payload);
        return String.format(
                Locale.ROOT,
                "statusSpeed=%.2f immobilized=%b",
                event.getStatusSpeedMultiplier(),
                event.getImmobilized());
    }
}
