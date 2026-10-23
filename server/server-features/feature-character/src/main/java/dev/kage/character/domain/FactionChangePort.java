package dev.kage.character.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Ghi một lần đổi linh căn (ADR-066) — đếm số lần, tiêu Tẩy Căn Đan, ghi hệ mới, trong MỘT transaction.
 * Hiện thực ở server-database (chạm cả {@code character} lẫn {@code inventory_item}).
 */
public interface FactionChangePort {

    /** Mã vật phẩm Tẩy Căn Đan (V68). */
    String PILL_CODE = "faction_change_pill";

    /** Khoá túi đồ của nhân vật TRƯỚC mọi khoá khác (DATABASE.md §5.1). */
    void lockInventory(Transaction tx, long characterId);

    /** Hệ hiện tại + số lần đã đổi — khoá dòng nhân vật tới hết transaction. */
    FactionChangeState lockState(Transaction tx, long characterId);

    /** Tiêu một Tẩy Căn Đan; {@code false} = không có viên nào lấy được (chưa ghi gì). */
    boolean takePill(Transaction tx, long characterId);

    /** Ghi hệ mới + tăng bộ đếm + audit. */
    void recordChange(Transaction tx, long characterId, Faction faction, boolean pillUsed);

    /**
     * Bản trong bộ nhớ, không bao giờ có đan — cho composition test chỉ có {@link CharacterPort}. Ghi hệ
     * qua {@link CharacterPort#updateFaction}.
     */
    static FactionChangePort inMemory(CharacterPort characterPort) {
        Map<Long, Integer> changes = new HashMap<>();
        return new FactionChangePort() {
            @Override
            public void lockInventory(Transaction tx, long characterId) {
                // không có túi đồ trong bản này
            }

            @Override
            public synchronized FactionChangeState lockState(Transaction tx, long characterId) {
                Faction current = characterPort
                        .findById(characterId)
                        .map(Character::faction)
                        .orElseThrow(() -> new IllegalStateException("Nhân vật " + characterId + " không tồn tại"));
                return new FactionChangeState(current, changes.getOrDefault(characterId, 0));
            }

            @Override
            public boolean takePill(Transaction tx, long characterId) {
                return false;
            }

            @Override
            public synchronized void recordChange(Transaction tx, long characterId, Faction faction, boolean pillUsed) {
                characterPort.updateFaction(characterId, faction);
                changes.merge(characterId, 1, Integer::sum);
            }
        };
    }
}
