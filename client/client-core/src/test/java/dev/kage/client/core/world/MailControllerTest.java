package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ClaimMailOutcome;
import dev.kage.protocol.DeleteMailOutcome;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.MailAttachmentView;
import dev.kage.protocol.MailView;
import dev.kage.protocol.SendMailOutcome;

/**
 * {@link MailController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code GuildControllerTest}.
 */
class MailControllerTest {

    private final MailController controller = new MailController();

    @Test
    @DisplayName("Hòm thư bắt đầu rỗng, chưa chọn lá nào, chưa có thư mới")
    void startsEmpty() {
        assertThat(controller.mails()).isEmpty();
        assertThat(controller.selected()).isEmpty();
        assertThat(controller.hasNewMail()).isFalse();
        assertThat(controller.pendingClaimMailId()).isZero();
    }

    @Test
    @DisplayName("ListMailResponse là nguồn DUY NHẤT của danh sách thư, giữ đủ 8 trường MailView")
    void adoptsServerMailList() {
        controller.onMailList(list(mail(7L, "Thư của bạn", "Chào bạn nhé", 250L)));

        assertThat(controller.mails())
                .containsExactly(
                        new MailController.Mail(7L, true, 42L, "Thư của bạn", "Chào bạn nhé", 250L, List.of(), false));
    }

    @Test
    @DisplayName("has_sender=false là thư hệ thống — sender_id lúc đó KHÔNG có nghĩa")
    void keepsSystemMailFlag() {
        MailView system = MailView.newBuilder()
                .setMailId(3L)
                .setHasSender(false)
                .setSubject("Đền bù bảo trì")
                .setBody("Xin lỗi vì sự cố.")
                .setGoldAmount(1000L)
                .build();

        controller.onMailList(ListMailResponse.newBuilder().addMails(system).build());

        assertThat(controller.mails()).singleElement().satisfies(mail -> {
            assertThat(mail.hasSender()).isFalse();
            assertThat(mail.senderId()).isZero();
        });
    }

    @Test
    @DisplayName("Đính kèm giữ nguyên item_template_id + số lượng, KHÔNG bịa tên (Khoảng trống 2, ADR-035)")
    void keepsAttachmentsAsRawNumbers() {
        MailView withItems = MailView.newBuilder()
                .setMailId(9L)
                .setHasSender(true)
                .setSenderId(1L)
                .setSubject("Quà")
                .setBody("")
                .addAttachments(attachment(12L, 3))
                .addAttachments(attachment(88L, 1))
                .build();

        controller.onMailList(ListMailResponse.newBuilder().addMails(withItems).build());

        assertThat(controller.mails().get(0).attachments())
                .containsExactly(new MailController.Attachment(12L, 3), new MailController.Attachment(88L, 1));
    }

    @Test
    @DisplayName("Danh sách THAY hoàn toàn, không trộn với bản cũ")
    void replacesMailsInsteadOfMerging() {
        controller.onMailList(list(mail(1L, "A", "", 0L), mail(2L, "B", "", 0L)));
        controller.onMailList(list(mail(2L, "B", "", 0L)));

        assertThat(controller.mails()).extracting(MailController.Mail::mailId).containsExactly(2L);
    }

    @Test
    @DisplayName("Chữ của người khác đi qua sanitize — glyph font UI không vẽ được thành '?'")
    void sanitizesTextWrittenByOtherPlayers() {
        // 日 nằm ngoài FontFactory.VIETNAMESE_CHARACTERS: không sanitize thì font im lặng nuốt mất và
        // người nhận đọc được một tiêu đề cụt mà không hiểu vì sao.
        controller.onMailList(list(mail(1L, "Chào 日 bạn", "Nội dung 日", 0L)));

        MailController.Mail mail = controller.mails().get(0);
        assertThat(mail.subject()).isEqualTo("Chào ? bạn");
        assertThat(mail.body()).isEqualTo("Nội dung ?");
    }

    @Test
    @DisplayName("Chọn thư: id có thật thì mở đọc, id lạ thì KHÔNG đổi gì")
    void selectsOnlyExistingMail() {
        controller.onMailList(list(mail(5L, "A", "thân bài", 0L)));

        controller.select(999L);
        assertThat(controller.selected()).isEmpty();

        controller.select(5L);
        assertThat(controller.selected()).map(MailController.Mail::mailId).contains(5L);
    }

