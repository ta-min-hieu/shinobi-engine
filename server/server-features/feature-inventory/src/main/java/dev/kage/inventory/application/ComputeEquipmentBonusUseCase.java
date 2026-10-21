package dev.kage.inventory.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.GemPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemSetPort;
import dev.kage.inventory.domain.ItemSetProgress;
import dev.kage.inventory.domain.ItemSets;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;

/**
 * Tính TỔNG chỉ số cộng thêm từ mọi item đang mặc (GAMEPLAY.md §2/§7) — đọc thuần, không ghi.
 * Gọi lại MỖI lần trang bị đổi VÀ ngay lúc attach nhân vật vào world (server-world không tự biết
 * feature-inventory — {@code GatewayChannelHandler} là nơi nối 2 bên, xem
 * {@code dev.kage.server.world.WorldFacade#applyEquipmentBonus}).
 */
public final class ComputeEquipmentBonusUseCase {

    private final EquipmentPort equipmentPort;
    private final InventoryPort inventoryPort;
    private final ItemTemplatePort itemTemplatePort;
    private final GemPort gemPort;
    private final ItemSetPort itemSetPort;

    public ComputeEquipmentBonusUseCase(
            EquipmentPort equipmentPort, InventoryPort inventoryPort, ItemTemplatePort itemTemplatePort) {
        this(equipmentPort, inventoryPort, itemTemplatePort, GemPort.NONE);
    }

    /** Kèm Linh Ngọc (ADR-064 D4): ngọc cộng PHẲNG vào món đang khảm, không nhân theo cường hoá. */
    public ComputeEquipmentBonusUseCase(
            EquipmentPort equipmentPort,
            InventoryPort inventoryPort,
            ItemTemplatePort itemTemplatePort,
            GemPort gemPort) {
        this.equipmentPort = equipmentPort;
        this.inventoryPort = inventoryPort;
        this.itemTemplatePort = itemTemplatePort;
        this.gemPort = gemPort;
        this.itemSetPort = ItemSetPort.NONE;
    }

    private ComputeEquipmentBonusUseCase(ComputeEquipmentBonusUseCase base, ItemSetPort itemSetPort) {
        this.equipmentPort = base.equipmentPort;
        this.inventoryPort = base.inventoryPort;
        this.itemTemplatePort = base.itemTemplatePort;
        this.gemPort = base.gemPort;
        this.itemSetPort = itemSetPort;
    }

    /** Kèm bộ pháp khí (ADR-065): đủ số món khác nhau của một bộ thì cộng thưởng bậc. */
    public ComputeEquipmentBonusUseCase withItemSets(ItemSetPort port) {
        return new ComputeEquipmentBonusUseCase(this, port);
    }

    public EquipmentBonus execute(long characterId) {
        Equipment equipment = equipmentPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("EquipmentPort phải luôn trả Optional có giá trị"));
        Inventory inventory = inventoryPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));

        int totalBonusAtk = 0;
        int totalBonusDef = 0;
        int totalBonusRes = 0;
        int totalBonusHp = 0;
        Set<Long> wornTemplateIds = new HashSet<>();
        for (long itemId : equipment.slots().values()) {
            ItemStack item = inventory.findItem(itemId).orElse(null);
            if (item == null) {
                continue; // dữ liệu lệch hiếm gặp (item bị xoá khỏi túi nhưng equipment_slot chưa dọn) — bỏ qua an toàn
            }
            ItemTemplate template =
                    itemTemplatePort.findById(item.itemTemplateId()).orElse(null);
            if (template == null) {
                continue;
            }
            wornTemplateIds.add(template.id());
            // ADR-036 D3 — cường hoá khuếch đại chỉ số GỐC của CHÍNH món đó, tính riêng từng món
            // rồi mới cộng lại. Cộng tổng trước rồi mới nhân hệ số là SAI: mỗi món có cấp cường
            // hoá riêng, một món +10 không được kéo theo cả bộ đồ +0.
            int enhanceLevel = item.enhanceLevel();
            totalBonusAtk += EnhancementTable.enhancedBonus(template.bonusAtk(), enhanceLevel);
            totalBonusDef += EnhancementTable.enhancedBonus(template.bonusDef(), enhanceLevel);
            totalBonusRes += EnhancementTable.enhancedBonus(template.bonusRes(), enhanceLevel);
            totalBonusHp += EnhancementTable.enhancedBonus(template.bonusHp(), enhanceLevel);
        }
        EquipmentBonus gems = gemBonusOf(characterId, equipment);
        EquipmentBonus sets = ItemSets.totalBonus(ItemSets.progress(itemSetPort.itemSets(), wornTemplateIds));
        return new EquipmentBonus(
                totalBonusAtk + gems.bonusAtk() + sets.bonusAtk(),
                totalBonusDef + gems.bonusDef() + sets.bonusDef(),
                totalBonusRes + gems.bonusRes() + sets.bonusRes(),
                totalBonusHp + gems.bonusHp() + sets.bonusHp());
    }

    /** Các bộ pháp khí đang mặc ít nhất một món (ADR-065) — cho bảng nhân vật. Đọc thuần. */
    public List<ItemSetProgress> itemSetProgress(long characterId) {
        Equipment equipment = equipmentPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("EquipmentPort phải luôn trả Optional có giá trị"));
        Inventory inventory = inventoryPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
        Set<Long> worn = new HashSet<>();
        for (long itemId : equipment.slots().values()) {
            inventory.findItem(itemId).ifPresent(item -> worn.add(item.itemTemplateId()));
        }
        return ItemSets.progress(itemSetPort.itemSets(), worn);
    }

    /** Tổng chỉ số ngọc trên các món ĐANG MẶC. */
    private EquipmentBonus gemBonusOf(long characterId, Equipment equipment) {
        Map<Long, Long> sockets = gemPort.socketsOf(characterId);
        if (sockets.isEmpty()) {
            return new EquipmentBonus(0, 0, 0, 0);
        }
        Map<Long, EquipmentBonus> bonuses = gemPort.gemBonuses();
        int atk = 0;
        int def = 0;
        int res = 0;
        int hp = 0;
        for (long itemId : equipment.slots().values()) {
            EquipmentBonus gem = bonuses.get(sockets.get(itemId));
            if (gem != null) {
                atk += gem.bonusAtk();
                def += gem.bonusDef();
                res += gem.bonusRes();
                hp += gem.bonusHp();
            }
        }
        return new EquipmentBonus(atk, def, res, hp);
    }
}
