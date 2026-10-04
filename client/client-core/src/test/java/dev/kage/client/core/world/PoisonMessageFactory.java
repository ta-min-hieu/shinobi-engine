package dev.kage.client.core.world;

import java.util.List;
import java.util.Random;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;

/**
 * Sinh message protobuf ĐÚNG KIỂU nhưng mang giá trị độc — đi theo {@link Descriptor} nên mọi
 * message (kể cả message thêm sau này) tự có mặt, không cần ai nhớ viết thêm một bộ sinh.
 *
 * <p>Giá trị được nghiêng về phía hỏng: NaN/vô cực/biên số cho số thực, 0/âm/MIN/MAX cho số
 * nguyên, id lấy từ một bể NHỎ (để spawn/move/despawn hay trùng nhau — "thứ tự lạ" cần các gói
 * cùng nói về một thực thể), chuỗi rỗng/rất dài/NUL/RTL/emoji/khoá i18n giả, enum lạ.
 */
final class PoisonMessageFactory {

    /** Id của CHÍNH nhân vật đang chơi trong mọi ca fuzz — gói "nói về mình" phải xuất hiện. */
    static final long LOCAL_ID = 42L;

    private static final long[] ID_POOL = {
        LOCAL_ID,
        0L,
        -1L,
        1L,
        Long.MIN_VALUE,
        Long.MAX_VALUE,
        -100L,
        -101L,
        -102L, // quái
        1_000L,
        1_001L,
        1_002L, // nhân vật khác
        -5_000L,
        -5_001L, // NPC
        7L,
        8L,
        9L, // vật phẩm / thư / bang / tổ đội...
    };

    private static final int[] POISON_INTS = {0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, -100_000, 100_000};

    private static final float[] POISON_FLOATS = {
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        Float.MAX_VALUE,
        -Float.MAX_VALUE,
        Float.MIN_VALUE,
        -0f,
        0f,
        1e30f,
    };

    private static final int LONG_STRING_CHARS = 5_000;
    private static final int MAX_DEPTH = 3;
    private static final int MAX_BYTES_FIELD = 16;
    private static final int UNKNOWN_ENUM_NUMBER = 9_999;
    private static final float WORLD_SPAN = 50f;

    private static final List<String> POISON_STRINGS = List.of(
            "",
            "a",
            "x".repeat(LONG_STRING_CHARS),
            "\u0000",
            "nul\u0000in\u0000middle",
            (char) 0x202E + "evil" + (char) 0x202C, // RTL override
            "😀🐉",
            "{0}{1}{2}'",
            "'{'",
            "line\nbreak\r\n\t",
            "hud.monster.",
            "error.not_authenticated",
            "error.logged_in_elsewhere",
            "item.name.",
            "quest.name.",
            "???");

    private final Random random;

    PoisonMessageFactory(Random random) {
        this.random = random;
    }

    /** Bộ nhận chính xác các field, có độ dài repeated mặc định (0..~20). */
    DynamicMessage message(Descriptor descriptor) {
        return build(descriptor, 0, -1);
    }

    /** Như {@link #message} nhưng MỌI field repeated ở tầng ngoài cùng có đúng {@code count} phần tử. */
    DynamicMessage messageWithRepeated(Descriptor descriptor, int count) {
        return build(descriptor, 0, count);
    }

    private DynamicMessage build(Descriptor descriptor, int depth, int forcedRepeatedCount) {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
        for (FieldDescriptor field : descriptor.getFields()) {
            if (field.isMapField()) {
                continue; // client không đọc map nào; DynamicMessage với map entry rối rắm vô ích.
            }
            if (field.isRepeated()) {
                int count = forcedRepeatedCount >= 0 ? forcedRepeatedCount : repeatedCount(depth);
                for (int i = 0; i < count; i++) {
                    builder.addRepeatedField(field, value(field, depth, forcedRepeatedCount >= 0));
                }
            } else if (random.nextInt(8) != 0) { // đôi khi bỏ trống hẳn — giá trị mặc định cũng là một ca.
                builder.setField(field, value(field, depth, false));
            }
        }
        return builder.build();
    }

    /** Chỉ tầng ngoài cùng được dài — lồng nhau mà mỗi tầng 200 phần tử là 200^3 message, nổ heap test. */
    private int repeatedCount(int depth) {
        if (depth > 0) {
            return random.nextInt(4);
        }
        int roll = random.nextInt(10);
        if (roll < 6) {
            return random.nextInt(4);
        }
        if (roll < 9) {
            return 4 + random.nextInt(17);
        }
        return 21 + random.nextInt(200);
    }

    private Object value(FieldDescriptor field, int depth, boolean cheap) {
        return switch (field.getJavaType()) {
            case INT -> poisonInt();
            case LONG -> field.getName().endsWith("id") ? poisonId() : poisonLong();
            case FLOAT -> poisonFloat();
            case DOUBLE -> (double) poisonFloat();
            case BOOLEAN -> random.nextBoolean();
            default -> nonNumeric(field, depth, cheap);
        };
    }

    private Object nonNumeric(FieldDescriptor field, int depth, boolean cheap) {
        return switch (field.getJavaType()) {
            case STRING -> cheap ? "" : poisonString();
            case BYTE_STRING -> ByteString.copyFrom(randomBytes(random.nextInt(MAX_BYTES_FIELD)));
            case ENUM -> poisonEnum(field.getEnumType());
            default -> nested(field.getMessageType(), depth, cheap);
        };
    }

    private DynamicMessage nested(Descriptor type, int depth, boolean cheap) {
        if (depth >= MAX_DEPTH || cheap) {
            return DynamicMessage.getDefaultInstance(type);
        }
        return build(type, depth + 1, -1);
    }

    int poisonInt() {
        if (random.nextInt(4) == 0) {
            return POISON_INTS[random.nextInt(POISON_INTS.length)];
        }
        return random.nextInt(201);
    }

    long poisonLong() {
        if (random.nextInt(4) == 0) {
            return random.nextBoolean() ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return random.nextInt(10_001) - 100L;
    }

    long poisonId() {
        if (random.nextInt(5) == 0) {
            return random.nextLong();
        }
        return ID_POOL[random.nextInt(ID_POOL.length)];
    }

    float poisonFloat() {
        if (random.nextInt(3) == 0) {
            return POISON_FLOATS[random.nextInt(POISON_FLOATS.length)];
        }
        return (random.nextFloat() * 2f - 1f) * WORLD_SPAN;
    }

    String poisonString() {
        if (random.nextInt(3) == 0) {
            return "s" + random.nextInt(1_000);
        }
        return POISON_STRINGS.get(random.nextInt(POISON_STRINGS.size()));
    }

    private EnumValueDescriptor poisonEnum(EnumDescriptor type) {
        if (random.nextInt(4) == 0) {
            int number = random.nextBoolean() ? UNKNOWN_ENUM_NUMBER : -1;
            return type.findValueByNumberCreatingIfUnknown(number);
        }
        List<EnumValueDescriptor> values = type.getValues();
        return values.get(random.nextInt(values.size()));
    }

    byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }
}
