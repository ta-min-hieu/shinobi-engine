package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemUseEffect;

/** Lô 49 phía client — thu phóng camera (ADR-323), lọc túi (ADR-324), tên cảnh giới (ADR-322). */
class Lot49ClientTest {

    @Test
    void cameraZoomStepsAndClamps() {
        assertThat(CameraZoomCoordinator.next(1f, 1f)).isEqualTo(1.1f);
        assertThat(CameraZoomCoordinator.next(1f, -1f)).isEqualTo(0.9f);
        assertThat(CameraZoomCoordinator.next(CameraZoomCoordinator.MAX_ZOOM, 1f))
                .isEqualTo(CameraZoomCoordinator.MAX_ZOOM);
        assertThat(CameraZoomCoordinator.next(CameraZoomCoordinator.MIN_ZOOM, -1f))
                .isEqualTo(CameraZoomCoordinator.MIN_ZOOM);
    }

    @Test
    void bagFilterClassifiesByCatalog() {
        ItemCatalogEntry sword = ItemCatalogEntry.newBuilder()
                .setEquipSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                .build();
        ItemCatalogEntry pill = ItemCatalogEntry.newBuilder()
                .setUseEffect(ItemUseEffect.ITEM_USE_EFFECT_HEAL_HP)
                .build();
        assertThat(BagFilterCoordinator.kindOf(Optional.of(sword))).isEqualTo(BagFilterCoordinator.Kind.EQUIPMENT);
        assertThat(BagFilterCoordinator.kindOf(Optional.of(pill))).isEqualTo(BagFilterCoordinator.Kind.USABLE);
        assertThat(BagFilterCoordinator.kindOf(Optional.of(ItemCatalogEntry.getDefaultInstance())))
                .isEqualTo(BagFilterCoordinator.Kind.OTHER);
        assertThat(BagFilterCoordinator.kindOf(Optional.empty())).isEqualTo(BagFilterCoordinator.Kind.OTHER);
        assertThat(BagFilterCoordinator.Kind.parse("Trang bị")).contains(BagFilterCoordinator.Kind.EQUIPMENT);
        assertThat(BagFilterCoordinator.Kind.parse("đan")).contains(BagFilterCoordinator.Kind.USABLE);
        assertThat(BagFilterCoordinator.Kind.parse("abc")).isEmpty();
    }

    @Test
    void realmKeysClamp() {
        assertThat(BreakthroughHistoryCoordinator.realmKey(0)).isEqualTo(OnlineCountCoordinator.REALM_KEYS.getFirst());
        assertThat(BreakthroughHistoryCoordinator.realmKey(99)).isEqualTo(OnlineCountCoordinator.REALM_KEYS.getLast());
    }
}
