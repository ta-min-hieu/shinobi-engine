package dev.kage.mail.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MailAttachmentTest {

    @Test
    void shouldRejectNonPositiveItemTemplateId() {
        assertThatThrownBy(() -> new MailAttachment(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        assertThatThrownBy(() -> new MailAttachment(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
