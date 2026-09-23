package com.example;

import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.PatientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A doctor reads a patient's finished treatments; nobody else can.
 *
 * Uses its own freshly registered patient, never a seeded account, because
 * other test classes share this database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PatientHistoryTest {

    private static final String PHONE = "03995555";
    private static final String NAME = "History Patient";
    private static final String PASSWORD = "History-password1";
    private static final String SEED_PASSWORD = "changeme123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private MedicalServiceRepository serviceRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aDoctorSeesOnlyFinishedTreatmentsNewestFirstAndNobodyElseCan() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", NAME, "phoneNumber", PHONE, "password", PASSWORD))))
                .andExpect(status().isCreated());
        Patient patient = patientRepository.findByPhoneNumberAndFullName(PHONE, NAME).orElseThrow();

        // There is no "complete treatment" endpoint yet (SCRUM-74), so the
        // finished visits are written straight to the database.
        Doctor doctor = doctorRepository.findAll().get(0);
        List<MedicalService> services = serviceRepository.findAll();
        save(patient, doctor, services.get(0), LocalDateTime.of(2026, 3, 2, 9, 0), "COMPLETED");
        save(patient, doctor, services.get(1), LocalDateTime.of(2026, 6, 1, 10, 0), "COMPLETED");
        save(patient, doctor, services.get(0), LocalDateTime.of(2026, 5, 4, 11, 0), "CANCELLED");
        save(patient, doctor, services.get(0), LocalDateTime.of(2027, 1, 4, 9, 0), "BOOKED");

        String url = "/api/doctors/patients/" + patient.getPatientId() + "/history";
        String doctorToken = token("03400001", "Dr. Rania Haddad", SEED_PASSWORD);

        mvc.perform(get(url).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value(NAME))
                .andExpect(jsonPath("$.visits.length()").value(2))
                .andExpect(jsonPath("$.visits[0].datetime").value("2026-06-01T10:00"))
                .andExpect(jsonPath("$.visits[0].service").value(services.get(1).getServiceName()))
                .andExpect(jsonPath("$.visits[0].doctor").value(doctor.getUser().getFullName()))
                .andExpect(jsonPath("$.visits[1].datetime").value("2026-03-02T09:00"));

        mvc.perform(get("/api/doctors/patients/999999/history")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PATIENT_NOT_FOUND"));

        // The patient themselves and the front desk are both refused.
        mvc.perform(get(url).header("Authorization", "Bearer " + token(PHONE, NAME, PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization",
                        "Bearer " + token("03400009", "Reception Desk One", SEED_PASSWORD)))
                .andExpect(status().isForbidden());
        // /api/doctors/** is otherwise public; the history must not be.
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    private void save(Patient patient, Doctor doctor, MedicalService service,
            LocalDateTime when, String status) {
        Appointment appointment = new Appointment(patient, doctor, when, service);
        appointment.setStatus(status);
        appointmentRepository.save(appointment);
    }

    private String token(String phone, String name, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of(
                                        "phoneNumber", phone, "fullName", name, "password", password))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("token").asText();
    }
}