    @Test
    @DisplayName("Lá đang đọc biến mất khỏi danh sách mới thì BỎ chọn, không treo nội dung cũ")
    void dropsSelectionWhenMailDisappears() {
        controller.onMailList(list(mail(5L, "A", "", 0L)));
        controller.select(5L);

        // Xoá xong hỏi lại — server không còn trả lá đó nữa.
        controller.onMailList(ListMailResponse.getDefaultInstance());

        assertThat(controller.selected()).isEmpty();
    }

    @Test
    @DisplayName("NewMailEvent bật cờ; mở hòm thư ra xem thì tắt")
    void tracksNewMailFlag() {
        controller.onNewMail();
        assertThat(controller.hasNewMail()).isTrue();

        controller.clearNewMailFlag();
        assertThat(controller.hasNewMail()).isFalse();
    }

    // ---------------------------------------------------------------------------------------------
    // ADR-025 — khoá idempotency của lệnh nhận thưởng.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ADR-025: bấm Nhận lại trên CÙNG lá thư khi lệnh chưa xong giữ NGUYÊN khoá")
    void reusesClaimKeyWhileTheSameClaimIsPending() {
        String first = controller.beginClaim(7L);
        String second = controller.beginClaim(7L);

        assertThat(second).isEqualTo(first);
        assertThat(controller.pendingClaimMailId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("ADR-025: khoá là UUID v4 hợp lệ (server đối chiếu unique ở tầng DB)")
    void claimKeyIsAUuid() {
        assertThat(java.util.UUID.fromString(controller.beginClaim(7L))).isNotNull();
    }

    @Test
    @DisplayName("ADR-025: lá thư KHÁC là một ý định khác — phải có khoá khác")
    void newKeyForADifferentMail() {
        String first = controller.beginClaim(7L);
        String second = controller.beginClaim(8L);

        assertThat(second).isNotEqualTo(first);
        assertThat(controller.pendingClaimMailId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("ADR-025: lệnh đã có kết luận thì lần bấm sau là ý định MỚI, khoá mới")
    void newKeyAfterTheClaimSettled() {
        String first = controller.beginClaim(7L);
        controller.onClaimSettled();
        assertThat(controller.pendingClaimMailId()).isZero();

        assertThat(controller.beginClaim(7L)).isNotEqualTo(first);
    }

    // ---------------------------------------------------------------------------------------------
    // Soạn thư.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Bốn ô đều rỗng: KHÔNG gửi và cũng KHÔNG mắng người chơi")
    void emptyFormSendsNothingQuietly() {
        assertThat(controller.compose("", "", "", "")).isEqualTo(MailController.ComposeResult.Nothing.INSTANCE);
    }

    @Test
    @DisplayName("Thư hợp lệ: cắt khoảng trắng thừa, ô vàng rỗng nghĩa là 0 vàng")
    void composesValidMail() {
        MailController.ComposeResult result = controller.compose("  Kage  ", "  Chào  ", "Nội dung", "");

        assertThat(result)
                .isEqualTo(new MailController.ComposeResult.Send(
                        new MailController.Draft("Kage", "Chào", "Nội dung", 0L)));
    }

    @Test
    @DisplayName("Tên người nhận ngắn/dài quá bị chặn ở client")
    void rejectsBadRecipientName() {
        assertThat(reject(controller.compose("K", "Chào", "", ""))).isEqualTo("mail.error.bad_recipient");
        assertThat(reject(controller.compose("K".repeat(17), "Chào", "", ""))).isEqualTo("mail.error.bad_recipient");
    }

    @Test
    @DisplayName("Tiêu đề rỗng bị chặn — cột mail.subject là NOT NULL và server không kiểm")
    void rejectsEmptySubject() {
        assertThat(reject(controller.compose("Kage", "   ", "Nội dung", ""))).isEqualTo("mail.error.empty_subject");
    }

    @Test
    @DisplayName("Tiêu đề > 64 và nội dung > 1024 bị chặn — vượt cột VARCHAR của V13 là SQLException")
    void rejectsOversizedText() {
        assertThat(reject(controller.compose("Kage", "s".repeat(MailController.MAX_SUBJECT_LENGTH + 1), "", "")))
                .isEqualTo("mail.error.subject_too_long");
        assertThat(reject(controller.compose("Kage", "Chào", "b".repeat(MailController.MAX_BODY_LENGTH + 1), "")))
                .isEqualTo("mail.error.body_too_long");
    }

    @Test
    @DisplayName("Ô vàng: số hợp lệ đi qua, chữ và số âm bị chặn (CHECK gold_amount >= 0 ở V13)")
    void parsesGold() {
        assertThat(controller.compose("Kage", "Chào", "", "1500"))
                .isEqualTo(new MailController.ComposeResult.Send(new MailController.Draft("Kage", "Chào", "", 1500L)));
        assertThat(reject(controller.compose("Kage", "Chào", "", "nhiều"))).isEqualTo("mail.error.bad_gold");
        assertThat(reject(controller.compose("Kage", "Chào", "", "-1"))).isEqualTo("mail.error.bad_gold");
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n.
    // ---------------------------------------------------------------------------------------------

    /**
     * Quét HẾT ba enum thay vì liệt kê tay từng giá trị: thêm một outcome mới vào mail.proto mà quên
     * thêm khoá i18n sẽ làm test này đỏ ngay, thay vì lặng lẽ hiện "mail.error.unknown" cho người
     * chơi. {@code UNSPECIFIED}/{@code UNRECOGNIZED} CỐ TÌNH vắng mặt khỏi bảng tra (xem javadoc
     * {@link MailController}) nên bị loại khỏi phép quét.
     */
    @Test
    @DisplayName("Mọi outcome THẬT của ba enum mail.proto đều có khoá i18n riêng")
    void mapsEveryRealOutcome() {
        for (SendMailOutcome outcome : SendMailOutcome.values()) {
            if (isRealOutcome(outcome.name())) {
                assertThat(MailController.sendMessageKey(outcome))
                        .as("SendMailOutcome.%s chưa có khoá i18n", outcome)
                        .hasValueSatisfying(key -> assertThat(key).startsWith("mail."));
            }
        }
        for (ClaimMailOutcome outcome : ClaimMailOutcome.values()) {
            if (isRealOutcome(outcome.name())) {
                assertThat(MailController.claimMessageKey(outcome))
                        .as("ClaimMailOutcome.%s chưa có khoá i18n", outcome)
                        .hasValueSatisfying(key -> assertThat(key).startsWith("mail."));
            }
        }
        for (DeleteMailOutcome outcome : DeleteMailOutcome.values()) {
            if (isRealOutcome(outcome.name())) {
                assertThat(MailController.deleteMessageKey(outcome))
                        .as("DeleteMailOutcome.%s chưa có khoá i18n", outcome)
                        .hasValueSatisfying(key -> assertThat(key).startsWith("mail."));
            }
        }
    }

    private static boolean isRealOutcome(String name) {
        return !name.endsWith("UNSPECIFIED") && !"UNRECOGNIZED".equals(name);
    }

    @Test
    @DisplayName("UNSPECIFIED/UNRECOGNIZED không có khoá — chỗ gọi dịch thành 'không hiểu' thay vì đoán")
    void leavesUnknownOutcomesUnmapped() {
        assertThat(MailController.sendMessageKey(SendMailOutcome.SEND_MAIL_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(MailController.sendMessageKey(SendMailOutcome.UNRECOGNIZED)).isEmpty();
        assertThat(MailController.claimMessageKey(ClaimMailOutcome.CLAIM_MAIL_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(MailController.deleteMessageKey(DeleteMailOutcome.DELETE_MAIL_OUTCOME_UNSPECIFIED))
                .isEmpty();
    }

    @Test
    @DisplayName("clear() dọn sạch: rời màn hình rồi vào lại không thấy hòm thư của phiên trước")
    void clearsEverything() {
        controller.onMailList(list(mail(5L, "A", "", 0L)));
        controller.select(5L);
        controller.onNewMail();
        controller.beginClaim(5L);

        controller.clear();

        assertThat(controller.mails()).isEmpty();
        assertThat(controller.selected()).isEmpty();
        assertThat(controller.hasNewMail()).isFalse();
        assertThat(controller.pendingClaimMailId()).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Bó buộc giữa hai bundle i18n và font UI.
    //
    // FontFactoryVietnameseGlyphTest chứng minh FONT có glyph cho mọi ký tự trong
    // FontFactory.VIETNAMESE_CHARACTERS; nó KHÔNG hề đọc file .properties. Hai test dưới đây đóng
    // đúng nửa còn lại của vòng: chuỗi thật sự hiện lên màn hình có nằm trong tập glyph đó không.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Mọi chuỗi tiếng Việt của hòm thư nằm trong tập glyph font UI — không bị nuốt chữ")
    void everyVietnameseMailStringIsRenderable() {
        java.util.Properties vietnamese = bundle("strings_vi.properties");
        for (String key : mailKeys(vietnamese)) {
            String value = vietnamese.getProperty(key);
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                assertThat(dev.kage.client.core.render.FontFactory.canRender(character))
                        .as(
                                "%s chứa ký tự '%s' (U+%s) mà font UI KHÔNG vẽ được — chữ sẽ biến mất"
                                        + " lặng lẽ trên màn hình",
                                key, character, Integer.toHexString(character).toUpperCase(java.util.Locale.ROOT))
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("Hai bundle có ĐÚNG cùng tập khoá mail.* — thiếu một bên là hiện thẳng khoá ra màn hình")
    void bothBundlesDeclareTheSameMailKeys() {
        assertThat(mailKeys(bundle("strings_vi.properties"))).isEqualTo(mailKeys(bundle("strings_en.properties")));
    }

    private static java.util.SortedSet<String> mailKeys(java.util.Properties properties) {
        java.util.SortedSet<String> keys = new java.util.TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("mail.")) {
                keys.add(key);
            }
        }
        assertThat(keys)
                .as("bundle không có khoá mail.* nào — đường dẫn resource sai?")
                .isNotEmpty();
        return keys;
    }

    /** Đọc bundle bằng UTF-8 tường minh: {@code Properties.load(InputStream)} mặc định ISO-8859-1. */
    private static java.util.Properties bundle(String fileName) {
        java.util.Properties properties = new java.util.Properties();
        java.io.InputStream stream = MailControllerTest.class.getResourceAsStream("/i18n/" + fileName);
        assertThat(stream)
                .as("không tìm thấy /i18n/%s trên classpath", fileName)
                .isNotNull();
        // Reader trong try-with-resources chứ không phải InputStream: đóng Reader đóng luôn stream
        // bên dưới, còn đóng mỗi stream thì SpotBugs (OS_OPEN_STREAM) chỉ ra đúng rằng Reader bọc
        // ngoài không bao giờ được đóng.
        try (java.io.Reader reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return properties;
    }

    // --- dựng dữ liệu --------------------------------------------------------------------------

    private static String reject(MailController.ComposeResult result) {
        assertThat(result).isInstanceOf(MailController.ComposeResult.Reject.class);
        return ((MailController.ComposeResult.Reject) result).messageKey();
    }

    private static ListMailResponse list(MailView... mails) {
        ListMailResponse.Builder builder = ListMailResponse.newBuilder();
        for (MailView mail : mails) {
            builder.addMails(mail);
        }
        return builder.build();
    }

    /**
     * Một lá thư CHƯA nhận thưởng, người gửi là nhân vật 42.
     *
     * <p>Không có tham số {@code claimed}: mọi test ở trên đều cần thư chưa nhận, và thêm nó vào là
     * đẩy hàm này lên 5 tham số — đúng ngưỡng {@code ExcessiveParameterList} của PMD (config/pmd,
     * minimum=5). Test nào cần thư đã nhận thì dựng {@link MailView} tại chỗ, như
     * {@code keepsSystemMailFlag} đang làm.
     */
    private static MailView mail(long mailId, String subject, String body, long gold) {
        return MailView.newBuilder()
                .setMailId(mailId)
                .setHasSender(true)
                .setSenderId(42L)
                .setSubject(subject)
                .setBody(body)
                .setGoldAmount(gold)
                .setClaimed(false)
                .build();
    }

    private static MailAttachmentView attachment(long itemTemplateId, int quantity) {
        return MailAttachmentView.newBuilder()
                .setItemTemplateId(itemTemplateId)
                .setQuantity(quantity)
                .build();
    }
}
