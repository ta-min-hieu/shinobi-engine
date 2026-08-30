package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Đánh dấu entity là QUÁI (không phải nhân vật người chơi) + mã định danh loại quái
 * ({@code monster_template.code}, đọc từ DB kể từ ADR-037).
 *
 * <p>{@code modelKey} là khoá art ({@code monster_template.model_key}) đi thẳng ra client qua
 * {@code MonsterSpawnedEvent} — sinh ra để nhiều loài dùng CHUNG một model trong lúc chờ art riêng.
 * Client vẫn tự suy được từ {@code monsterCode} khi field này rỗng ({@code MonsterModelKey}), nên
 * đây là bổ sung chứ không phải thay thế.
 */
public final class MonsterComponent implements Component {
    public String monsterCode;
    public String modelKey;

    /**
     * EXP người hạ gục nhận được ({@code monster_template.exp_reward}). {@code server-world} KHÔNG
     * dùng con số này — nó chỉ chở ra ngoài qua {@code WorldOutboundSink#onMonsterKilled}, đúng như
     * nó đã chở {@code monsterCode} ra cho bảng rơi và nhiệm vụ.
     *
     * <p>Đi kèm lượt báo "quái chết" thay vì để gateway tra lại: EXP tới từ CÙNG một catalog đã nạp
     * một lần lúc khởi động, nên tra lại là mở một đường đọc thứ hai cho cùng một dữ liệu — và là
     * một tham số thứ năm cho {@code ConnectedCharacterRegistry}, vốn đã chạm trần Checkstyle với
     * 15 chỗ dựng.
     */
    public long expReward;
}
