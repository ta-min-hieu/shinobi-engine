package dev.kage.world.domain;

import java.util.Map;

/** Pháp bảo (ADR-127, V107) — bảng {@code artifact_skill}; hiện thực ở server-database, đọc một lần. */
@FunctionalInterface
public interface ArtifactPort {

    /**
     * Chiêu chủ động của một mẫu pháp bảo.
     *
     * @param effect {@code SHIELD} | {@code HEAL} | {@code SPEED}
     */
    record ArtifactSkill(
            long itemTemplateId, String effect, int magnitudePercent, int durationSeconds, int cooldownSeconds) {}

    /** Mọi pháp bảo theo {@code item_template.id}. */
    Map<Long, ArtifactSkill> skills();
}
