package dev.kage.tools.assetpipeline;

import java.nio.file.Path;

/**
 * Một file model glTF đã quét được ở {@code assets/exported/models/{low,med,high}/<asset_key>.glb}
 * (ADR-031, ART-PIPELINE.md §7) — bản đối xứng của {@link AssetFolder} cho nhánh 3D.
 *
 * <p>Khác {@link AssetFolder} ở chỗ nhánh 3D là <b>một file cho một tier</b> chứ không phải một thư
 * mục frame dùng chung cho mọi tier: cùng {@code assetKey} xuất hiện 3 lần, mỗi lần một
 * {@link GraphicsTier}, vì ba tier là ba bản decimate khác nhau chứ không phải ba lần hạ mẫu cùng
 * một ảnh.
 *
 * <p>{@code triangleCount} là số ĐO ĐƯỢC từ chính file (xem {@link GlbTriangleCounter}), không phải
 * số chép lại từ sidecar {@code model-meta.json} — sidecar do Blender ghi lúc xuất, còn cái cần gác
 * là file thật sẽ ship cho người chơi.
 */
record ScannedModel(String assetKey, GraphicsTier tier, Path file, int triangleCount) {}
