package dev.kage.tools.assetpipeline;

import java.nio.file.Path;

/**
 * Một thư mục trong {@code assets/raw/render-out/<asset_key>/} đã quét được.
 *
 * <p>{@code renderMeta} null nghĩa là asset tĩnh (icon, ART-PIPELINE.md §4.1 "Icon" không có action)
 * — thư mục chỉ chứa đúng 1 file {@code icon.png}, không có {@code render-meta.json}.
 */
record AssetFolder(String assetKey, Path folder, RenderMeta renderMeta) {

    boolean isStaticIcon() {
        return renderMeta == null;
    }
}
