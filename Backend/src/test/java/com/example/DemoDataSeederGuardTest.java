package com.example;

import com.example.service.DemoDataSeeder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The seeder's safety checks, with no Spring context and no database. Both
 * refusals happen before the seeder touches a repository, so nulls stand in
 * for everything it would otherwise use.
 */
class DemoDataSeederGuardTest {

    private static final String LOCAL = "jdbc:mysql://localhost:3306/qcare_db";

    private static DemoDataSeeder seeder(String url, boolean allowRemote, String env) {
        return new DemoDataSeeder(null, null, null, null, null, null, null, null, null, null, null, null,
                "pw", url, allowRemote, env);
    }

    @Test
    void refusesWhenQcareEnvIsProduction() {
        assertThatThrownBy(() -> seeder(LOCAL, false, "production").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("QCARE_ENV=production");
        assertThatThrownBy(() -> seeder(LOCAL, false, " Production ").run())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theRemoteOverrideDoesNotUnlockProduction() {
        assertThatThrownBy(() -> seeder("jdbc:mysql://db.example.com/qcare_db", true, "production").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("QCARE_ENV=production");
    }

    @Test
    void refusesANonLocalMysqlHost() {
        assertThatThrownBy(() -> seeder("jdbc:mysql://db.example.com:3306/qcare_db", false, "").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non-local");
    }
}
