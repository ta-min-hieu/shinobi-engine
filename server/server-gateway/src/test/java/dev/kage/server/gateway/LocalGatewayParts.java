package dev.kage.server.gateway;

import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CharacterUseCases;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.application.SelectFactionUseCase;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Mảnh dựng chung cho các test gateway chạy trên {@code LocalChannel} (đổi phiên, đổi nhân vật, thứ tự
 * báo online) — ba lớp test đó từng chép y hệt cùng một khối setup (README dòng 58(b), 2026-10-17).
 * Mọi Port là fake trong bộ nhớ; World chính không tick (test tự đẩy sự kiện vào registry).
 */
final class LocalGatewayParts {

    private LocalGatewayParts() {}

    /** {@code CharacterFacade} thật trên Port fake {@code port}. */
    static CharacterFacade characterFacadeOver(GatewayPipelineTest.FakeCharacterPort port) {
        return new CharacterFacade(
                new CharacterUseCases(
                        new CreateCharacterUseCase(port, action -> action.accept(null)),
                        new ListCharactersUseCase(port),
                        new SelectCharacterUseCase(port),
                        new SelectFactionUseCase(port),
                        new GrantExperienceUseCase(port)),
                port);
    }

    /** {@code AccountFacade} thật trên Port fake — đăng nhập tự tạo tài khoản. */
    static AccountFacade fakeAccountFacade() {
        return new AccountFacade(new LoginOrCreateAccountUseCase(
                new GatewayPipelineTest.FakeAccountPort(),
                new GatewayPipelineTest.FakeSessionPort(),
                action -> action.accept(null)));
    }

    /** World chính (không start) + hầm ngục + đấu trường, nối vào một registry mới. */
    static WorldGateway worldGatewayFor(
            CharacterFacade characterFacade, SimpleMeterRegistry meterRegistry, GatewayNetworkMetrics networkMetrics) {
        ConnectedCharacterRegistry registry = new ConnectedCharacterRegistry(
                GatewayPipelineTest.fakeInventoryFacade(),
                GatewayPipelineTest.fakeQuestFacade(),
                characterFacade::grantExperience,
                networkMetrics);
        MainWorldChannelRegistry mainWorldChannels = new MainWorldChannelRegistry(
                registry, new WorldFacade(registry), meterRegistry, WorldContentCatalog.EMPTY);
        return new WorldGateway(
                mainWorldChannels,
                registry,
                new CharacterPositionPersister(characterFacade, registry, Duration.ofMinutes(5)),
                new DungeonInstanceRegistry(
                        registry,
                        new DungeonInstanceRegistry.DungeonFacades(
                                characterFacade, GatewayPipelineTest.fakePartyFacade()),
                        mainWorldChannels,
                        TestWorldContents.withBoss()),
                new ArenaInstanceRegistry(
                        registry, characterFacade, GatewayPipelineTest.fakePvpFacade(), mainWorldChannels));
    }
}
