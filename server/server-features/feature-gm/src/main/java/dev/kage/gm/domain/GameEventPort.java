package dev.kage.gm.domain;

import java.util.List;

/** Lưu sự kiện để khởi động lại server không làm mất nó (ADR-067). Hiện thực ở server-database. */
public interface GameEventPort {

    /** Không lưu gì (composition test). */
    GameEventPort NONE = new GameEventPort() {
        @Override
        public List<GameEvent> loadAll() {
            return List.of();
        }

        @Override
        public void save(GameEvent event, long gmAuditLogId) {
            // không lưu
        }

        @Override
        public void clear(GameEventKind kind) {
            // không lưu
        }
    };

    /** Mọi dòng, kể cả đã hết hạn (người gọi lọc theo đồng hồ). */
    List<GameEvent> loadAll();

    /** Ghi đè sự kiện cùng loại. */
    void save(GameEvent event, long gmAuditLogId);

    void clear(GameEventKind kind);
}
