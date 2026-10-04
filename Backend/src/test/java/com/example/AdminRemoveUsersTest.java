package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.entity.User;
import com.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin removing staff accounts, and the rules that keep the clinic from
 * locking itself out: an admin cannot remove themselves, and the last admin
 * can neither be removed nor moved to another role.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminRemoveUsersTest {

    private static final String SEED_PASSWORD = "changeme123";
    private static final String ADMIN_PHONE = "03400011";
    private static final String ADMIN_NAME = "Admin One";
    private static final String TEMP_PASSWORD = "Temp-pass1";
    private static final AtomicInteger NEXT_PHONE = new AtomicInteger(0);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void onlyAnAdminMayRemoveUsers() throws Exception {
        int id = create(signInAsAdmin(), "Nour Haidar", uniquePhone(), "RECEPTIONIST");

        mvc.perform(delete("/api/admin/users/" + id)).andExpect(status().isUnauthorized());

        String receptionist = signIn("03400009", "Reception Desk One", SEED_PASSWORD);
        remove(receptionist, id).andExpect(status().isForbidden());
        assertThat(userRepository.findById(id).orElseThrow().getActive()).isTrue();
    }

    @Test
    void aRemovedUserIsSignedOutAndCannotSignInAgain() throws Exception {
        String admin = signInAsAdmin();
        String phone = uniquePhone();
        int id = create(admin, "Omar Khalil", phone, "RECEPTIONIST");
        String token = signIn(phone, "Omar Khalil", TEMP_PASSWORD);

        remove(admin, id).andExpect(status().isNoContent());

        // The account is kept, only switched off, so its history stays intact.
        assertThat(userRepository.findById(id).orElseThrow().getActive()).isFalse();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        expectError(login(phone, "Omar Khalil", TEMP_PASSWORD), 401, "ACCOUNT_DEACTIVATED");
    }

    @Test
    void aWrongPasswordDoesNotRevealThatAnAccountWasRemoved() throws Exception {
        String admin = signInAsAdmin();
        String phone = uniquePhone();
        int id = create(admin, "Maya Karam", phone, "DOCTOR");
        remove(admin, id).andExpect(status().isNoContent());

        expectError(login(phone, "Maya Karam", "Not-the-pass1"), 401, "BAD_CREDENTIALS");
    }

    @Test
    void theListShowsWhichUsersWereRemoved() throws Exception {
        String admin = signInAsAdmin();
        String phone = uniquePhone();
        int kept = create(admin, "Rami Daher", phone, "RECEPTIONIST");
        int removed = create(admin, "Rami Daher Two", phone, "RECEPTIONIST");
        remove(admin, removed).andExpect(status().isNoContent());

        JsonNode list = body(mvc.perform(get("/api/admin/users").param("search", "Rami Daher")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));

        Map<Integer, Boolean> active = new HashMap<>();
        list.forEach(user -> active.put(user.path("userId").asInt(), user.path("active").asBoolean()));
        assertThat(active).containsEntry(kept, true).containsEntry(removed, false);
    }

    @Test
    void anAdminCannotRemoveThemselves() throws Exception {
        String admin = signInAsAdmin();
        int self = userRepository.findByPhoneNumberAndFullName(ADMIN_PHONE, ADMIN_NAME)
                .orElseThrow().getUserId();

        expectError(remove(admin, self), 400, "CANNOT_DEACTIVATE_SELF");
        assertThat(userRepository.findById(self).orElseThrow().getActive()).isTrue();
    }

    @Test
    void aUserCanOnlyBeRemovedOnce() throws Exception {
        String admin = signInAsAdmin();
        int id = create(admin, "Lara Mansour", uniquePhone(), "RECEPTIONIST");
        remove(admin, id).andExpect(status().isNoContent());

        expectError(remove(admin, id), 400, "USER_ALREADY_DEACTIVATED");
        expectError(remove(admin, 999_999), 404, "USER_NOT_FOUND");
    }

    @Test
    void anotherAdminCanBeRemovedWhileOneRemains() throws Exception {
        String admin = signInAsAdmin();
        int other = create(admin, "Sami Aoun", uniquePhone(), "ADMIN");

        remove(admin, other).andExpect(status().isNoContent());
    }

    @Test
    void theLastAdminCannotBeMovedToAnotherRole() throws Exception {
        String admin = signInAsAdmin();
        User self = userRepository.findByPhoneNumberAndFullName(ADMIN_PHONE, ADMIN_NAME).orElseThrow();
        leaveOnlyThisAdminActive(self);

        expectError(update(admin, self.getUserId(), Map.of("role", "DOCTOR")), 400, "LAST_ADMIN");

        assertThat(userRepository.findById(self.getUserId()).orElseThrow().getRole()).isEqualTo("ADMIN");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminCanBeMovedToAnotherRoleWhileOneRemains() throws Exception {
        String admin = signInAsAdmin();
        User self = userRepository.findByPhoneNumberAndFullName(ADMIN_PHONE, ADMIN_NAME).orElseThrow();
        leaveOnlyThisAdminActive(self);
        int other = create(admin, "Hiba Salloum", uniquePhone(), "ADMIN");

        update(admin, other, Map.of("role", "RECEPTIONIST")).andExpect(status().isOk());
    }

    /**
     * Other tests share this database and add admins of their own, so the
     * last-admin rule is only tested once every other admin is switched off.
     */
    private void leaveOnlyThisAdminActive(User keep) {
        List<User> others = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equals(u.getRole()) && !u.getUserId().equals(keep.getUserId()))
                .toList();
        others.forEach(u -> u.setActive(false));
        userRepository.saveAll(others);
    }

    private int create(String token, String fullName, String phone, String role) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", fullName);
        payload.put("phoneNumber", phone);
        payload.put("password", TEMP_PASSWORD);
        payload.put("role", role);
        return body(mvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())).path("userId").asInt();
    }

    private ResultActions remove(String token, int id) throws Exception {
        return mvc.perform(delete("/api/admin/users/" + id).header("Authorization", "Bearer " + token));
    }

    private ResultActions update(String token, int id, Map<String, ?> payload) throws Exception {
        return mvc.perform(put("/api/admin/users/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)));
    }

    private ResultActions login(String phone, String fullName, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "phoneNumber", phone, "fullName", fullName, "password", password))));
    }

    private void expectError(ResultActions result, int httpStatus, String code) throws Exception {
        result.andExpect(status().is(httpStatus));
        assertThat(body(result).path("error").asText()).isEqualTo(code);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** A valid Lebanese mobile number in a range no other test uses. */
    private static String uniquePhone() {
        return String.format("0377%04d", NEXT_PHONE.incrementAndGet());
    }

    private String signInAsAdmin() throws Exception {
        return signIn(ADMIN_PHONE, ADMIN_NAME, SEED_PASSWORD);
    }

    private String signIn(String phone, String fullName, String password) throws Exception {
        String response = login(phone, fullName, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("token").asText();
    }
}
