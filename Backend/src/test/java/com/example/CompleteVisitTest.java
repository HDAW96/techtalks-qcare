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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Finishing a visit.
 *
 * The case worth protecting is the one that was broken: completing a visit has
 * to free the chair. It used to look only for a queue ticket linked to the
 * appointment, and nothing in the product ever creates that link - checking a
 * booked patient in at the desk is not built - so every ticket has a null
 * appointment_id, the lookup always missed, and the patient stayed "in the
 * chair" on both the desk board and the doctor's screen after the doctor had
 * finished with them.
 */
// This class opens a queue and puts a patient in the chair, like the other
// queue tests. A fresh context afterwards keeps that out of the next class.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
class CompleteVisitTest {

    private static final String SEED_PASSWORD = "changeme123";

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
    void completingAVisitFreesTheChair() throws Exception {
        Patient patient = patientRepository.save(new Patient("Rami Daher", "03880011"));
        LocalDate today = LocalDate.now();

        Appointment appointment = appointmentRepository.save(
                new Appointment(patient, doctor, today.atTime(LocalTime.of(10, 0)), service));

        // The desk called them in. Note the ticket is NOT linked to the
        // appointment - nothing in the product makes that link, which is
        // exactly why the original fix did not work.
        Queue queue = queueRepository.save(new Queue(doctor, null, today));
        QueueTicket ticket = new QueueTicket(queue, patient, service, 3);
        ticket.setStatus("IN_CHAIR");
        LocalDateTime calledInAt = LocalDateTime.now();
        ticket.setStartedAt(calledInAt);
        ticketRepository.save(ticket);
        assertThat(ticket.getAppointment()).isNull();

        String token = signInAsDoctor();
        JsonNode done = complete(appointment.getAppointmentId(), token,
                "{\"clinicalNotes\":\"Upper left filling replaced.\"}", 200);
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");

        // The chair is free again, on the desk board and on the doctor's own
        // screen.
        QueueTicket after = ticketRepository.findById(ticket.getTicketId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("DONE");
        // When they were called in is still on record: a finished visit is
        // when that is worth keeping.
        assertThat(after.getStartedAt()).isNotNull();

        JsonNode schedule = json.readTree(mvc.perform(get("/api/doctors/me/schedule")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(schedule.path("nowServing").isNull()).isTrue();
    }

    @Test
    void aBookedPatientWhoNeverQueuedStillCompletes() throws Exception {
        // No queue, no ticket at all. Completing must not fail for want of a
        // chair to free.
        Patient patient = patientRepository.save(new Patient("Lara Zein", "03880022"));
        Appointment appointment = appointmentRepository.save(new Appointment(
                patient, doctor, LocalDate.now().atTime(LocalTime.of(15, 30)), service));

        JsonNode done = complete(appointment.getAppointmentId(), signInAsDoctor(),
                "{\"clinicalNotes\":null}", 200);
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void aRequestWithNoBodyIsRefusedAsBadRequestNotAsAServerError() throws Exception {
        Patient patient = patientRepository.save(new Patient("Ziad Noun", "03880033"));
        Appointment appointment = appointmentRepository.save(new Appointment(
                patient, doctor, LocalDate.now().atTime(LocalTime.of(16, 0)), service));

        mvc.perform(post("/api/appointments/" + appointment.getAppointmentId() + "/complete")
                .header("Authorization", "Bearer " + signInAsDoctor())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheDoctorWhoOwnsTheAppointmentMayCompleteIt() throws Exception {
        Patient patient = patientRepository.save(new Patient("Hadi Salem", "03880044"));
        Appointment appointment = appointmentRepository.save(new Appointment(
                patient, doctor, LocalDate.now().atTime(LocalTime.of(17, 0)), service));
        int id = appointment.getAppointmentId();
        String body = "{\"clinicalNotes\":\"x\"}";

        mvc.perform(post("/api/appointments/" + id + "/complete")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        String patientToken = registerAndSignIn("Rita Aoun", "03880055");
        complete(id, patientToken, body, 403);

        String desk = signIn("03400009", "Reception Desk One", SEED_PASSWORD);
        complete(id, desk, body, 403);

        // Another doctor cannot close someone else's appointment.
        Doctor other = doctorRepository.findAll().get(1);
        String otherToken = signIn(other.getUser().getPhoneNumber(),
                other.getUser().getFullName(), SEED_PASSWORD);
        complete(id, otherToken, body, 403);

        // And a visit cannot be completed twice.
        complete(id, signInAsDoctor(), body, 200);
        complete(id, signInAsDoctor(), body, 409);
    }

    // ---- helpers ----

    private JsonNode complete(int appointmentId, String token, String body, int expected)
            throws Exception {
        String response = mvc.perform(post("/api/appointments/" + appointmentId + "/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String signInAsDoctor() throws Exception {
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
