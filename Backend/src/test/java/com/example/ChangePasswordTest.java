package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changing a password: the current one must be given again, the new one must
 * meet the same rules as registering, and afterwards only the new one works.
 *
 * Uses its own freshly registered patient, never a seeded account: other test
 * classes sign in as the seeded receptionist and share this database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordTest {

    private static final String PHONE = "03991234";
    private static final String NAME = "Password Changer";
    private static final String FIRST = "First-password1";
    private static final String SECOND = "Second-password2";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aPatientChangesTheirPasswordAndOnlyTheNewOneWorks() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", NAME, "phoneNumber", PHONE, "password", FIRST))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
        String token = token(FIRST);

        // A wrong current password is a 400, never a 401, so the browser does
        // not treat it as an expired session and sign the user out.
        change(token, "Wrong-password1", SECOND)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CURRENT_PASSWORD_WRONG"));
        change(token, FIRST, "weakpassword")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PASSWORD_NO_UPPERCASE"));
        change(token, FIRST, FIRST)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SAME_PASSWORD"));

        change(token, FIRST, SECOND)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(NAME))
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        signIn(FIRST).andExpect(status().isUnauthorized());
        signIn(SECOND).andExpect(status().isOk());
    }

    @Test
    void theSeededReceptionistIsAskedToChangeTheirPassword() throws Exception {
        String token = json.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phoneNumber", "03400009",
                                "fullName", "Reception Desk One", "password", "changeme123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void changingAPasswordNeedsASignIn() throws Exception {
        mvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", FIRST, "newPassword", SECOND))))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions change(String token, String current, String next) throws Exception {
        return mvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "currentPassword", current, "newPassword", next))));
    }

    private ResultActions signIn(String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "phoneNumber", PHONE, "fullName", NAME, "password", password))));
    }

    private String token(String password) throws Exception {
        return json.readTree(signIn(password)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("token").asText();
    }
}
