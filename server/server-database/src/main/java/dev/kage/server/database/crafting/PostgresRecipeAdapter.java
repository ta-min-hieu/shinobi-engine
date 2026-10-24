package dev.kage.server.database.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.crafting.domain.Recipe;
import dev.kage.crafting.domain.RecipeMaterial;
import dev.kage.crafting.domain.RecipePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link RecipePort} (ADR-079, V76) — công thức là tham số, đọc MỘT lần rồi giữ. */
public final class PostgresRecipeAdapter implements RecipePort {

    private static final String RECIPES = """
            SELECT r.id, r.code, r.output_item_template_id, r.output_quantity, r.gold_cost, r.required_level
              FROM crafting_recipe r
             ORDER BY r.sort_order, r.id
            """;

    private static final String MATERIALS = """
            SELECT recipe_id, item_template_id, quantity
              FROM crafting_recipe_material
             ORDER BY recipe_id, item_template_id
            """;

    private record Row(long id, String code, long output, int outputQuantity, long goldCost, int requiredLevel) {}

    private final DataSource dataSource;
    private volatile List<Recipe> cached;

    public PostgresRecipeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Recipe> recipes() {
        List<Recipe> loaded = cached;
        if (loaded == null) {
            loaded = load();
            cached = loaded;
        }
        return loaded;
    }

    private List<Recipe> load() {
        Map<Long, List<RecipeMaterial>> materials = new LinkedHashMap<>();
        JdbcOperations.query(
                        dataSource,
                        MATERIALS,
                        rs -> Map.entry(rs.getLong(1), new RecipeMaterial(rs.getLong(2), rs.getInt(3))))
                .forEach(row -> materials
                        .computeIfAbsent(row.getKey(), id -> new ArrayList<>())
                        .add(row.getValue()));
        List<Recipe> recipes = new ArrayList<>();
        for (Row row : JdbcOperations.query(
                dataSource,
                RECIPES,
                rs -> new Row(
                        rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getLong(5), rs.getInt(6)))) {
            recipes.add(new Recipe(
                    row.code(),
                    row.output(),
                    row.outputQuantity(),
                    row.goldCost(),
                    row.requiredLevel(),
                    materials.getOrDefault(row.id(), List.of())));
        }
        return List.copyOf(recipes);
    }
}
