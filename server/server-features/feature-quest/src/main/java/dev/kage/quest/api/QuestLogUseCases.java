package dev.kage.quest.api;

import dev.kage.quest.application.AbandonQuestUseCase;
import dev.kage.quest.application.ListQuestsUseCase;

/**
 * Hai use case của NHẬT KÝ nhiệm vụ — đọc (ADR-035) và bỏ (ADR-054) — gom lại để {@link QuestFacade}
 * giữ constructor ở 4 tham số (CODING-STANDARDS {@code ParameterNumber} max=4), cùng cách
 * {@code EnhancePorts}/{@code SocialFacades}.
 */
public record QuestLogUseCases(ListQuestsUseCase list, AbandonQuestUseCase abandon) {}
