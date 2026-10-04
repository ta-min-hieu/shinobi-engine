package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

import dev.kage.protocol.Opcode;

/**
 * Bảng opcode server→client → kiểu message protobuf của nó, dựng TỪ enum {@link Opcode} — opcode
 * mới thêm vào giao thức tự có mặt trong fuzz, không cần ai nhớ.
 *
 * <p>Phân loại hướng gói cùng phép với {@code ClientOpcodeCoverageTest}; kiểu message suy từ tên
 * opcode theo quy ước đặt tên của giao thức ({@code OPCODE_MOVE_RESPONSE} → {@code MoveResponse},
 * {@code OPCODE_PONG} → {@code PongResponse}).
 */
final class ServerOpcodes {

    static final List<Opcode> SERVER_TO_CLIENT = Collections.unmodifiableList(serverToClient());

    static final Map<Opcode, Descriptor> DESCRIPTORS = Collections.unmodifiableMap(descriptors());

    private static final String PROTOCOL_PACKAGE = "dev.kage.protocol.";

    private ServerOpcodes() {}

    /**
     * Lệnh TRA CỨU ({@code OPCODE_LIST_…}/{@code OPCODE_GET_…} kết thúc bằng {@code _REQUEST}) —
     * chỉ đọc, không đổi gì phía server.
     */
    static boolean isQuery(Opcode opcode) {
        String name = opcode.name();
        return name.endsWith("_REQUEST") && (name.startsWith("OPCODE_LIST_") || name.startsWith("OPCODE_GET_"));
    }

    static boolean hasRepeated(Descriptor descriptor) {
        for (FieldDescriptor field : descriptor.getFields()) {
            if (field.isRepeated()) {
                return true;
            }
        }
        return false;
    }

    private static List<Opcode> serverToClient() {
        List<Opcode> result = new ArrayList<>();
        for (Opcode opcode : Opcode.values()) {
            if (isWire(opcode) && !isClientToServer(opcode)) {
                result.add(opcode);
            }
        }
        return result;
    }

    private static boolean isWire(Opcode opcode) {
        return opcode != Opcode.UNRECOGNIZED && opcode != Opcode.OPCODE_UNSPECIFIED;
    }

    private static boolean isClientToServer(Opcode opcode) {
        String name = opcode.name();
        return opcode == Opcode.OPCODE_PING || name.endsWith("_COMMAND") || name.endsWith("_REQUEST");
    }

    private static Map<Opcode, Descriptor> descriptors() {
        Map<Opcode, Descriptor> result = new LinkedHashMap<>();
        for (Opcode opcode : serverToClient()) {
            String camel = camelCase(opcode.name().substring("OPCODE_".length()));
            Descriptor descriptor = descriptorOf(PROTOCOL_PACKAGE + camel);
            if (descriptor == null) {
                descriptor = descriptorOf(PROTOCOL_PACKAGE + camel + "Response");
            }
            if (descriptor != null) {
                result.put(opcode, descriptor);
            }
        }
        return result;
    }

    private static Descriptor descriptorOf(String className) {
        try {
            Class<?> type = Class.forName(className);
            return (Descriptor) type.getMethod("getDescriptor").invoke(null);
        } catch (ReflectiveOperationException e) {
            return null; // không có lớp tên ấy — thử tên kế tiếp.
        }
    }

    private static String camelCase(String upperSnake) {
        StringBuilder builder = new StringBuilder();
        for (String part : upperSnake.split("_", -1)) {
            builder.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }
}
