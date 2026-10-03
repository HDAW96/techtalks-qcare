package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.entity.User;
import com.example.repository.AdminRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.ReceptionistRepository;
import com.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin creating and editing staff accounts: each role gets its own row,
 * the user must pick their own password, and the same rules as registering apply.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUsersTest {

    private static final String SEED_PASSWORD = "changeme123";
    private static final String ADMIN_PHONE = "03400011";
    private static final String ADMIN_NAME = "Admin One";
    private static final String TEMP_PASSWORD = "Temp-pass1";
    private static final AtomicInteger NEXT_PHONE = new AtomicInteger(0);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private ReceptionistRepository receptionistRepository;
    @Autowired
    private AdminRepository adminRepository;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void onlyAnAdminMayCreateUsers() throws Exception {
        Map<String, Object> payload = staff("Hala Nasser", uniquePhone(), "RECEPTIONIST");

        mvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        String receptionist = signIn("03400009", "Reception Desk One", SEED_PASSWORD);
        send(receptionist, payload).andExpect(status().isForbidden());
    }

    @Test
    void creatingADoctorAlsoCreatesTheDoctorRow() throws Exception {
        String phone = uniquePhone();
        Map<String, Object> payload = staff("Dr. Lina Saad", phone, "DOCTOR");
        payload.put("specialization", "  Endodontics  ");

        JsonNode created = body(send(signInAsAdmin(), payload).andExpect(status().isCreated()));

        assertThat(created.path("fullName").asText()).isEqualTo("Dr. Lina Saad");
        assertThat(created.path("phoneNumber").asText()).isEqualTo(phone);
        assertThat(created.path("role").asText()).isEqualTo("DOCTOR");
        assertThat(created.toString()).doesNotContainIgnoringCase("password");

        User user = userRepository.findById(created.path("userId").asInt()).orElseThrow();
        assertThat(doctorRepository.findByUser(user)).hasValueSatisfying(
                doctor -> assertThat(doctor.getSpecialization()).isEqualTo("Endodontics"));
    }

    @Test
    void creatingAReceptionistOrAdminCreatesTheirRow() throws Exception {
        String admin = signInAsAdmin();

        JsonNode receptionist = body(send(admin, staff("Rami Khoury", uniquePhone(), "RECEPTIONIST"))
                .andExpect(status().isCreated()));
        User receptionistUser = userRepository.findById(receptionist.path("userId").asInt()).orElseThrow();
        assertThat(receptionistRepository.findByUser(receptionistUser)).isPresent();

        JsonNode newAdmin = body(send(admin, staff("Joelle Haddad", uniquePhone(), "ADMIN"))
                .andExpect(status().isCreated()));
        User adminUser = userRepository.findById(newAdmin.path("userId").asInt()).orElseThrow();
        assertThat(adminRepository.findByUser(adminUser)).isPresent();
    }

    @Test
    void theNewUserCanSignInButMustChooseTheirOwnPassword() throws Exception {
        String phone = uniquePhone();
        send(signInAsAdmin(), staff("  Omar   Itani ", phone, "doctor")).andExpect(status().isCreated());

        // The name is stored cleaned, so the user signs in with it as normally typed.
        String token = signIn(phone, "Omar Itani", TEMP_PASSWORD);
        JsonNode me = body(mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(me.path("role").asText()).isEqualTo("DOCTOR");
        assertThat(me.path("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void theSameNameAndPhoneCannotBeUsedTwice() throws Exception {
        String admin = signInAsAdmin();
        String phone = uniquePhone();
        send(admin, staff("Sara Mansour", phone, "RECEPTIONIST")).andExpect(status().isCreated());

        expectError(send(admin, staff("Sara Mansour", phone, "DOCTOR")), 409, "ACCOUNT_EXISTS");
        // A seeded account, so an admin cannot recreate an existing user either.
        expectError(send(admin, staff(ADMIN_NAME, ADMIN_PHONE, "ADMIN")), 409, "ACCOUNT_EXISTS");
    }

    @Test
    void theRegisterRulesApply() throws Exception {
        String admin = signInAsAdmin();

        expectError(send(admin, staff("   ", uniquePhone(), "DOCTOR")), 400, "NAME_REQUIRED");
        expectError(send(admin, staff("Ziad Aoun", "12345", "DOCTOR")), 400, "PHONE_INVALID");

        Map<String, Object> weak = staff("Ziad Aoun", uniquePhone(), "DOCTOR");
        weak.put("password", "changeme123");
        expectError(send(admin, weak), 400, "PASSWORD_NO_UPPERCASE");
    }

    @Test
    void onlyStaffRolesCanBeCreated() throws Exception {
        String admin = signInAsAdmin();

        expectError(send(admin, staff("Nadine Rizk", uniquePhone(), "PATIENT")), 400, "ROLE_INVALID");
        expectError(send(admin, staff("Nadine Rizk", uniquePhone(), "SURGEON")), 400, "ROLE_INVALID");
        expectError(send(admin, staff("Nadine Rizk", uniquePhone(), null)), 400, "ROLE_INVALID");
    }

    @Test
    void movingAUserToANewRoleCreatesThatRolesRow() throws Exception {
        String admin = signInAsAdmin();
        int id = body(send(admin, staff("Karim Fares", uniquePhone(), "RECEPTIONIST"))
                .andExpect(status().isCreated())).path("userId").asInt();

        JsonNode updated = body(update(admin, id, Map.of("role", " doctor "))
                .andExpect(status().isOk()));

        assertThat(updated.path("role").asText()).isEqualTo("DOCTOR");
        User user = userRepository.findById(id).orElseThrow();
        assertThat(doctorRepository.findByUser(user)).isPresent();
    }

    @Test
    void updatesFollowTheSameRules() throws Exception {
        String admin = signInAsAdmin();
        String phone = uniquePhone();
        int id = body(send(admin, staff("Maya Haddad", phone, "RECEPTIONIST"))
                .andExpect(status().isCreated())).path("userId").asInt();

        expectError(update(admin, id, Map.of("fullName", "   ")), 400, "NAME_REQUIRED");
        expectError(update(admin, id, Map.of("phoneNumber", "12345")), 400, "PHONE_INVALID");
        expectError(update(admin, id, Map.of("role", "PATIENT")), 400, "ROLE_INVALID");
        expectError(update(admin, id, Map.of("password", "changeme123")), 400, "PASSWORD_NO_UPPERCASE");
        // Taking the name and phone of another account is refused too.
        expectError(update(admin, id, Map.of("fullName", ADMIN_NAME, "phoneNumber", ADMIN_PHONE)),
                409, "ACCOUNT_EXISTS");
        expectError(update(admin, 999999, Map.of("fullName", "Nobody")), 404, "USER_NOT_FOUND");

        // A rejected update changes nothing.
        User user = userRepository.findById(id).orElseThrow();
        assertThat(user.getFullName()).isEqualTo("Maya Haddad");
        assertThat(user.getPhoneNumber()).isEqualTo(phone);
        assertThat(user.getRole()).isEqualTo("RECEPTIONIST");
    }

    @Test
    void aPasswordSetByTheAdminMustBeChangedOnSignIn() throws Exception {
        String admin = signInAsAdmin();
        String phone = uniquePhone();
        int id = body(send(admin, staff("Rana Saleh", phone, "ADMIN"))
                .andExpect(status().isCreated())).path("userId").asInt();

        update(admin, id, Map.of("password", "New-pass1")).andExpect(status().isOk());

        String token = signIn(phone, "Rana Saleh", "New-pass1");
        JsonNode me = body(mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(me.path("mustChangePassword").asBoolean()).isTrue();
    }

    private static Map<String, Object> staff(String fullName, String phone, String role) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", fullName);
        payload.put("phoneNumber", phone);
        payload.put("password", TEMP_PASSWORD);
        payload.put("role", role);
        return payload;
    }

    private ResultActions send(String token, Map<String, ?> payload) throws Exception {
        return mvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)));
    }

    private ResultActions update(String token, int id, Map<String, ?> payload) throws Exception {
        return mvc.perform(put("/api/admin/users/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)));
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
        return String.format("0376%04d", NEXT_PHONE.incrementAndGet());
    }

    private String signInAsAdmin() throws Exception {
        return signIn(ADMIN_PHONE, ADMIN_NAME, SEED_PASSWORD);
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
