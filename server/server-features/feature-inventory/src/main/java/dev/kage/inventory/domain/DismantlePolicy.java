package dev.kage.inventory.domain;

import java.util.EnumSet;
import java.util.Set;

/** Phân giải pháp khí (ADR-097) — món nào được, thu về bao nhiêu. Hàm thuần; số là PLACEHOLDER cân bằng. */
public final class DismantlePolicy {

    /** Bảy ô chiến đấu — phi kiếm (MOUNT) và linh thú (PET) không phân giải. */
    public static final Set<EquipmentSlotType> DISMANTLABLE_SLOTS = EnumSet.of(
            EquipmentSlotType.WEAPON,
            EquipmentSlotType.ARMOR,
            EquipmentSlotType.HELMET,
            EquipmentSlotType.GLOVE,
            EquipmentSlotType.BOOT,
            EquipmentSlotType.ACCESSORY_1,
            EquipmentSlotType.ACCESSORY_2);

    /** Mã vật phẩm Linh Thảo thu về. */
    public static final String HERB_CODE = "spirit_herb";

    private static final long GOLD_PER_REQUIRED_LEVEL = 20L;
    private static final long GOLD_PER_ENHANCE_LEVEL = 100L;
    private static final int ENHANCE_LEVELS_PER_EXTRA_HERB = 3;

    private DismantlePolicy() {}

    /** Linh thạch thu về. */
    public static long gold(int requiredLevel, int enhanceLevel) {
        return GOLD_PER_REQUIRED_LEVEL * requiredLevel + GOLD_PER_ENHANCE_LEVEL * enhanceLevel;
    }

    /** Linh Thảo thu về. */
    public static int herbs(int enhanceLevel) {
        return 1 + enhanceLevel / ENHANCE_LEVELS_PER_EXTRA_HERB;
    }
}
