package dev.kage.character.application;

/**
 * Gộp toàn bộ UseCase mà {@link dev.kage.character.api.CharacterFacade} cần — tránh constructor
 * Facade vượt 4 tham số (Checkstyle {@code ParameterNumber}), cùng tinh thần {@code PartyUseCases}/
 * {@code GuildUseCases}.
 */
public record CharacterUseCases(
        CreateCharacterUseCase createCharacterUseCase,
        ListCharactersUseCase listCharactersUseCase,
        SelectCharacterUseCase selectCharacterUseCase,
        SelectFactionUseCase selectFactionUseCase,
        GrantExperienceUseCase grantExperienceUseCase) {}
