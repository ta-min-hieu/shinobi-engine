package dev.kage.tools.loadtest.probe;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import dev.kage.tools.loadtest.CommandLineArguments;

/**
 * Kịch bản 1 lần chạy probe — parse từ {@code --key=value} (xem {@link CommandLineArguments}).
 *
 * <p>Toàn bộ tham số đều có mặc định chạy được ngay trên môi trường dev (tài khoản playtest
 * {@code kagetest}), vì công cụ này sinh ra để trả lời NHANH một câu hỏi cụ thể chứ không phải để
 * cấu hình lâu dài.
 *
 * @param host địa chỉ gateway
 * @param port cổng gateway (mặc định 7777)
 * @param username tên account gửi trong {@code ConnectRequest}
 * @param characterId 0 = không chỉ định, chọn theo {@code characterName} hoặc nhân vật đầu tiên
 * @param characterName rỗng = không chỉ định
 * @param moveToX rỗng = đứng yên tại chỗ server cho spawn; có giá trị = đi bộ THẬT tới toạ độ đó
 * @param attackTarget {@code none} | {@code nearest} | id thực thể cụ thể (số, âm = quái)
 * @param attackIntervalMillis nhịp gửi {@code AttackCommand} (cooldown thật do server giữ)
 * @param observeSeconds thời gian đứng đọc sự kiện sau khi đã tới chỗ
 * @param equipItemCodes danh sách mã trang bị cần MẶC trước khi đi bộ (ô trang bị suy từ catalog)
 * @param quest kịch bản nhiệm vụ (nói chuyện NPC / lĩnh thưởng / in nhật ký) — xem {@link QuestPlan}
 * @param enterDungeon mã hầm ngục để tự lập nhóm 1 người rồi vào, sau khi đi bộ; rỗng = không vào —
 *     xem {@link DungeonScenario}
 */
public record ProbeConfig(
        String host,
        int port,
        String username,
        long characterId,
        String characterName,
        OptionalDouble moveToX,
        String attackTarget,
        int attackIntervalMillis,
        int observeSeconds,
        String useItemCode,
        OptionalDouble returnToX,
        int settleSeconds,
        List<String> equipItemCodes,
        QuestPlan quest,
        String enterDungeon) {

    /**
     * Danh sách mã trang bị BẤT BIẾN — record không tự copy, mà lệnh gọi có thể truyền list sống
     * (đúng lý do {@link QuestPlan} đã làm với {@code dialogueChoices}). Thiếu chỗ này thì SpotBugs
     * bắt đúng hai lỗi: {@code EI_EXPOSE_REP2} (constructor giữ list của người ngoài) và
     * {@code EI_EXPOSE_REP} (getter trả thẳng list nội bộ ra).
     */
    public ProbeConfig {
        equipItemCodes = List.copyOf(equipItemCodes);
    }

    /**
     * Phần kịch bản NHIỆM VỤ của một lần chạy probe — tách khỏi {@link ProbeConfig} để chữ ký
     * record không vượt ngưỡng {@code ParameterNumber} và để nhóm tham số đi cùng nhau nằm cùng
     * chỗ (cùng cách {@code GameConnection.GatewayEndpoint} gộp địa chỉ gateway).
     *
     * @param npcCode NPC để mở hội thoại ({@code TalkToNpcCommand}); rỗng = không nói chuyện
     * @param dialogueChoices đường đi trong cây hội thoại, mỗi phần tử là CHỈ SỐ lựa chọn (số) hoặc
     *     KHOÁ ({@code action} hay {@code label_key}) — xem {@link DialogueChoice}
     * @param claimQuestCode quest để gửi {@code ClaimQuestRewardCommand}; rỗng = không lĩnh thưởng
     * @param report {@code true} = in nhật ký nhiệm vụ + túi đồ ngay cả khi không làm gì khác
     */
    public record QuestPlan(String npcCode, List<String> dialogueChoices, String claimQuestCode, boolean report) {

        /** Danh sách bước bất biến — record không tự copy, mà lệnh gọi có thể truyền list sống. */
        public QuestPlan {
            dialogueChoices = List.copyOf(dialogueChoices);
        }

        /** {@code true} khi có ít nhất một việc phải làm trước khi vào vòng quan sát. */
        public boolean isRequested() {
            return report || !npcCode.isEmpty() || !claimQuestCode.isEmpty();
        }
    }

    /** Giá trị {@code attackTarget} nghĩa là "không đánh, chỉ quan sát". */
    public static final String ATTACK_TARGET_NONE = "none";

    /** Giá trị {@code attackTarget} nghĩa là "đánh con quái gần nhất lúc bắt đầu quan sát". */
    public static final String ATTACK_TARGET_NEAREST = "nearest";

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7777;
    private static final String DEFAULT_USERNAME = "kagetest";
    private static final int DEFAULT_ATTACK_INTERVAL_MILLIS = 1000; // CombatConstants.ATTACK_INTERVAL_SECONDS = 1s
    private static final int DEFAULT_OBSERVE_SECONDS = 10;

    /** @param args mảng tham số thô của {@code main} */
    public static ProbeConfig parse(String[] args) {
        CommandLineArguments raw = CommandLineArguments.parse(args);
        return new ProbeConfig(
                raw.text("host", DEFAULT_HOST),
                raw.integer("port", DEFAULT_PORT),
                raw.text("username", DEFAULT_USERNAME),
                raw.number("characterId", 0L),
                raw.text("characterName", ""),
                raw.optionalDouble("moveToX"),
                raw.text("attackTarget", ATTACK_TARGET_NONE).toLowerCase(Locale.ROOT),
                raw.integer("attackIntervalMillis", DEFAULT_ATTACK_INTERVAL_MILLIS),
                raw.integer("observeSeconds", DEFAULT_OBSERVE_SECONDS),
                raw.text("useItemCode", ""),
                raw.optionalDouble("returnToX"),
                raw.integer("settleSeconds", 0),
                List.copyOf(raw.list("equipItemCodes")),
                new QuestPlan(
                        raw.text("talkToNpc", ""),
                        raw.list("dialogueChoices"),
                        raw.text("claimQuest", ""),
                        raw.flag("questReport", false)),
                raw.text("enterDungeon", ""));
    }

    /** {@code true} khi người dùng muốn probe tự chọn con gần nhất trong AoI. */
    public boolean attacksNearest() {
        return ATTACK_TARGET_NEAREST.equals(attackTarget);
    }

    /**
     * Id thực thể người dùng chỉ đích danh, rỗng nếu {@code attackTarget} là {@code none} hoặc
     * {@code nearest}.
     *
     * @throws NumberFormatException nếu giá trị không phải {@code none}/{@code nearest} và cũng
     *     không phải số — gõ sai thì dừng ngay, đừng âm thầm chuyển thành "không đánh"
     */
    public OptionalLong explicitAttackTargetId() {
        if (ATTACK_TARGET_NONE.equals(attackTarget) || attacksNearest()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(attackTarget));
    }
}
