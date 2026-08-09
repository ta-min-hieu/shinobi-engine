package dev.kage.client.core.world;

/**
 * 3 mức đồ hoạ client phía nhánh 3D ([ADR-028](docs/01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md)),
 * dùng để chọn thư mục model glTF dưới {@code assets/exported/models/<tier>/}
 * ({@code tools/blender/export_character_lods.py} xuất cả 3 tier cùng lúc).
 *
 * <p><b>Không</b> phải cùng kiểu với {@code dev.kage.tools.assetpipeline.GraphicsTier} (module
 * {@code tools:asset-pipeline}, dùng cho pipeline art lúc build) — {@code client-core} không phụ
 * thuộc {@code tools:asset-pipeline} (xem {@code client-core/build.gradle.kts}) nên đây là enum
 * RIÊNG, chỉ chứa đúng phần {@code folderName} mà {@link WorldModelRenderer} cần để đọc file,
 * không kéo theo {@code scaleOfMaster}/{@code maxTriangles} (thứ chỉ pipeline lúc build quan tâm).
 *
 * <p>Cố tình CHƯA có gì để đổi tier lúc chạy (không UI, không Preferences, không tải-theo-mạng) —
 * đó là {@code ensureAssetsAvailable(tier)} mà ADR-028 mô tả, CHƯA code ở bất kỳ đâu trong repo kể
 * cả nhánh sprite 2D ([ART-PIPELINE.md §8.4](docs/14-art/ART-PIPELINE.md) mới là thiết kế). Enum
 * này chỉ dọn chỗ hardcode {@code "models/med/"} cũ, không phải xây tính năng đổi tier.
 */
public enum GraphicsTier {
    LOW("low"),
    MEDIUM("med"),
    HIGH("high");

    /** Tên thư mục con dưới {@code assets/exported/models/}, khớp {@code export_character_lods.py}. */
    final String folderName;

    GraphicsTier(String folderName) {
        this.folderName = folderName;
    }
}
