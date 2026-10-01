package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin's services API, and that a deactivated service really cannot be
 * chosen for anything new.
 *
 * Every test makes its own services and changes only those. All test classes
 * share one database and other tests take the first seeded service, so
 * deactivating or renaming a seeded one would break them depending on the
 * order the classes run in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ManageServicesTest {

    private static final String SEED_PASSWORD = "changeme123";
    private static final String ADMIN_PHONE = "03400011";
    private static final String ADMIN_NAME = "Admin One";
    private static final AtomicInteger NEXT_PHONE = new AtomicInteger(0);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MedicalServiceRepository serviceRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void onlyAnAdminMayUseIt() throws Exception {
        mvc.perform(get("/api/admin/services")).andExpect(status().isUnauthorized());

        String patient = registerAndSignIn("Nour Khalil", uniquePhone());
        mvc.perform(get("/api/admin/services").header("Authorization", "Bearer " + patient))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCreatesAService() throws Exception {
        String admin = signInAsAdmin();
        String name = uniqueName("Whitening");

        JsonNode created = body(send(post("/api/admin/services"), admin,
                Map.of("name", "  " + name + "  ", "minutes", 25)).andExpect(status().isCreated()));

        assertThat(created.path("id").asInt()).isPositive();
        assertThat(created.path("name").asText()).isEqualTo(name);
        assertThat(created.path("minutes").asInt()).isEqualTo(25);
        assertThat(created.path("active").asBoolean()).isTrue();
        assertThat(created.path("inUse").asBoolean()).isFalse();

        JsonNode list = body(mvc.perform(get("/api/admin/services")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assertThat(containsId(list, created.path("id").asInt())).isTrue();
    }

    @Test
    void theDurationMustBeFromOneToEightHours() throws Exception {
        String admin = signInAsAdmin();
        for (int minutes : new int[] {0, -5, 481}) {
            expectError(send(post("/api/admin/services"), admin,
                    Map.of("name", uniqueName("Bad duration"), "minutes", minutes)),
                    400, "DURATION_INVALID");
        }
        Map<String, Object> noMinutes = new HashMap<>();
        noMinutes.put("name", uniqueName("No duration"));
        expectError(send(post("/api/admin/services"), admin, noMinutes), 400, "DURATION_INVALID");

        int id = createService(admin, uniqueName("Edit duration"), 30);
        expectError(send(patch("/api/admin/services/" + id), admin, Map.of("minutes", 0)),
                400, "DURATION_INVALID");
    }

    @Test
    void theNameIsRequiredAndAtMostOneHundredCharacters() throws Exception {
        String admin = signInAsAdmin();
        expectError(send(post("/api/admin/services"), admin, Map.of("name", "   ", "minutes", 20)),
                400, "NAME_REQUIRED");
        expectError(send(post("/api/admin/services"), admin, Map.of("name", "x".repeat(101), "minutes", 20)),
                400, "NAME_TOO_LONG");

        // Exactly the limit is still fine.
        String stamp = String.valueOf(System.nanoTime());
        String longest = stamp + "y".repeat(100 - stamp.length());
        JsonNode created = body(send(post("/api/admin/services"), admin,
                Map.of("name", longest, "minutes", 20)).andExpect(status().isCreated()));
        assertThat(created.path("name").asText()).hasSize(100);
    }

    @Test
    void twoServicesCannotShareANameWhateverTheCase() throws Exception {
        String admin = signInAsAdmin();
        String name = uniqueName("Root canal");
        int id = createService(admin, name, 60);

        expectError(send(post("/api/admin/services"), admin,
                Map.of("name", name.toUpperCase(), "minutes", 60)), 409, "SERVICE_EXISTS");

        int other = createService(admin, uniqueName("Crown"), 50);
        expectError(send(patch("/api/admin/services/" + other), admin,
                Map.of("name", name.toLowerCase())), 409, "SERVICE_EXISTS");

        // Renaming a service to its own name is not a clash with itself.
        JsonNode same = body(send(patch("/api/admin/services/" + id), admin,
                Map.of("name", name, "minutes", 70)).andExpect(status().isOk()));
        assertThat(same.path("name").asText()).isEqualTo(name);
        assertThat(same.path("minutes").asInt()).isEqualTo(70);
    }

    @Test
    void anEditLeavesFieldsThatWereNotSentAlone() throws Exception {
        String admin = signInAsAdmin();
        String name = uniqueName("Polish");
        int id = createService(admin, name, 15);

        JsonNode edited = body(send(patch("/api/admin/services/" + id), admin,
                Map.of("minutes", 35)).andExpect(status().isOk()));
        assertThat(edited.path("name").asText()).isEqualTo(name);
        assertThat(edited.path("minutes").asInt()).isEqualTo(35);
        assertThat(edited.path("active").asBoolean()).isTrue();
    }

    @Test
    void aDeactivatedServiceCannotBeChosenForAnythingNew() throws Exception {
        String admin = signInAsAdmin();
        int id = createService(admin, uniqueName("Implant"), 30);
        assertThat(containsId(publicServices(), id)).isTrue();

        JsonNode off = body(send(patch("/api/admin/services/" + id), admin,
                Map.of("active", false)).andExpect(status().isOk()));
        assertThat(off.path("active").asBoolean()).isFalse();
        assertThat(containsId(publicServices(), id)).isFalse();

        // Still on the admin's own list, so it can be turned back on.
        JsonNode adminList = body(mvc.perform(get("/api/admin/services")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assertThat(containsId(adminList, id)).isTrue();

        Integer doctorId = doctorRepository.findAll().get(0).getDoctorId();
        expectError(mvc.perform(get("/api/doctors/" + doctorId + "/availability")
                .param("serviceId", String.valueOf(id))), 409, "SERVICE_INACTIVE");

        String patient = registerAndSignIn("Karim Saab", uniquePhone());
        expectError(send(post("/api/appointments"), patient, Map.of(
                "doctorId", doctorId, "serviceId", id,
                "datetime", LocalDate.now().plusDays(7).atTime(10, 0).toString())),
                409, "SERVICE_INACTIVE");

        send(patch("/api/admin/services/" + id), admin, Map.of("active", true))
                .andExpect(status().isOk());
        assertThat(containsId(publicServices(), id)).isTrue();
    }

    @Test
    void anUnusedServiceCanBeDeleted() throws Exception {
        String admin = signInAsAdmin();
        int id = createService(admin, uniqueName("Unused"), 20);

        mvc.perform(delete("/api/admin/services/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(serviceRepository.findById(id)).isEmpty();

        expectError(mvc.perform(delete("/api/admin/services/" + id)
                .header("Authorization", "Bearer " + admin)), 404, "SERVICE_NOT_FOUND");
        expectError(send(patch("/api/admin/services/" + id), admin, Map.of("minutes", 20)),
                404, "SERVICE_NOT_FOUND");
    }

    @Test
    void aServiceWithAppointmentsCannotBeDeleted() throws Exception {
        String admin = signInAsAdmin();
        int id = createService(admin, uniqueName("Extraction"), 30);
        MedicalService service = serviceRepository.findById(id).orElseThrow();
        Doctor doctor = doctorRepository.findAll().get(0);
        Patient patient = patientRepository.save(new Patient("Maya Fares", uniquePhone()));
        appointmentRepository.save(new Appointment(patient, doctor,
                LocalDate.now().minusDays(3).atTime(LocalTime.of(9, 0)), service));

        expectError(mvc.perform(delete("/api/admin/services/" + id)
                .header("Authorization", "Bearer " + admin)), 409, "SERVICE_IN_USE");
        assertThat(serviceRepository.findById(id)).isPresent();

        JsonNode list = body(mvc.perform(get("/api/admin/services")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        for (JsonNode row : list) {
            if (row.path("id").asInt() == id) {
                assertThat(row.path("inUse").asBoolean()).isTrue();
            }
        }
    }

    private int createService(String admin, String name, int minutes) throws Exception {
        return body(send(post("/api/admin/services"), admin, Map.of("name", name, "minutes", minutes))
                .andExpect(status().isCreated())).path("id").asInt();
    }

    private JsonNode publicServices() throws Exception {
        return body(mvc.perform(get("/api/services")).andExpect(status().isOk()));
    }

    private static boolean containsId(JsonNode list, int id) {
        for (JsonNode row : list) {
            if (row.path("id").asInt() == id) {
                return true;
            }
        }
        return false;
    }

    private ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token, Map<String, ?> payload) throws Exception {
        return mvc.perform(request
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

    private static String uniqueName(String base) {
        return base + " " + System.nanoTime();
    }

    /** A valid Lebanese mobile number in a range no other test uses. */
    private static String uniquePhone() {
        return String.format("0379%04d", NEXT_PHONE.incrementAndGet());
    }

    private String signInAsAdmin() throws Exception {
        return signIn(ADMIN_PHONE, ADMIN_NAME, SEED_PASSWORD);
    }

    private String registerAndSignIn(String fullName, String phone) throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "fullName", fullName, "phoneNumber", phone,
                        "password", "A-long-password1"))))
                .andExpect(status().isCreated());
        return signIn(phone, fullName, "A-long-password1");
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
