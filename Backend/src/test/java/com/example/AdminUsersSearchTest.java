package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Searching the user list: a phone number is found however it is typed, the
 * characters % and _ are searched for as themselves, and the list comes back
 * in name order.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUsersSearchTest {

    private static final String SEED_PASSWORD = "changeme123";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();
    private String admin;

    @BeforeEach
    void signInAsAdmin() throws Exception {
        admin = signIn("03400011", "Admin One", SEED_PASSWORD);
    }

    @Test
    void aPhoneNumberIsFoundHoweverItIsTyped() throws Exception {
        // Seeded: Dr. Rania Haddad, 03400001.
        for (String typed : List.of("03400001", "03 400 001", "03-400-001",
                "+961 3 400 001", "00961 3 400 001", "400 001")) {
            assertThat(names(search(typed, null))).as(typed).contains("Dr. Rania Haddad");
        }
    }

    @Test
    void percentAndUnderscoreAreSearchedForAsThemselves() throws Exception {
        assertThat(search("%", null)).as("%").isEmpty();
        assertThat(search("_", null)).as("_").isEmpty();
        assertThat(search("!", null)).as("!").isEmpty();
        // "Dr_ Rania" must not match "Dr. Rania" as though _ meant any character.
        assertThat(names(search("Dr_ Rania", null))).doesNotContain("Dr. Rania Haddad");
    }

    @Test
    void theListIsInNameOrder() throws Exception {
        createReceptionist("Zeina Order Test", "03790001");
        createReceptionist("Adam Order Test", "03790002");
        createReceptionist("maya Order Test", "03790003");

        assertThat(names(search("Order Test", null)))
                .containsExactly("Adam Order Test", "maya Order Test", "Zeina Order Test");

        List<String> everyone = names(search(null, null));
        List<String> sorted = new ArrayList<>(everyone);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        assertThat(everyone).isEqualTo(sorted);
    }

    @Test
    void theRoleFilterIgnoresCaseAndSpaces() throws Exception {
        JsonNode doctors = search(null, " doctor ");
        assertThat(doctors).isNotEmpty();
        doctors.forEach(u -> assertThat(u.path("role").asText()).isEqualTo("DOCTOR"));
    }

    private JsonNode search(String text, String role) throws Exception {
        var request = get("/api/admin/users").header("Authorization", "Bearer " + admin);
        if (text != null) {
            request.param("search", text);
        }
        if (role != null) {
            request.param("role", role);
        }
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static List<String> names(JsonNode users) {
        List<String> names = new ArrayList<>();
        users.forEach(u -> names.add(u.path("fullName").asText()));
        return names;
    }

    private void createReceptionist(String fullName, String phone) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", fullName);
        payload.put("phoneNumber", phone);
        payload.put("password", "Temp-pass1");
        payload.put("role", "RECEPTIONIST");
        mvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    private String signIn(String phone, String fullName, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "phoneNumber", phone, "fullName", fullName, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("token").asText();
    }
}
