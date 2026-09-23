package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.entity.Appointment;
import com.example.entity.Doctor;
import com.example.entity.MedicalService;
import com.example.entity.Patient;
import com.example.entity.Queue;
import com.example.entity.QueueTicket;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.PatientRepository;
import com.example.repository.QueueRepository;
import com.example.repository.QueueTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A doctor's own day, and who may read it.
 *
 * The rule worth protecting is the one that is easy to get wrong: every
 * GET /api/doctors/** is public so the booking screen can list doctors before
 * sign-in, so this endpoint is only private because a narrower rule sits above
 * that one in SecurityConfig. A test that only checked the happy path would
 * pass just as well with every patient's name exposed to the internet.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DoctorScheduleTest {

    private static final String SEED_PASSWORD = "changeme123";
    private static final String URL = "/api/doctors/me/schedule";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private MedicalServiceRepository serviceRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private QueueRepository queueRepository;
    @Autowired
    private QueueTicketRepository ticketRepository;

    private final ObjectMapper json = new ObjectMapper();

    private Doctor doctor;
    private MedicalService service;

    @BeforeEach
    void seedIsPresent() {
        doctor = doctorRepository.findAll().get(0);
        service = serviceRepository.findAll().get(0);
    }

    @Test
    void theScheduleIsForTheSignedInDoctorOnlyAndIsNotPublic() throws Exception {
        // The endpoint sits under the public /api/doctors/** path.
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());

        String patient = registerAndSignIn("Rita Aoun", "03660011");
        mvc.perform(get(URL).header("Authorization", "Bearer " + patient))
                .andExpect(status().isForbidden());

        String desk = signIn("03400009", "Reception Desk One", SEED_PASSWORD);
        mvc.perform(get(URL).header("Authorization", "Bearer " + desk))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDoctorSeesTodaysAppointmentsAndWhoIsInTheirChair() throws Exception {
        Patient booked = patientRepository.save(new Patient("Maya Chahine", "03660022"));
        Patient walkIn = patientRepository.save(new Patient("Omar Saad", "03660033"));

        LocalDate today = LocalDate.now();
        // Two today, one tomorrow - the third must not appear.
        appointmentRepository.save(
                new Appointment(booked, doctor, today.atTime(LocalTime.of(11, 0)), service));
        appointmentRepository.save(
                new Appointment(booked, doctor, today.atTime(LocalTime.of(9, 30)), service));
        appointmentRepository.save(new Appointment(booked, doctor,
                today.plusDays(1).atTime(LocalTime.of(10, 0)), service));

        // The desk has called a walk-in into this doctor's chair.
        Queue queue = queueRepository.save(new Queue(doctor, null, today));
        QueueTicket ticket = new QueueTicket(queue, walkIn, service, 7);
        ticket.setStatus("IN_CHAIR");
        ticket.setStartedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        JsonNode body = read(signInAsThisDoctor());

        assertThat(body.path("doctorId").asInt()).isEqualTo(doctor.getDoctorId());
        assertThat(body.path("date").asText()).isEqualTo(today.toString());

        // Today only, earliest first.
        JsonNode appointments = body.path("appointments");
        assertThat(appointments).hasSize(2);
        assertThat(appointments.get(0).path("datetime").asText()).endsWith("T09:30");
        assertThat(appointments.get(1).path("datetime").asText()).endsWith("T11:00");

        // Every row carries the patient, so the history panel can be opened
        // from it (SCRUM-72).
        assertThat(appointments.get(0).path("patientId").asInt())
                .isEqualTo(booked.getPatientId());
        assertThat(appointments.get(0).path("patientName").asText()).isEqualTo("Maya Chahine");

        JsonNode now = body.path("nowServing");
        assertThat(now.isNull()).isFalse();
        assertThat(now.path("ticket").asText()).isEqualTo("R-7");
        assertThat(now.path("patientId").asInt()).isEqualTo(walkIn.getPatientId());
        assertThat(now.path("patientName").asText()).isEqualTo("Omar Saad");
        assertThat(now.path("startedAt").isNull()).isFalse();
    }

    @Test
    void anEmptyChairIsNullRatherThanAnError() throws Exception {
        // This doctor has no queue open at all today.
        Doctor other = doctorRepository.findAll().get(1);
        String token = signIn(other.getUser().getPhoneNumber(), other.getUser().getFullName(),
                SEED_PASSWORD);

        JsonNode body = read(token);
        assertThat(body.path("nowServing").isNull()).isTrue();
        assertThat(body.path("doctorId").asInt()).isEqualTo(other.getDoctorId());
    }

    // ---- helpers ----

    private JsonNode read(String token) throws Exception {
        String response = mvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String signInAsThisDoctor() throws Exception {
        return signIn(doctor.getUser().getPhoneNumber(), doctor.getUser().getFullName(),
                SEED_PASSWORD);
    }

    private String registerAndSignIn(String fullName, String phone) throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "fullName", fullName, "phoneNumber", phone,
                        "password", "A-long-password1"))))
                .andExpect(status().isCreated());
        return signIn(phone, fullName, "A-long-password1");
    }

    private String signIn(String phone, String fullName, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                        "phoneNumber", phone, "fullName", fullName, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("token").asText();
    }
}
