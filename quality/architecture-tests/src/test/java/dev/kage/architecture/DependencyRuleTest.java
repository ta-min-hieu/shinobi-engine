package dev.kage.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ADR-011 — biến ranh giới kiến trúc thành luật máy thi hành (CI-CD §4).
 *
 * <p>Rule dùng wildcard {@code ..} nên bền vững với package prefix: chỉ cần các segment
 * ngữ nghĩa (engine.ecs, server.gateway, server.database, domain, infrastructure) xuất hiện.
 *
 * <p>Mọi module production đều trên classpath (build.gradle.kts tự dò, {@link ScannedModulesTest}
 * chốt ngược) và {@code failOnEmptyShould=true}: rule có mệnh đề {@code that()} rỗng là ĐỎ, không
 * phải "pass trivially". KHÔNG xoá rule nào mà không có ADR ({@code Supersedes ADR-011}).
 */
@AnalyzeClasses(packages = "dev.kage", importOptions = ImportOption.DoNotIncludeTests.class)
// Tên field @ArchTest là TÊN HIỂN THỊ của rule trong báo cáo JUnit/ArchUnit — viết thành câu snake_case
// cho đọc được ("gameplay_khong_import_netty"), đúng quy ước ví dụ của ArchUnit, và đã được trích theo
// đúng tên này ở ROADMAP/CLAUDE.md/javadoc. Đây không phải hằng số theo nghĩa ConstantName nhắm tới.
@SuppressWarnings("checkstyle:ConstantName")
class DependencyRuleTest {

    @ArchTest
    static final ArchRule gameplay_khong_import_netty = noClasses()
            .that()
            .resideOutsideOfPackage("..server.gateway..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.netty..")
            .because("Chỉ server-gateway biết Netty (Điều 23, ADR-003)");

    @ArchTest
    static final ArchRule chi_engine_ecs_import_ashley = noClasses()
            .that()
            .resideOutsideOfPackage("..engine.ecs..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.badlogic.ashley..")
            .because("Điều kiện đảo ngược ADR-006: gameplay không import Ashley trực tiếp");

    @ArchTest
    static final ArchRule chi_server_database_dung_jdbc = noClasses()
            .that()
            .resideOutsideOfPackage("..server.database..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.sql..", "javax.sql..", "org.postgresql..")
            .because("Chỉ server-database chạm JDBC (Điều 23)");

    @ArchTest
    static final ArchRule chi_server_database_dung_redis = noClasses()
            .that()
            .resideOutsideOfPackage("..server.database..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("redis.clients..", "io.lettuce..")
            .because("Chỉ server-database chạm Redis/Valkey client (Điều 23)");

    @ArchTest
    static final ArchRule chi_server_gateway_dung_micrometer = noClasses()
            .that()
            .resideOutsideOfPackage("..server.gateway..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.micrometer..")
            .because("Chỉ server-gateway biết Micrometer (ADR-015) — server-world dùng qua TickMetricsPort");

    @ArchTest
    static final ArchRule domain_khong_import_infrastructure = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because("Dependency Inversion — domain là tâm (REPOSITORY-STRUCTURE §4)");

    @ArchTest
    static final ArchRule gameplay_khong_import_spring = noClasses()
            .that()
            .resideInAnyPackage("dev.kage..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .because("Không dùng Spring cho Game Server (ADR-009)");

    /**
     * Module {@code shared-*} KHÔNG nằm dưới gói {@code ..shared..}: shared-protocol sinh code vào
     * {@code dev.kage.protocol} (java_package của mọi .proto). Bản cũ chọn {@code ..shared..} — tập rỗng,
     * rule xanh giả từ Phase 0 tới khi rà 2026-10-03 bật lại {@code failOnEmptyShould}. Cấm theo chiều
     * ngược lại cho chắc: protocol không được biết bất kỳ gói {@code dev.kage} nào khác (engine, client,
     * server và cả 14 feature module {@code dev.kage.<feature>}).
     */
    @ArchTest
    static final ArchRule shared_khong_phu_thuoc_gameplay = noClasses()
            .that()
            .resideInAPackage("dev.kage.protocol..")
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage("dev.kage..")
                    .and(JavaClass.Predicates.resideOutsideOfPackage("dev.kage.protocol..")))
            .because("shared chỉ chứa DTO/Protocol/Constant, không business logic (Điều 22)");

    @ArchTest
    static final ArchRule engine_ecs_khong_phu_thuoc_engine_core = noClasses()
            .that()
            .resideInAPackage("..engine.ecs..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..engine.core..")
            .because("Server dùng engine-ecs nhưng KHÔNG được kéo theo rendering (ENGINE.md §9)");
}
