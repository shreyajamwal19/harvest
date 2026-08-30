package com.harvest.chef.importer;

import com.harvest.chef.entity.Recipe;
import com.harvest.chef.repository.RecipeRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time startup importer for the 231k-recipe Food.com dataset, gated behind
 * harvest.import.foodcom (default false). Skips entirely if the recipes table already has rows,
 * so it's safe to leave the flag on across restarts.
 */
@Component
@ConditionalOnProperty(name = "harvest.import.foodcom", havingValue = "true")
public class FoodComImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FoodComImporter.class);

    private final JdbcTemplate jdbcTemplate;
    private final RecipeRepository recipeRepository;
    private final EntityManager entityManager;

    public FoodComImporter(JdbcTemplate jdbcTemplate, RecipeRepository recipeRepository, EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.recipeRepository = recipeRepository;
        this.entityManager = entityManager;
    }

    @Override
    public void run(String... args) {
        if (recipeRepository.count() > 0) {
            log.info("Recipes table already contains rows. Skipping import.");
            return;
        }

        log.info("Starting import...");
        long startTime = System.currentTimeMillis();

        final int BATCH_SIZE = 500;
        final List<Recipe> batch = new ArrayList<>(BATCH_SIZE);
        final int[] totalImported = {0};
        final int[] rowCount = {0};

        String sql = "SELECT name, description, ingredients, steps FROM recipes_import";

        jdbcTemplate.query(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            sql,
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_READ_ONLY
                    );
                    ps.setFetchSize(BATCH_SIZE);
                    return ps;
                },
                rs -> {
                    rowCount[0]++;
                    try {
                        String title = rs.getString("name");
                        if (title == null || title.trim().isEmpty()) {
                            log.debug("Row {} has a blank title. Skipping.", rowCount[0]);
                            return;
                        }

                        Recipe recipe = new Recipe();
                        recipe.setTitle(title.trim());
                        
                        String description = rs.getString("description");
                        recipe.setDescription(
                                description == null || description.trim().isEmpty() || description.trim().equalsIgnoreCase("nan")
                                        ? null
                                        : description.trim()
                        );
                        
                        String ingredientsRaw = rs.getString("ingredients");
                        recipe.setIngredients(parsePythonList(ingredientsRaw));
                        
                        String stepsRaw = rs.getString("steps");
                        recipe.setSteps(parsePythonList(stepsRaw));

                        batch.add(recipe);

                        if (batch.size() >= BATCH_SIZE) {
                            try {
                                saveBatch(batch);
                                totalImported[0] += batch.size();
                                log.info("Imported {} recipes...", totalImported[0]);
                            } catch (Exception e) {
                                log.error("Failed to save batch ending at row {}. Total imported so far: {}. Error: {}", rowCount[0], totalImported[0], e.getMessage());
                            } finally {
                                batch.clear();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse or map row {}. Error: {}. Skipping malformed row.", rowCount[0], e.getMessage());
                    }
                }
        );

        if (!batch.isEmpty()) {
            try {
                saveBatch(batch);
                totalImported[0] += batch.size();
                log.info("Imported {} recipes...", totalImported[0]);
            } catch (Exception e) {
                log.error("Failed to save final batch. Total imported remains: {}. Error: {}", totalImported[0], e.getMessage());
            } finally {
                batch.clear();
            }
        }

        long elapsedMillis = System.currentTimeMillis() - startTime;
        log.info("Finished. Total imported: {}. Elapsed time: {} ms", totalImported[0], elapsedMillis);
    }

    private void saveBatch(List<Recipe> batch) {
        // Deliberately NOT wrapped in the outer run()'s transaction (there isn't one anymore -
        // @Transactional was removed from run() for exactly this reason): recipeRepository
        // .saveAll() is transactional on its own by default, so each batch commits
        // independently. With the whole 231k-row import in one giant transaction instead, a
        // single bad row anywhere in the file would poison the entire transaction on Postgres
        // (which refuses further commands once one statement in a transaction errors, until
        // rollback) - meaning every batch after the first failure would fail too, and the
        // eventual rollback would silently discard every recipe imported so far, batches that
        // this method's own try/catch in run() believed had already succeeded. Per-batch
        // commits make partial progress genuinely durable and one bad batch genuinely isolated.
        recipeRepository.saveAll(batch);
        entityManager.flush();
        entityManager.clear();
    }

    private List<String> parsePythonList(String pyList) {
        if (pyList == null || pyList.trim().isEmpty() || pyList.trim().equalsIgnoreCase("nan")) {
            return new ArrayList<>();
        }

        String trimmed = pyList.trim();
        
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        List<String> result = new ArrayList<>();
        boolean inString = false;
        char currentQuoteChar = 0;
        boolean escapeNext = false;
        StringBuilder currentElement = new StringBuilder();

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (escapeNext) {
                currentElement.append(c);
                escapeNext = false;
            } else if (c == '\\') {
                escapeNext = true;
            } else if (inString) {
                if (c == currentQuoteChar) {
                    inString = false;
                    result.add(currentElement.toString());
                    currentElement.setLength(0); 
                } else {
                    currentElement.append(c); 
                }
            } else {
                if (c == '\'' || c == '"') {
                    inString = true;
                    currentQuoteChar = c;
                }
            }
        }
        
        if (inString && currentElement.length() > 0) {
            result.add(currentElement.toString());
        }

        return result;
    }
}