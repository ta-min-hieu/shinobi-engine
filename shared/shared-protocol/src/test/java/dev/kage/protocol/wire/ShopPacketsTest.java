package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.ListShopItemsRequest;
import dev.kage.protocol.ListShopItemsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellItemCommand;
import dev.kage.protocol.SellItemResponse;
import dev.kage.protocol.SellOutcome;
import dev.kage.protocol.ShopListingView;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code PacketCodec}
 * + protobuf thật cho packet Shop (1000-1099, NETWORK.md §4).
 */
class ShopPacketsTest {

    @Test
    void shouldRoundTripListShopItemsRequest() throws InvalidProtocolBufferException {
        ListShopItemsRequest request = ListShopItemsRequest.newBuilder().build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_LIST_SHOP_ITEMS_REQUEST_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, request.toByteArray());
        ListShopItemsRequest parsed = ListShopItemsRequest.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed).isEqualTo(request);
    }

    @Test
    void shouldRoundTripListShopItemsResponse() throws InvalidProtocolBufferException {
        ShopListingView listing =
                ShopListingView.newBuilder().setItemTemplateId(1L).setBuyPrice(5L).setSellPrice(2L).build();
        ListShopItemsResponse response = ListShopItemsResponse.newBuilder().addListings(listing).build();
        PacketHeader header =
                new PacketHeader(Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE, ProtocolVersion.CURRENT, 1);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        ListShopItemsResponse parsed = ListShopItemsResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getListingsList()).hasSize(1);
        assertThat(parsed.getListings(0).getItemTemplateId()).isEqualTo(1L);
        assertThat(parsed.getListings(0).getBuyPrice()).isEqualTo(5L);
        assertThat(parsed.getListings(0).getSellPrice()).isEqualTo(2L);
    }

    @Test
    void shouldRoundTripBuyItemCommand() throws InvalidProtocolBufferException {
        BuyItemCommand command = BuyItemCommand.newBuilder()
                .setItemTemplateId(1L)
                .setQuantity(3)
                .setIdempotencyKey("11111111-1111-1111-1111-111111111111")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        BuyItemCommand parsed = BuyItemCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getItemTemplateId()).isEqualTo(1L);
        assertThat(parsed.getQuantity()).isEqualTo(3);
        assertThat(parsed.getIdempotencyKey()).isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void shouldRoundTripBuyItemResponse() throws InvalidProtocolBufferException {
        BuyItemResponse response =
                BuyItemResponse.newBuilder().setOutcome(BuyOutcome.BUY_OUTCOME_INSUFFICIENT_GOLD).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE, ProtocolVersion.CURRENT, 2);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        BuyItemResponse parsed = BuyItemResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(BuyOutcome.BUY_OUTCOME_INSUFFICIENT_GOLD);
    }

    @Test
    void shouldRoundTripSellItemCommand() throws InvalidProtocolBufferException {
        SellItemCommand command = SellItemCommand.newBuilder()
                .setItemId(42L)
                .setQuantity(2)
                .setIdempotencyKey("22222222-2222-2222-2222-222222222222")
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        SellItemCommand parsed = SellItemCommand.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getItemId()).isEqualTo(42L);
        assertThat(parsed.getQuantity()).isEqualTo(2);
        assertThat(parsed.getIdempotencyKey()).isEqualTo("22222222-2222-2222-2222-222222222222");
    }

    @Test
    void shouldRoundTripSellItemResponse() throws InvalidProtocolBufferException {
        SellItemResponse response = SellItemResponse.newBuilder().setOutcome(SellOutcome.SELL_OUTCOME_OK).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, response.toByteArray());
        SellItemResponse parsed = SellItemResponse.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getOutcome()).isEqualTo(SellOutcome.SELL_OUTCOME_OK);
    }
}
