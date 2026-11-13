package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 49 — ghi chú bang (ADR-319), quà giới thiệu (ADR-320), báo cường hoá cao (ADR-321). */
class Lot49ServerTest {

    @Test
    void validatesMemberNote() {
        assertThat(Lot49Commands.validNote("Đánh boss giỏi")).isTrue();
        assertThat(Lot49Commands.validNote("a".repeat(101))).isFalse();
        assertThat(Lot49Commands.validNote("x\u0007y")).isFalse();
    }

    @Test
    void referralKeysDifferPerRecipient() {
        assertThat(Lot49Commands.referralKey(5, 5)).isNotEqualTo(Lot49Commands.referralKey(5, 9));
        assertThat(Lot49Commands.referralKey(5, 9)).isEqualTo(Lot49Commands.referralKey(5, 9));
    }

    @Test
    void enhanceNoticeCarriesLinkedItem() throws InvalidProtocolBufferException {
        ItemStack item = new ItemStack(77L, 42L, 1, 11);
        ChatMessageEvent event = ChatMessageEvent.parseFrom(Lot49Commands.enhanceNotice("ĐạoHữu01", item));
        assertThat(event.getBody())
                .contains("ĐạoHữu01 cường hoá thành công lên +11")
                .contains("+11!");
        assertThat(event.getLinkedItem().getItemTemplateId()).isEqualTo(42L);
        assertThat(event.getLinkedItem().getEnhanceLevel()).isEqualTo(11);
    }
}
