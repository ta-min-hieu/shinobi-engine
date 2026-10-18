package dev.kage.server.gateway;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

import dev.kage.protocol.Opcode;

/**
 * Sinh payload ĐỘC cho bộ fuzz opcode (xem {@link OpcodeFuzzer}) — hoàn toàn xác định theo seed.
 *
 * <p>Ba hình dạng, trộn theo tỉ lệ cố định:
 *
 * <ul>
 *   <li><b>byte ngẫu nhiên</b> — phần lớn không parse được (⇒ 400 {@code error.invalid_payload}), số ít
 *       parse "trúng" thành một message lạ;
 *   <li><b>message đúng KIỂU nhưng giá trị biên/độc</b> — số âm/0/MIN/MAX, NaN/vô cực, chuỗi rỗng/rất
 *       dài/NUL/surrogate lẻ/RTL/emoji, UUID sai, id không tồn tại, enum UNRECOGNIZED, repeated
 *       rỗng/rất dài/trùng lặp. Đây là hình dạng đi SÂU nhất — vượt qua parse, tới use case và DB;
 *   <li><b>message hợp lệ bị cắt cụt</b> — biên giữa hai dạng trên.
 * </ul>
 *
 * <p>Đọc kiểu message bằng phản chiếu ({@code getDefaultInstance}) theo CÙNG quy ước tên với
 * {@code OpcodeRegistryTest}: {@code OPCODE_FOO_BAR} mang message {@code FooBar}. Phản chiếu ở đây là
 * mã test, không phải game loop.
 */
final class FuzzPayloads {

    /** Trần cỡ payload — dưới {@code GatewayServer.MAX_FRAME_LENGTH} (64 KiB từ 2026-10-11) cho chắc chắn. */
    private static final int MAX_PAYLOAD_BYTES = 48 * 1024;

    private static final int MAX_RANDOM_BYTES = 96;
    private static final int MAX_DEPTH = 3;
    private static final int LONG_STRING_LENGTH = 4096;
    private static final int[] REPEATED_COUNTS = {0, 1, 2, 3, 17, 300};

    private static final int PERCENT = 100;
    private static final int RANDOM_BYTES_PERCENT = 20;
    private static final int TRUNCATED_PERCENT = 10;
    private static final int UNSET_FIELD_PERCENT = 20;
    private static final int DUPLICATE_ELEMENTS_PERCENT = 40;
    private static final int WELL_FORMED_KEY_PERCENT = 70;
    private static final int POOL_PERCENT = 85;
    private static final int REMEMBERED_PERCENT = 30;

    private static final int[] INT_POOL = {
        0, 1, -1, 2, 3, 5, 7, 10, 42, 100, 255, 65_535, 999_999, Short.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE
    };

    private static final long[] LONG_POOL = {
        0L, 1L, -1L, 2L, 3L, 4L, 5L, 10L, 999_999L, (long) Integer.MAX_VALUE + 1L, Long.MIN_VALUE, Long.MAX_VALUE
    };

    private static final float[] FLOAT_POOL = {
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        -0f,
        0f,
        1f,
        -1f,
        Float.MAX_VALUE,
        -Float.MAX_VALUE,
        Float.MIN_VALUE,
        1e30f,
        -1e30f,
        0.5f
    };

    private static final String[] STRING_POOL = {
        "",
        " ",
        "   ",
        "\t\n\r",
        "\u0000",
        "a\u0000b",
        "\uD800",
        "x\uDC00y",
        "\u202Eevil\u202C",
        "\u200B",
        "\uFEFF",
        "مرحبا",
        "Đạo Hữu",
        "😀😀",
        "🧙‍♂️",
        "x".repeat(21),
        "x".repeat(LONG_STRING_LENGTH),
        "not-a-uuid",
        "00000000-0000-0000-0000-000000000000",
        "constructor",
        "__proto__",
        "toString",
        "hasOwnProperty",
        "../../../etc/passwd",
        "'; DROP TABLE account;--",
        "%s%n%x",
        "${jndi:ldap://x}",
        "NaN",
        "-1",
        "9999999999999999999",
        "accept_quest:",
        "accept_quest:nonexistent",
        "accept_quest:kill_training_dummies",
        "open_shop",
        "open_storage",
        "senior_sister",
        "town_elder",
        "kill_training_dummies",
        "start",
        "Kage",
        "FuzzPeer",
        "FuzzHero",
        "fireball",
        "sample_dungeon",
        "report_to_outer_sect",
        "waypost_east_ridge"
    };

