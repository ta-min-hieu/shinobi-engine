package dev.kage.tools.loadtest.probe;

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
        int observeSeconds) {

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
                raw.integer("observeSeconds", DEFAULT_OBSERVE_SECONDS));
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
