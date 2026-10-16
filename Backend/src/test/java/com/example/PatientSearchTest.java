package com.example;

import com.example.entity.Patient;
import com.example.repository.PatientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PatientSearchTest {

    private static final String SEED_PASSWORD = "changeme123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PatientRepository patientRepository;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void phoneVariantsAndNameSearchFindPatients() throws Exception {
        savePatient("SCRUM96 Phone Match", "03400001");
        savePatient("SCRUM96 Name Match", "03790011");
        String receptionist = signIn("03400009", "Reception Desk One", SEED_PASSWORD);

        for (String typed : List.of("03400001", "03 400 001", "+961 3 400 001")) {
            assertThat(names(search(receptionist, typed)))
                    .as(typed)
                    .contains("SCRUM96 Phone Match");
        }
        assertThat(names(search(receptionist, "name match")))
                .contains("SCRUM96 Name Match");
    }

    @Test
    void percentAndUnderscoreAreLiteralAndResultsAreNameOrdered() throws Exception {
        savePatient("SCRUM96 % Literal", "03790012");
        savePatient("SCRUM96 _ Literal", "03790013");
        savePatient("SCRUM96 Order Zebra", "03790014");
        savePatient("SCRUM96 Order Alpha", "03790015");
        savePatient("SCRUM96 Order Maya", "03790016");
        String receptionist = signIn("03400009", "Reception Desk One", SEED_PASSWORD);

        assertThat(names(search(receptionist, "%"))).containsExactly("SCRUM96 % Literal");
        assertThat(names(search(receptionist, "_"))).containsExactly("SCRUM96 _ Literal");
        assertThat(names(search(receptionist, "SCRUM96 Order")))
                .containsExactly("SCRUM96 Order Alpha", "SCRUM96 Order Maya", "SCRUM96 Order Zebra");

        List<String> allNames = names(search(receptionist, null));
        List<String> sortedNames = new ArrayList<>(allNames);
        sortedNames.sort(String.CASE_INSENSITIVE_ORDER);
        assertThat(allNames).isEqualTo(sortedNames);
    }

    @Test
    void onlyReceptionistsAndAdminsMaySearchPatients() throws Exception {
        String receptionist = signIn("03400009", "Reception Desk One", SEED_PASSWORD);
        String admin = signIn("03400011", "Admin One", SEED_PASSWORD);
        String doctor = signIn("03400001", "Dr. Rania Haddad", SEED_PASSWORD);

        mvc.perform(get("/api/patients").header("Authorization", "Bearer " + receptionist))
                .andExpect(status().isOk());
        mvc.perform(get("/api/patients").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/patients").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "phoneNumber", "03790017",
                                "fullName", "SCRUM96 Patient Role",
                                "password", "Patient-pass1"))))
                .andExpect(status().isCreated());
        String patient = signIn("03790017", "SCRUM96 Patient Role", "Patient-pass1");
        mvc.perform(get("/api/patients").header("Authorization", "Bearer " + patient))
                .andExpect(status().isForbidden());
    }

    private void savePatient(String name, String phone) {
        patientRepository.saveAndFlush(new Patient(name, phone));
    }

    private JsonNode search(String token, String text) throws Exception {
        var request = get("/api/patients").header("Authorization", "Bearer " + token);
        if (text != null) {
            request.param("search", text);
        }
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static List<String> names(JsonNode patients) {
        List<String> names = new ArrayList<>();
        patients.forEach(patient -> names.add(patient.path("fullName").asText()));
        return names;
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
    
     private String desk() throws Exception {
        return signIn("03400009", "Reception Desk One", SEED_PASSWORD);
    }
     
    @Test
     void blankSearchReturnsEmptyList() throws Exception {
       String token = desk();
        assertThat(search(token, null)).isEmpty();
        assertThat(search(token, "")).isEmpty();
        assertThat(search(token, "   ")).isEmpty();
      }
     
    @Test
    void trimmedSearchQueryFindsPatientsCorrectly() throws Exception {
    savePatient("SCRUM96 Trim Match", "03790020");
    String receptionist = signIn("03400009", "Reception Desk One", SEED_PASSWORD);

    for (String paddedQuery : List.of("  SCRUM96 Trim Match  ", "\tSCRUM96 Trim Match\t", " SCRUM96 Trim Match ")) {
        assertThat(names(search(receptionist, paddedQuery)))
                .as("Searching with padded query: '%s'", paddedQuery)
                .contains("SCRUM96 Trim Match");
    }
   }

}