    private final RandomGenerator rng;
    private final FuzzMemory memory;

    /**
     * @param rng nguồn ngẫu nhiên DUY NHẤT của chuỗi payload này (xem {@link #random(long)})
     * @param memory id/chuỗi thật server vừa trả về — trộn vào để nhắm trúng đối tượng có thật
     */
    FuzzPayloads(RandomGenerator rng, FuzzMemory memory) {
        this.rng = rng;
        this.memory = memory;
    }

    /** Bộ sinh số cho một ca — cùng {@code caseSeed} luôn ra cùng payload. */
    static RandomGenerator random(long caseSeed) {
        return new SplittableRandom(caseSeed);
    }

    /** Lấy một phần tử của {@code options} — dùng chung nguồn ngẫu nhiên với payload. */
    <T> T pick(List<T> options) {
        return options.get(rng.nextInt(options.size()));
    }

    /** {@code true} với xác suất {@code percent}%. */
    boolean chance(int percent) {
        return rng.nextInt(PERCENT) < percent;
    }

    /** Payload ĐỘC cho {@code opcode} — luôn {@code ≤} {@link #MAX_PAYLOAD_BYTES}. */
    byte[] next(Opcode opcode) {
        int roll = rng.nextInt(PERCENT);
        if (roll < RANDOM_BYTES_PERCENT) {
            return randomBytes(rng.nextInt(MAX_RANDOM_BYTES + 1));
        }
        byte[] encoded = hostileMessage(defaultInstanceOf(opcode), 0).toByteArray();
        if (encoded.length > MAX_PAYLOAD_BYTES) {
            encoded = java.util.Arrays.copyOf(encoded, MAX_PAYLOAD_BYTES);
        }
        if (roll < RANDOM_BYTES_PERCENT + TRUNCATED_PERCENT && encoded.length > 1) {
            return java.util.Arrays.copyOf(encoded, rng.nextInt(encoded.length));
        }
        return encoded;
    }

    /** Message rỗng của đúng kiểu payload mà {@code opcode} mang. */
    static Message defaultInstanceOf(Opcode opcode) {
        String className = "dev.kage.protocol." + messageNameOf(opcode);
        try {
            return (Message)
                    Class.forName(className).getMethod("getDefaultInstance").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Không tìm thấy message payload cho " + opcode, e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("getDefaultInstance ném lỗi cho " + opcode, e.getCause());
        }
    }

    private Message hostileMessage(Message prototype, int depth) {
        Message.Builder builder = prototype.newBuilderForType();
        for (FieldDescriptor field : prototype.getDescriptorForType().getFields()) {
            if (rng.nextInt(PERCENT) < UNSET_FIELD_PERCENT) {
                continue;
            }
            if (field.isRepeated()) {
                fillRepeated(builder, field, depth);
            } else {
                Object value = hostileValue(builder, field, depth);
                builder.setField(field, value);
            }
        }
        return builder.build();
    }

    private void fillRepeated(Message.Builder builder, FieldDescriptor field, int depth) {
        // Lồng sâu thì bớt số phần tử — 300 × 300 phần tử là một payload hàng chục MB.
        int count = REPEATED_COUNTS[rng.nextInt(depth == 0 ? REPEATED_COUNTS.length : 4)];
        boolean duplicates = rng.nextInt(PERCENT) < DUPLICATE_ELEMENTS_PERCENT;
        Object first = null;
        for (int i = 0; i < count; i++) {
            Object value = duplicates && first != null ? first : hostileValue(builder, field, depth);
            if (first == null) {
                first = value;
            }
            builder.addRepeatedField(field, value);
        }
    }

    private Object hostileValue(Message.Builder builder, FieldDescriptor field, int depth) {
        return switch (field.getJavaType()) {
            case STRING -> hostileString(field);
            case BYTE_STRING -> ByteString.copyFrom(randomBytes(rng.nextInt(MAX_RANDOM_BYTES + 1)));
            case ENUM -> hostileEnum(field.getEnumType());
            case MESSAGE -> nestedMessage(builder, field, depth);
            default -> hostileNumber(field);
        };
    }

