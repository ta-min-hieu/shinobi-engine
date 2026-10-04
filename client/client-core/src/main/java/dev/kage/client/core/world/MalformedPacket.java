package dev.kage.client.core.world;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.NpcSpawnedEvent;

/**
 * Gói HỎNG từ server — đúng kiểu protobuf nhưng mang dữ liệu không thể hiển thị (toạ độ NaN/vô cực,
 * id sai không gian, máu ngoài khoảng). Client không quyết gameplay (Điều 15) nên không "sửa" gì
 * cả: chỉ BỎ gói đó (hoặc kẹp con số về khoảng hiển thị được) và nói ra trong log.
 *
 * <p>Log có TRẦN: một server hỏng có thể gửi cùng một gói hỏng mỗi tick, và một dòng WARN mỗi
 * khung là đúng thứ bị cấm trong game loop (/CLAUDE.md §6). {@link #WARN_LIMIT} dòng đầu đủ để
 * Closed Beta đọc ra nguyên nhân; sau đó chỉ báo một lần rằng đã ngừng ghi.
 *
 * <p>Không gian id (combat.proto, {@code AreaOfInterestTracker}): DƯƠNG là nhân vật, ÂM là quái hoặc
 * NPC (chung một bộ đếm phía server), 0 là "không có". Đây là hợp đồng, không phải suy đoán — cả
 * {@link CombatHudController} lẫn {@code TargetComponent} phía server đều dựa vào nó.
 *
 * <p>Tìm ra bởi {@code InboundPacketFuzzTest} (2026-10-04).
 */
final class MalformedPacket {

    static final int WARN_LIMIT = 50;

    private static final Logger LOG = LoggerFactory.getLogger(MalformedPacket.class);

    private static final AtomicInteger REPORTED = new AtomicInteger();

    private MalformedPacket() {}

    /**
     * Ghi nhận một gói vừa bị bỏ/kẹp.
     *
     * @param what gói gì và vì sao (chuỗi hằng — không ghép chuỗi ở chỗ gọi)
     * @param entityId id thực thể gói nói tới, để đối chiếu với log server
     */
    static void report(String what, long entityId) {
        int count = REPORTED.incrementAndGet();
        if (count <= WARN_LIMIT) {
            LOG.warn("Gói hỏng từ server, đã bỏ/kẹp: {} (id={})", what, entityId);
        } else if (count == WARN_LIMIT + 1) {
            LOG.warn("Đã gặp hơn {} gói hỏng từ server — ngừng ghi từng gói", WARN_LIMIT);
        }
    }

    /** Nhân vật KHÁC vào tầm nhìn: id dương, không phải chính mình, toạ độ hữu hạn. */
    static boolean accepts(CharacterSpawnedEvent event, long localCharacterId) {
        long id = event.getCharacterId();
        if (!isCharacterId(id) || id == localCharacterId) {
            report("CharacterSpawnedEvent id không phải nhân vật khác", id);
            return false;
        }
        return acceptsPosition("CharacterSpawnedEvent", id, event.getX(), event.getY());
    }

    static boolean accepts(CharacterMovedEvent event) {
        return acceptsPosition("CharacterMovedEvent", event.getCharacterId(), event.getX(), event.getY());
    }

    /** Quái vào tầm nhìn: id ÂM (dương là nhân vật — kể cả chính mình), toạ độ hữu hạn. */
    static boolean accepts(MonsterSpawnedEvent event) {
        long id = event.getMonsterId();
        if (!isMonsterOrNpcId(id)) {
            report("MonsterSpawnedEvent id không phải quái", id);
            return false;
        }
        return acceptsPosition("MonsterSpawnedEvent", id, event.getX(), event.getY());
    }

    /** Như {@link #accepts(MonsterSpawnedEvent)} nhưng KHÔNG ghi log — cho người nhận thứ hai của cùng gói. */
    static boolean isDisplayable(MonsterSpawnedEvent event) {
        return isMonsterOrNpcId(event.getMonsterId()) && isFinite(event.getX(), event.getY());
    }

    static boolean accepts(MonsterMovedEvent event) {
        return acceptsPosition("MonsterMovedEvent", event.getMonsterId(), event.getX(), event.getY());
    }

    /** NPC vào tầm nhìn: id ÂM, toạ độ hữu hạn. */
    static boolean accepts(NpcSpawnedEvent event) {
        long id = event.getNpcId();
        if (!isMonsterOrNpcId(id)) {
            report("NpcSpawnedEvent id không phải NPC", id);
            return false;
        }
        return acceptsPosition("NpcSpawnedEvent", id, event.getX(), event.getY());
    }

    private static boolean acceptsPosition(String what, long id, float x, float y) {
        if (isFinite(x, y)) {
            return true;
        }
        report(what + " toạ độ không hữu hạn", id);
        return false;
    }

    static boolean isFinite(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y);
    }

    /** Id nhân vật hợp lệ: dương. */
    static boolean isCharacterId(long id) {
        return id > 0L;
    }

    /** Id quái/NPC hợp lệ: âm. */
    static boolean isMonsterOrNpcId(long id) {
        return id < 0L;
    }

    /**
     * Máu/Khí kẹp về khoảng hiển thị được: {@code [0, max]} khi đã biết {@code max} (&gt; 0), chỉ
     * chặn dưới ở 0 khi chưa biết.
     */
    static int clampPoints(int current, int max) {
        if (max > 0) {
            return Math.clamp(current, 0, max);
        }
        return Math.max(0, current);
    }
}
