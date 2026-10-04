package dev.kage.server.database.economy;

import java.util.Map;

import javax.sql.DataSource;

/**
 * Mọi thứ một bài chạy chia sẻ giữa các luồng và bộ kiểm bất biến: Facade thật, bảng tra vật phẩm,
 * sổ phía bộ lái, danh sách nhân vật và trạng thái BAN ĐẦU đã biết (vàng mỗi người, tổng số lượng
 * theo loại) — thứ mọi bất biến bảo toàn lấy làm gốc.
 */
record EconomyStressWorld(
        DataSource dataSource,
        long accountId,
        EconomyStressFacades facades,
        EconomyStressCatalog catalog,
        EconomyStressTally tally,
        long[] characters,
        long initialGoldEach,
        Map<Long, Long> initialQuantityByTemplate) {

    EconomyStressWorld {
        characters = characters.clone();
        initialQuantityByTemplate = Map.copyOf(initialQuantityByTemplate);
    }

    @Override
    public long[] characters() {
        return characters.clone();
    }
}
