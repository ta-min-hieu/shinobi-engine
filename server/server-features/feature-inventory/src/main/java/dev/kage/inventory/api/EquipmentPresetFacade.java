package dev.kage.inventory.api;

import java.util.Map;

import dev.kage.inventory.domain.EquipmentPresetPort;
import dev.kage.inventory.domain.EquipmentSlotType;

/**
 * Lưu bộ trang bị (ADR-175) — tối đa {@link #PRESET_COUNT} bộ mỗi nhân vật. Mặc bộ đi qua đúng đường mặc đồ thường
 * ({@link InventoryFacade#equipItem}), nên luật cấp/ô/sở hữu không có ngoại lệ. Chạm DB — chỉ gọi trên Virtual Thread.
 */
public final class EquipmentPresetFacade {

    /** Số bộ tối đa (số bộ hợp lệ: 1..PRESET_COUNT). */
    public static final int PRESET_COUNT = 3;

    /** Composition không có bộ trang bị (test): không lưu gì. */
    public static final EquipmentPresetFacade DISABLED = new EquipmentPresetFacade(new EquipmentPresetPort() {
        @Override
        public Map<Integer, Map<EquipmentSlotType, Long>> presetsOf(long characterId) {
            return Map.of();
        }

        @Override
        public void replace(long characterId, int presetNo, Map<EquipmentSlotType, Long> slots) {
            // không lưu
        }
    });

    private final EquipmentPresetPort port;

    /** Dựng ở composition root. */
    public EquipmentPresetFacade(EquipmentPresetPort port) {
        this.port = port;
    }

    /** Số bộ có nằm trong 1..{@link #PRESET_COUNT} không. */
    public static boolean isValidPreset(int presetNo) {
        return presetNo >= 1 && presetNo <= PRESET_COUNT;
    }

    /** Mọi bộ đã lưu. */
    public Map<Integer, Map<EquipmentSlotType, Long>> presetsOf(long characterId) {
        return port.presetsOf(characterId);
    }

    /** Ghi đè bộ {@code presetNo} (đã kiểm hợp lệ) bằng các món đang mặc. */
    public void save(long characterId, int presetNo, Map<EquipmentSlotType, Long> equipped) {
        port.replace(characterId, presetNo, Map.copyOf(equipped));
    }
}
