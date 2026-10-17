package dev.kage.server.gateway;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;

import dev.kage.character.api.CharacterFacade;
import dev.kage.friend.api.FriendFacade;

/**
 * NHIỀU kết nối {@link EmbeddedChannel} trên CÙNG một gateway — chung sổ tài khoản/nhân vật, chung
 * {@link ConnectedCharacterRegistry}, chung {@link FriendFacade}. {@code GatewayPipelineTest.newPipeline}
 * dựng mọi thứ MỚI cho mỗi kênh, nên không đo được gì xảy ra GIỮA hai kết nối (người này nhắn người
 * kia, ngắt rồi nối lại).
 *
 * <p>Executor chạy đồng bộ và World không {@code start()} — cùng lý do {@code newPipeline}: đọc gói
 * ra không bị race, không rò luồng tick. Ngắt kết nối vẫn dọn trọn (mốc chặn của World chưa start
 * xong ngay), nên nối lại cùng nhân vật được.
 */
final class SharedWorldPipelines {

    private final GatewayFacades facades;
    private final WorldGateway worldGateway;
    private final GatewayObservability observability;

    SharedWorldPipelines() {
        this(GatewayPipelineTest.fakeFriendFacade());
    }

    SharedWorldPipelines(FriendFacade friendFacade) {
        GatewayPipelineTest.FakeCharacterPort characterPort = new GatewayPipelineTest.FakeCharacterPort();
        CharacterFacade characterFacade = LocalGatewayParts.characterFacadeOver(characterPort);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GatewayNetworkMetrics networkMetrics = new GatewayNetworkMetrics(meterRegistry);
        observability = new GatewayObservability(networkMetrics, GatewayTracing.disabled());
        worldGateway = LocalGatewayParts.worldGatewayFor(characterFacade, meterRegistry, networkMetrics);
        characterPort.worldGateway = worldGateway;
        facades = new GatewayFacades(
                LocalGatewayParts.fakeAccountFacade(),
                characterFacade,
                GatewayPipelineTest.fakeInventoryFacade(),
                new ContentFacades(
                        GatewayPipelineTest.fakeShopFacade(),
                        GatewayPipelineTest.fakeDialogueFacade(),
                        GatewayPipelineTest.fakeQuestFacade(),
                        new SocialFacades(
                                friendFacade,
                                GatewayPipelineTest.fakeMailFacade(),
                                GatewayPipelineTest.fakeChatFacade(),
                                GatewayPipelineTest.fakeGuildFacade(),
                                GatewayPipelineTest.fakePartyFacade(),
                                GatewayPipelineTest.fakeTradeFacade(),
                                GatewayPipelineTest.fakePvpFacade())));
    }

    /** Một kết nối mới tới gateway chung — pipeline dựng bằng chính hàm của server. */
    EmbeddedChannel newChannel() {
        GatewayChannelHandler handler = new GatewayChannelHandler(facades, worldGateway, Runnable::run, observability);
        return new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                GatewayServer.addProtocolHandlers(ch.pipeline(), handler);
            }
        });
    }

    /** Dừng các bộ hẹn giờ nền của gateway chung — gọi ở cuối mỗi test. */
    void stop() {
        worldGateway.mainWorldChannels().stopAll();
        worldGateway.registry().stop();
    }
}
