package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Dòng 73(d) (2026-10-17): mọi lớp Component trong mã nguồn phải nằm trong danh sách đăng ký sẵn —
 * quên một lớp là sổ ComponentType tĩnh của Ashley lại bị ghi từ tick thread của một World.
 */
class WorldComponentTypesTest {

    private static final List<Path> COMPONENT_SOURCES = List.of(
            Path.of("src/main/java/dev/kage/server/world/component"),
            Path.of("../../engine/engine-ecs/src/main/java/dev/kage/engine/ecs/component"));

    @Test
    void everyComponentInTheSourceTreeShouldBePreregistered() throws IOException {
        List<String> registered =
                WorldComponentTypes.ALL.stream().map(Class::getSimpleName).toList();

        List<String> declared = declaredComponents();

        assertThat(declared).as("đọc được thư mục component").isNotEmpty();
        assertThat(registered).containsExactlyInAnyOrderElementsOf(declared);
    }

    private static List<String> declaredComponents() throws IOException {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (Path directory : COMPONENT_SOURCES) {
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    String source = Files.readString(file, StandardCharsets.UTF_8);
                    if (source.contains("implements Component")) {
                        names.add(String.valueOf(file.getFileName()).replace(".java", ""));
                    }
                }
            }
        }
        return names;
    }
}
