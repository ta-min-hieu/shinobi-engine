package dev.kage.tools.assetpipeline;

/**
 * 3 mức đồ họa client (ART-PIPELINE.md §5). {@code scaleOfMaster} là tỉ lệ so với master @2x
 * Blender render ra (không phải so với @1x) — LOW=@0.5x của @1x = 0.25 của master, MEDIUM/HIGH
 * đều @1x = 0.5 của master. HIGH dùng chung ảnh với MEDIUM: "hiệu ứng" khác nhau giữa 2 tier là
 * runtime code (particle cap, bloom...), không phải độ phân giải ảnh khác nhau — nên pipeline này
 * đóng gói High bằng cách sao chép trực tiếp output của Medium, không render/hạ mẫu riêng.
 */
enum GraphicsTier {
    LOW("low", 0.25),
    MEDIUM("med", 0.5),
    HIGH("high", 0.5);

    final String folderName;
    final double scaleOfMaster;

    GraphicsTier(String folderName, double scaleOfMaster) {
        this.folderName = folderName;
        this.scaleOfMaster = scaleOfMaster;
    }
}
