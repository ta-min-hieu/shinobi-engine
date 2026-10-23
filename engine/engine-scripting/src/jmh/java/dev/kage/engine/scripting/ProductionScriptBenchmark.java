package dev.kage.engine.scripting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * README dòng 70(b), lô 18 (chủ dự án chọn "chỉ đo + báo số, chưa đổi trần", 2026-10-23): mỗi script skill/boss
 * THẬT tốn bao nhiêu trên tick khi chạy qua {@link ScriptSandbox#eval} — để đề xuất ngân sách riêng thay cho chốt
 * chống treo 500 ms. Host API chép đúng hình dạng {@code SkillSystem#evalSkillScript} (skill) và
 * {@code BossAiSystem} (boss).
 *
 * <p>Đọc script thẳng từ {@code server/server-world/src/main/resources/scripts} (engine-scripting không phụ thuộc
 * server-world; đây chỉ là công cụ đo). JMH fork chạy với thư mục làm việc là thư mục module.
 *
 * <p>Chạy: {@code ./gradlew :engine:engine-scripting:jmh -PjmhIncludes=ProductionScriptBenchmark} ({@code java -jar}
 * thẳng jar JMH không nạp được GraalJS). Số đo ghi ở BENCHMARK.md.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class ProductionScriptBenchmark {

    private static final Path SCRIPT_ROOT =
            Path.of("..", "..", "server", "server-world", "src", "main", "resources", "scripts");

    /** Trần câu lệnh của sandbox skill trên tick — khớp {@code SkillConstants}. */
    private static final long MAX_STATEMENTS = 10_000L;

    private static final Map<String, Object> SKILL_HOST_API =
            Map.of("ctx", Map.<String, Object>of("skill_dmg", 120.0, "target_hp_percent", 0.4));
    private static final Map<String, Object> BOSS_HOST_API = Map.of(
            "ctx",
            Map.<String, Object>of(
                    "hp_percent", 0.4, "already_enraged", false, "heavy_strike_ready", true, "base_atk", 30));

    @Param({
        "skill/fireball",
        "skill/hoa_bung_no",
        "skill/hoa_diem_nguc",
        "skill/hoa_tia_lua",
        "skill/loi_dien_giat",
        "skill/loi_thien_loi",
        "skill/loi_tia_set",
        "skill/loi_truy_kich",
        "skill/phong_bao_kich",
        "skill/phong_cuong_phong",
        "skill/phong_gia_toc",
        "skill/phong_loc_xoay",
        "skill/tho_dia_chan",
        "skill/tho_khien_da",
        "skill/tho_kien_dinh",
        "skill/tho_trong_kich",
        "skill/thuy_hoi_phuc",
        "skill/thuy_phong_toa",
        "skill/thuy_song_du",
        "skill/thuy_thuy_trieu",
        "boss/sample_boss",
        "boss/blood_sand_enforcer"
    })
    public String script;

    private ScriptSandbox sandbox;
    private String source;
    private Map<String, Object> hostApi;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        sandbox = new ScriptSandbox(MAX_STATEMENTS);
        source = Files.readString(SCRIPT_ROOT.resolve(script + ".js"), StandardCharsets.UTF_8);
        hostApi = script.startsWith("boss/") ? BOSS_HOST_API : SKILL_HOST_API;
    }

    /** Một lần chạy như trên tick: Context mới trên Engine chung, đúng sandbox production. */
    @Benchmark
    public boolean eval() {
        return sandbox.eval(source, hostApi, value -> value.hasMembers());
    }
}
