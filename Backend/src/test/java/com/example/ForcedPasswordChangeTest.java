package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The server, not only the frontend, holds an account on a temporary password
 * to changing it: until then every endpoint that needs a signed-in user
 * answers 403 PASSWORD_CHANGE_REQUIRED, except /api/auth/me (restoring the
 * session) and /api/auth/change-password (the way out).
 *
 * Runs against the real seeded accounts, so it keeps their flag
 * (SeededStaffHaveChosenTheirPasswords clears it for every other class). Its
 * own in-memory database, because a second context creating its tables in the
 * shared one would wipe the data the other classes are using.
 */
@SpringBootTest(properties = {
        "qcare.test.keep-forced-password-change=true",
        "spring.datasource.url=jdbc:h2:mem:forced-password-change;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ForcedPasswordChangeTest {

    private static final String SEED_PASSWORD = "changeme123";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aFlaggedAccountCanOnlyChangeItsPasswordAndThenWorksNormally() throws Exception {
        String token = signIn("03400009", "Reception Desk One", SEED_PASSWORD);

        // A normal desk endpoint is refused - with 403, never 401, so the
        // browser keeps the session instead of signing the user out.
        call(get("/api/queues/today"), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // Restoring the session still works, and says why.
        call(get("/api/auth/me"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // Public pages keep working for someone carrying the token.
        call(get("/api/queues/today/board"), token).andExpect(status().isOk());

        call(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", SEED_PASSWORD, "newPassword", "Desk-One-pass1"))),
                token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // The same token now works: the flag is read on every request.
        call(get("/api/queues/today"), token).andExpect(status().isOk());
        call(get("/api/auth/me"), token)
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void everySeededStaffAccountStartsFlaggedAndIsHeldToIt() throws Exception {
        String receptionist = signIn("03400010", "Reception Desk Two", SEED_PASSWORD);
        String doctor = signIn("03400001", "Dr. Rania Haddad", SEED_PASSWORD);
        String admin = signIn("03400011", "Admin One", SEED_PASSWORD);

        for (String token : new String[] { receptionist, doctor, admin }) {
            call(get("/api/auth/me"), token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mustChangePassword").value(true));
        }

        call(get("/api/queues/today"), receptionist)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
        call(get("/api/doctors/me/schedule"), doctor)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
        call(get("/api/admin/users"), admin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void anUnflaggedAccountIsNotAffected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Free Patient", "phoneNumber", "03991299",
                                "password", "Free-patient1"))))
                .andExpect(status().isCreated());
        String token = signIn("03991299", "Free Patient", "Free-patient1");

        call(get("/api/appointments/me"), token).andExpect(status().isOk());
    }

    private String signIn(String phone, String fullName, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of(
                                        "phoneNumber", phone, "fullName", fullName,
                                        "password", password))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("token").asText();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