    /** Số và bool — tách khỏi {@link #hostileValue} cho dưới trần độ phức tạp của PMD. */
    private Object hostileNumber(FieldDescriptor field) {
        return switch (field.getJavaType()) {
            case INT -> hostileInt();
            case LONG -> hostileLong();
            case FLOAT, DOUBLE -> hostileReal(field);
            default -> rng.nextBoolean();
        };
    }

    private Object hostileReal(FieldDescriptor field) {
        float value = hostileFloat();
        return field.getJavaType() == FieldDescriptor.JavaType.DOUBLE ? (Object) (double) value : (Object) value;
    }

    private Message nestedMessage(Message.Builder builder, FieldDescriptor field, int depth) {
        Message empty = builder.newBuilderForField(field).build();
        return depth >= MAX_DEPTH ? empty : hostileMessage(empty, depth + 1);
    }

    private int hostileInt() {
        if (memory.hasNumbers() && chance(REMEMBERED_PERCENT)) {
            return (int) memory.pickNumber(rng);
        }
        return chance(POOL_PERCENT) ? INT_POOL[rng.nextInt(INT_POOL.length)] : rng.nextInt();
    }

    private long hostileLong() {
        if (memory.hasNumbers() && chance(REMEMBERED_PERCENT)) {
            return memory.pickNumber(rng);
        }
        return chance(POOL_PERCENT) ? LONG_POOL[rng.nextInt(LONG_POOL.length)] : rng.nextLong();
    }

    private float hostileFloat() {
        return rng.nextInt(PERCENT) < POOL_PERCENT
                ? FLOAT_POOL[rng.nextInt(FLOAT_POOL.length)]
                : (rng.nextFloat() - 0.5f) * 2_000f;
    }

    private String hostileString(FieldDescriptor field) {
        // Khoá idempotency SAI dạng đã có cổng riêng (IdempotencyKeyFormat) — phần lớn ca phải mang
        // khoá ĐÚNG dạng thì payload mới đi qua được cổng đó, tới use case và DB phía sau.
        if (field.getName().contains("idempotency") && rng.nextInt(PERCENT) < WELL_FORMED_KEY_PERCENT) {
            return new UUID(rng.nextLong(), rng.nextLong()).toString();
        }
        if (memory.hasTexts() && chance(REMEMBERED_PERCENT)) {
            return memory.pickText(rng);
        }
        if (rng.nextInt(PERCENT) < POOL_PERCENT) {
            return STRING_POOL[rng.nextInt(STRING_POOL.length)];
        }
        int length = 1 + rng.nextInt(30);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            // Toàn dải BMP lẫn mặt phẳng bổ sung — gồm cả surrogate lẻ khi rơi vào D800–DFFF.
            int codePoint = rng.nextInt(0x1_0000 + 0x100);
            text.appendCodePoint(codePoint >= 0x1_0000 ? 0x1F300 + (codePoint - 0x1_0000) : codePoint);
        }
        return text.toString();
    }

    private EnumValueDescriptor hostileEnum(EnumDescriptor type) {
        List<EnumValueDescriptor> values = new ArrayList<>(type.getValues());
        int choice = rng.nextInt(values.size() + 2);
        if (choice < values.size()) {
            return values.get(choice);
        }
        // proto3 enum mở: số lạ vẫn lên dây và parse thành UNRECOGNIZED phía server.
        int unknownNumber = choice == values.size() ? -1 : 1_000 + rng.nextInt(1_000);
        return type.findValueByNumberCreatingIfUnknown(unknownNumber);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        rng.nextBytes(bytes);
        return bytes;
    }

    /** {@code OPCODE_FOO_BAR -> FooBar} — cùng quy ước {@code OpcodeRegistryTest}. */
    private static String messageNameOf(Opcode opcode) {
        if (opcode == Opcode.OPCODE_PING) {
            return "PingCommand";
        }
        String upper = opcode.name().substring("OPCODE_".length());
        StringBuilder name = new StringBuilder(upper.length());
        boolean wordStart = true;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '_') {
                wordStart = true;
            } else {
                name.append(wordStart ? c : Character.toLowerCase(c));
                wordStart = false;
            }
        }
        return name.toString();
    }
}
