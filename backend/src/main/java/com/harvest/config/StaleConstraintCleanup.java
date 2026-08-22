package com.harvest.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * conversation_messages carries a Postgres CHECK constraint on response_type
 * (conversation_messages_response_type_check) that was added directly against the database at
 * some point, listing a fixed set of legal values - it predates {@code ChefResponseType} gaining
 * MEAL_PLAN and SHOPPING_LIST, and {@code ddl-auto=update} does not know about or manage
 * hand-added CHECK constraints, so it was never kept in sync.
 *
 * Effect in production: every meal-plan/shopping-list turn generated a valid response and was
 * shown to the user, but the Memory Write step's INSERT was rejected by Postgres (SQLState
 * 23514), which rolled back the whole @Transactional method and surfaced as an unrelated-looking
 * "Chef Brain request failed unexpectedly" - despite the request having actually succeeded.
 *
 * The column is already validated at the application layer ({@code @Enumerated(EnumType.STRING)}
 * against the {@code ChefResponseType} enum) - a DB-level allow-list of the enum's current values
 * is redundant and, as shown here, guaranteed to drift out of sync every time the enum grows.
 * Dropping it (rather than reissuing it with today's values) fixes this permanently instead of
 * moving the same bug to the next new response type.
 *
 * Runs once per startup, is a no-op (IF EXISTS) if the constraint is already gone, and never
 * fails startup if it can't run (e.g. insufficient DB privileges) - it logs and moves on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleConstraintCleanup implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE conversation_messages DROP CONSTRAINT IF EXISTS conversation_messages_response_type_check");
            log.info("[startup] Ensured conversation_messages_response_type_check is not present "
                    + "(response_type is validated in code via ChefResponseType, not a DB allow-list)");
        } catch (Exception e) {
            log.warn("[startup] Could not drop conversation_messages_response_type_check - "
                    + "if MEAL_PLAN/SHOPPING_LIST turns are failing, this needs to be dropped manually: {}",
                    e.getMessage());
        }
    }
}
