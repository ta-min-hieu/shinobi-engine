package dev.kage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Chốt ngược cho {@link DependencyRuleTest}: mọi rule của nó dạng {@code noClasses()...should()}, nên
 * một module KHÔNG có mặt trên classpath test thì không bao giờ vi phạm được gì — xanh giả.
 *
 * <p>Đã xảy ra thật (rà 2026-10-03): {@code build.gradle.kts} liệt kê tay module từ Phase 3;
 * {@code client-desktop} chưa từng được quét (các feature module sau đó chỉ tới classpath gián tiếp
 * qua server-gateway — may chứ không phải thiết kế). Test này đối chiếu với mã nguồn
 * THẬT: mỗi thư mục {@code src/main/java} của module production (Gradle truyền vào qua system
 * property) phải góp ít nhất một class vào đúng tập mà {@link DependencyRuleTest} phân tích.
 */
class ScannedModulesTest {

    private static final String SOURCE_ROOTS_PROPERTY = "kage.architecture.sourceRoots";

    @Test
    void everyProductionModuleShouldBeOnTheScannedClasspath() throws IOException {
        String property = System.getProperty(SOURCE_ROOTS_PROPERTY, "");
        List<Path> sourceRoots = Stream.of(property.split(File.pathSeparator))
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .toList();
        assertThat(sourceRoots)
                .as("Gradle không truyền %s — không có gì để đối chiếu", SOURCE_ROOTS_PROPERTY)
                .isNotEmpty();

        JavaClasses scanned = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.kage");

        List<String> missing = new ArrayList<>();
        int sampled = 0;
        for (Path root : sourceRoots) {
            Optional<String> sample = anyClassName(root);
            if (sample.isEmpty()) {
                continue;
            }
            sampled++;
            if (!scanned.contain(sample.get())) {
                missing.add(root + " (vd " + sample.get() + ")");
            }
        }
        assertThat(sampled).as("không tìm thấy file .java nào dưới %s", sourceRoots).isPositive();
        assertThat(missing)
                .as("module production có mã nguồn mà ArchUnit không nhìn thấy — mọi rule xanh giả với nó")
                .isEmpty();
    }

    private static Optional<String> anyClassName(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .filter(path -> !path.getFileName().toString().equals("module-info.java"))
                    .findFirst()
                    .map(path -> {
                        String relative = root.relativize(path).toString().replace(File.separatorChar, '.');
                        return relative.substring(0, relative.length() - ".java".length());
                    });
        }
    }
}
