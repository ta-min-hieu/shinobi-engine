package dev.kage.monster.domain;

import java.util.List;

/** Đọc {@code monster_template}/{@code monster_spawn} — adapter thật ở {@code server-database}. */
public interface MonsterCatalogPort {

    /** Mọi loài quái đã khai báo. Thứ tự KHÔNG bảo đảm. */
    List<MonsterTemplate> listTemplates();

    /**
     * Điểm spawn của một world.
     *
     * @param worldCode {@link MonsterSpawnPoint#MAIN_WORLD_CODE} cho world chính
     * @return rỗng nếu world đó không khai điểm spawn nào — hợp lệ, không phải lỗi (dungeon/arena)
     */
    List<MonsterSpawnPoint> listSpawnPoints(String worldCode);
}
