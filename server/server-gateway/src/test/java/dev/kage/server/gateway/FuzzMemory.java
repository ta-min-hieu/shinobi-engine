package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Id và chuỗi THẬT mà server vừa trả về (id phiên giao dịch, id thư, id lời mời, node hội thoại, tên
 * nhân vật...) — bộ fuzz trộn chúng vào payload kế tiếp. Không có nó, gần như mọi lệnh nhắm tới một
 * đối tượng chỉ dừng ở "không tìm thấy", vì id/chuỗi ngẫu nhiên hầu như không bao giờ trúng thứ có
 * thật; đường xử lý SAU bước tìm thấy mới là nơi lỗi nằm.
 *
 * <p>Thứ tự ghi nhớ đi theo thứ tự câu trả lời, nên với cùng seed và cùng trạng thái server thì lựa
 * chọn vẫn xác định. Bỏ qua {@code ConnectResponse} (token phiên ngẫu nhiên) và {@code Pong} (đồng
 * hồ) — hai nguồn giá trị không lặp lại được giữa hai lần chạy.
 */
final class FuzzMemory {

    private static final int CAPACITY = 128;

    private final List<Long> numbers = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();

    void rememberNumber(long value) {
        if (value > 0 && !numbers.contains(value)) {
            add(numbers, value);
        }
    }

    void rememberText(String value) {
        if (!value.isEmpty() && !texts.contains(value)) {
            add(texts, value);
        }
    }

    boolean hasNumbers() {
        return !numbers.isEmpty();
    }

    boolean hasTexts() {
        return !texts.isEmpty();
    }

    long pickNumber(RandomGenerator rng) {
        return numbers.get(rng.nextInt(numbers.size()));
    }

    String pickText(RandomGenerator rng) {
        return texts.get(rng.nextInt(texts.size()));
    }

    /** Ghi nhớ mọi số dương và chuỗi trong các gói server vừa gửi (trừ lỗi, token phiên, Pong). */
    void harvest(List<PacketCodec.Frame> frames) throws InvalidProtocolBufferException {
        for (PacketCodec.Frame frame : frames) {
            Opcode opcode = Opcode.forNumber(frame.header().opcode());
            if (opcode == null
                    || opcode == Opcode.OPCODE_ERROR
                    || opcode == Opcode.OPCODE_PONG
                    || opcode == Opcode.OPCODE_CONNECT_RESPONSE) {
                continue;
            }
            walk(FuzzPayloads.defaultInstanceOf(opcode).getParserForType().parseFrom(frame.payload()));
        }
    }

    private void walk(Message message) {
        for (Map.Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
            FieldDescriptor field = entry.getKey();
            if (field.isRepeated()) {
                for (Object element : (List<?>) entry.getValue()) {
                    remember(field, element);
                }
            } else {
                remember(field, entry.getValue());
            }
        }
    }

    private void remember(FieldDescriptor field, Object value) {
        switch (field.getJavaType()) {
            case INT -> rememberNumber((Integer) value);
            case LONG -> rememberNumber((Long) value);
            case STRING -> rememberText((String) value);
            case MESSAGE -> walk((Message) value);
            default -> {
                // float/bool/enum/bytes: không phải định danh, không có gì để nhắm lại
            }
        }
    }

    private static <T> void add(List<T> values, T value) {
        if (values.size() == CAPACITY) {
            values.removeFirst();
        }
        values.add(value);
    }
}
