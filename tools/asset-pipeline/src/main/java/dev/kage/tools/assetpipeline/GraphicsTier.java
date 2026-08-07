package dev.kage.tools.assetpipeline;

/**
 * 3 mức đồ họa client (ART-PIPELINE.md §5). {@code scaleOfMaster} là tỉ lệ so với master @2x
 * Blender render ra (không phải so với @1x) — LOW=@0.5x của @1x = 0.25 của master, MEDIUM/HIGH
 * đều @1x = 0.5 của master. HIGH dùng chung ảnh với MEDIUM: "hiệu ứng" khác nhau giữa 2 tier là
 * runtime code (particle cap, bloom...), không phải độ phân giải ảnh khác nhau — nên pipeline này
 * đóng gói High bằng cách sao chép trực tiếp output của Medium, không render/hạ mẫu riêng.
 *
 * <p>{@code maxTriangles} chỉ áp cho <b>nhánh 3D</b> (ADR-031): ngân sách tam giác của model glTF
 * từng tier, lấy nguyên bảng ART-PIPELINE.md §4.4 — số ĐÃ ĐO thật 2026-08-05 bằng
 * {@code tools/blender/measure_triangle_budget.py}, không phải ước lượng. Cố tình đặt cạnh
 * {@code scaleOfMaster} thay vì dựng enum tier thứ hai: 3 mức đồ họa là một khái niệm duy nhất, chỉ
 * khác đơn vị giữa hai nhánh (2D = tỉ lệ ảnh, 3D = số tam giác + cỡ texture).
 */
enum GraphicsTier {
    LOW("low", 0.25, 5_000),
    MEDIUM("med", 0.5, 10_000),
    HIGH("high", 0.5, 20_000);

    final String folderName;
    final double scaleOfMaster;
    final int maxTriangles;

    GraphicsTier(String folderName, double scaleOfMaster, int maxTriangles) {
        this.folderName = folderName;
        this.scaleOfMaster = scaleOfMaster;
        this.maxTriangles = maxTriangles;
    }
}
