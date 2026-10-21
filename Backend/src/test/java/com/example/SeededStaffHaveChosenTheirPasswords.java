package com.example;

import com.example.entity.User;
import com.example.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Test-only. The server refuses every call but /api/auth/me and
 * /api/auth/change-password from an account still on its temporary password,
 * and DataSeeder flags every staff account it creates. Most test classes sign
 * in as those staff to exercise the desk, the doctor screen or the admin pages,
 * so this clears the flag once the seeder has run: the state of a clinic whose
 * staff have already chosen their passwords.
 *
 * Clearing it in the database, rather than having each test change the
 * password, keeps changeme123 working for every class sharing the database.
 *
 * ForcedPasswordChangeTest turns this off with
 * qcare.test.keep-forced-password-change=true to test the enforcement itself
 * against the real seeded accounts.
 *
 * Lives under src/test, so it is never part of the application.
 */
@Component
@ConditionalOnProperty(name = "qcare.test.keep-forced-password-change", havingValue = "false",
        matchIfMissing = true)
class SeededStaffHaveChosenTheirPasswords {

    private final UserRepository userRepository;

    SeededStaffHaveChosenTheirPasswords(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** After every CommandLineRunner, so after DataSeeder. */
    @EventListener(ApplicationReadyEvent.class)
    void clearForcedPasswordChange() {
        List<User> flagged = userRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getMustChangePassword()))
                .toList();
        flagged.forEach(user -> user.setMustChangePassword(false));
        userRepository.saveAll(flagged);
    }
}
