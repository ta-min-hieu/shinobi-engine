package dev.kage.server.database.inventory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.DropTablePort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link DropTablePort} trên PostgreSQL — đọc thuần, không random (roll ở
 * {@code LootRoller}, feature-inventory/domain — Điều 24: SQL chỉ ở đây).
 *
 * <p><b>Cảnh báo mã bảng rơi không tồn tại.</b> {@link DropTablePort#findEntries} trả danh sách
 * rỗng cho CẢ HAI trường hợp: "quái này cố ý không rơi gì" và "mã bảng rơi không khớp dòng nào
 * trong {@code drop_table}". Trường hợp thứ hai là một lỗi cấu hình câm — một con quái mới gõ sai
 * mã sẽ không rơi đồ VĨNH VIỄN mà không ai biết, vì mọi tầng phía trên đều coi rỗng là hợp lệ. Lớp
 * này phân biệt hai trường hợp đó và ghi WARN <b>một lần cho mỗi mã lạ</b> (xem
 * {@link #warnedMissingCodes}) — không đổi kiểu trả về, không ném lỗi: một mã sai không đáng làm
 * sập lượt giết quái của người chơi.
 */
public final class PostgresDropTableAdapter implements DropTablePort {

    private static final String FIND_ENTRIES = """
            SELECT e.item_template_id, e.drop_chance_percent, e.min_quantity, e.max_quantity
              FROM drop_table_entry e
              JOIN drop_table dt ON dt.id = e.drop_table_id
             WHERE dt.code = ?
            """;

    private static final Logger LOG = LoggerFactory.getLogger(PostgresDropTableAdapter.class);

    private static final String EXISTS_DROP_TABLE = "SELECT 1 FROM drop_table WHERE code = ?";

    /**
     * Mã đã cảnh báo rồi — chỉ để KHÔNG lặp lại. Quái chết liên tục nên nếu không chặn, một mã sai
     * sẽ đẻ ra một dòng log mỗi lần chết (Điều 31: không log theo nhịp gameplay). Tập này chỉ lớn
     * bằng số mã SAI thật sự có, tức là gần như luôn rỗng.
     */
    private final Set<String> warnedMissingCodes = ConcurrentHashMap.newKeySet();

    private final DataSource dataSource;

    public PostgresDropTableAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<DropTableEntry> findEntries(String dropTableCode) {
        List<DropTableEntry> entries = JdbcOperations.query(
                dataSource,
                FIND_ENTRIES,
                rs -> new DropTableEntry(
                        rs.getLong("item_template_id"),
                        rs.getInt("drop_chance_percent"),
                        rs.getInt("min_quantity"),
                        rs.getInt("max_quantity")),
                dropTableCode);
        if (entries.isEmpty()) {
            warnOnceIfCodeMissing(dropTableCode);
        }
        return entries;
    }

    /**
     * Truy vấn thứ hai chỉ chạy khi danh sách rỗng, và chỉ một lần cho mỗi mã — đường chạy bình
     * thường (bảng rơi có dữ liệu) không tốn thêm lượt đi DB nào.
     */
    private void warnOnceIfCodeMissing(String dropTableCode) {
        if (!warnedMissingCodes.add(dropTableCode)) {
            return;
        }
        boolean exists = JdbcOperations.queryOne(dataSource, EXISTS_DROP_TABLE, rs -> rs.getInt(1), dropTableCode)
                .isPresent();
        if (exists) {
            // Bảng có thật nhưng chưa có dòng entry nào — có thể cố ý (quái không rơi gì), nói ở
            // mức INFO để người vận hành thấy mà không coi là sự cố.
            LOG.info("Bảng rơi '{}' tồn tại nhưng chưa có entry nào — quái này sẽ không rơi gì", dropTableCode);
            return;
        }
        LOG.warn(
                "Không có bảng rơi nào mang mã '{}' — quái dùng mã này sẽ KHÔNG BAO GIỜ rơi đồ."
                        + " Kiểm lại mã quái so với cột drop_table.code (thường là gõ sai mã hoặc"
                        + " thiếu migration seed).",
                dropTableCode);
    }
}
