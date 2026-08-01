package dev.kage.tools.assetpipeline;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.imageio.ImageIO;

/**
 * Hạ mẫu PNG master @2x xuống tier @1x/@0.5x bằng {@code BufferedImage}/{@code Graphics2D} bilinear
 * (ADR-029 — "không cần thư viện resize riêng"). Tỉ lệ tính trực tiếp trên kích thước ảnh nguồn thật
 * (không dựa {@code render-meta.json.master_px}) vì file nguồn CHÍNH LÀ master, luôn khớp nhau.
 */
final class ImageDownscaler {

    private ImageDownscaler() {}

    static void downscaleTo(Path source, Path destination, double scaleOfMaster) throws IOException {
        BufferedImage original = ImageIO.read(source.toFile());
        int targetWidth = Math.max(1, (int) Math.round(original.getWidth() * scaleOfMaster));
        int targetHeight = Math.max(1, (int) Math.round(original.getHeight() * scaleOfMaster));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }

        // destination luôn được tạo bằng stagingDir.resolve(...) ở AtlasStager — có parent thật.
        Files.createDirectories(Objects.requireNonNull(destination.getParent()));
        ImageIO.write(scaled, "png", destination.toFile());
    }
}
