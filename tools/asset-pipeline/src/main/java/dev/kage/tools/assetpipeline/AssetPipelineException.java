package dev.kage.tools.assetpipeline;

/** Lỗi làm build fail (ART-PIPELINE.md §8.5 "fail build nếu..."). Không phải lỗi lập trình. */
final class AssetPipelineException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    AssetPipelineException(String message) {
        super(message);
    }
}
