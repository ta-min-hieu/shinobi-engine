package dev.kage.monster.api;

import java.util.List;

import dev.kage.monster.domain.MonsterCatalogPort;
import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.monster.domain.MonsterTemplate;

/**
 * API công khai của {@code feature-monster} — module khác ({@code server-gateway}) CHỈ gọi qua đây,
 * không đọc bảng {@code monster_template}/{@code monster_spawn} trực tiếp (Điều 24).
 *
 * <p>Đọc-thuần, không có use case nào: đây là tra cứu config nạp một lần lúc khởi động. Chỗ gọi
 * (gateway) tự giữ kết quả và dịch sang record của {@code server-world} — module này KHÔNG biết
 * {@code server-world} tồn tại, đúng khuôn ADR-036 đã dựng cho chỉ số trang bị.
 */
public final class MonsterFacade {

    private final MonsterCatalogPort catalogPort;

    public MonsterFacade(MonsterCatalogPort catalogPort) {
        this.catalogPort = catalogPort;
    }

    /** Mọi loài quái đã khai báo trong {@code monster_template}. */
    public List<MonsterTemplate> listTemplates() {
        return catalogPort.listTemplates();
    }

    /**
     * Điểm spawn của {@code worldCode}.
     *
     * @return rỗng nếu world đó không khai điểm spawn nào (dungeon/arena) — không phải lỗi
     */
    public List<MonsterSpawnPoint> listSpawnPoints(String worldCode) {
        return catalogPort.listSpawnPoints(worldCode);
    }
}
