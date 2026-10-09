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
 * The visit in a doctor's chair (BE-2).
 *
 * "Who is in the chair" is a visit - a queue ticket - and not a patient: a
 * checked-in booking and a walk-in are both visits, and one patient may hold
 * two bookings today. GET /api/doctors/me/current-visit names the first
 * visit called in, with the appointment id when it came from a booking.
 */
// Opens a queue and puts patients in the chair, like the other queue tests. A
// fresh context afterwards keeps that out of the next class.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
class CurrentVisitTest {

    private static final String SEED_PASSWORD = "changeme123";
    private static final String URL = "/api/doctors/me/schedule";
    private static final String CURRENT_URL = "/api/doctors/me/current-visit";

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
    @Autowired
    private com.example.service.QueueService queueService;

    private final ObjectMapper json = new ObjectMapper();

    private Doctor doctor;
    private MedicalService service;

    @BeforeEach
    void seedIsPresent() {
        // Not the first doctor, so a hardcoded ticket prefix would show.
        doctor = doctorRepository.findAll().get(1);
        service = serviceRepository.findAll().get(0);
    }

    @Test
    void theCurrentVisitIsTheCheckedInBookingInTheChairWithItsAppointmentId() throws Exception {
        emptyTheChair();
        Patient patient = patientRepository.save(new Patient("Nada Fakhoury", "03660044"));
        Appointment morning = appointmentRepository.save(new Appointment(patient, doctor,
                LocalDate.now().atTime(LocalTime.of(9, 0)), service));
        Appointment afternoon = appointmentRepository.save(new Appointment(patient, doctor,
                LocalDate.now().atTime(LocalTime.of(15, 0)), service));
        QueueTicket ticket = callIn(patient, afternoon);

        JsonNode current = currentVisit(signInAsThisDoctor());

        // The visit is identified by its ticket and its own booking, not by
        // the patient, who has two bookings today.
        assertThat(current.path("ticketId").asInt()).isEqualTo(ticket.getTicketId());
        assertThat(current.path("appointmentId").asInt()).isEqualTo(afternoon.getAppointmentId());
        assertThat(current.path("appointmentId").asInt()).isNotEqualTo(morning.getAppointmentId());
        assertThat(current.path("walkIn").asBoolean()).isFalse();
        assertThat(current.path("patientId").asInt()).isEqualTo(patient.getPatientId());
        assertThat(current.path("ticket").asText())
                .isEqualTo(queueService.ticketPrefixFor(doctor) + "-" + ticket.getTicketNumber());
    }

    @Test
    void theCurrentVisitIsAWalkInWhenThatIsWhoIsInTheChair() throws Exception {
        emptyTheChair();
        Patient walkIn = patientRepository.save(new Patient("Georges Daou", "03660055"));
        QueueTicket ticket = callIn(walkIn, null);

        JsonNode current = currentVisit(signInAsThisDoctor());

        assertThat(current.path("ticketId").asInt()).isEqualTo(ticket.getTicketId());
        assertThat(current.path("appointmentId").isNull()).isTrue();
        assertThat(current.path("walkIn").asBoolean()).isTrue();
    }

    @Test
    void onlyTheFirstCalledInVisitIsCurrentAndWaitingTicketsAreNot() throws Exception {
        emptyTheChair();
        Patient first = patientRepository.save(new Patient("Hala Itani", "03660066"));
        Patient second = patientRepository.save(new Patient("Ibrahim Sleiman", "03660077"));
        Patient waiting = patientRepository.save(new Patient("Jad Mouawad", "03660088"));

        QueueTicket later = callIn(second, null);
        later.setStartedAt(LocalDateTime.now());
        ticketRepository.save(later);
        QueueTicket earlier = callIn(first, null);
        earlier.setStartedAt(LocalDateTime.now().minusMinutes(20));
        ticketRepository.save(earlier);
        QueueTicket queued = callIn(waiting, null);
        queued.setStatus("WAITING");
        queued.setStartedAt(null);
        ticketRepository.save(queued);

        JsonNode current = currentVisit(signInAsThisDoctor());

        assertThat(current.path("ticketId").asInt()).isEqualTo(earlier.getTicketId());
        // The schedule's nowServing names the same visit.
        assertThat(read(signInAsThisDoctor()).path("nowServing").path("ticketId").asInt())
                .isEqualTo(earlier.getTicketId());
    }

    @Test
    void anEmptyChairHasNoCurrentVisitAndIsNotAnError() throws Exception {
        emptyTheChair();
        Patient waiting = patientRepository.save(new Patient("Karen Bou Saab", "03660099"));
        QueueTicket queued = callIn(waiting, null);
        queued.setStatus("WAITING");
        queued.setStartedAt(null);
        ticketRepository.save(queued);

        // A waiting ticket is not a visit in the chair.
        mvc.perform(get(CURRENT_URL)
                .header("Authorization", "Bearer " + signInAsThisDoctor()))
                .andExpect(status().isNoContent());

        // Nor is a doctor with no queue open at all.
        Doctor other = doctorRepository.findAll().get(0);
        mvc.perform(get(CURRENT_URL).header("Authorization", "Bearer "
                + signIn(other.getUser().getPhoneNumber(), other.getUser().getFullName(),
                        SEED_PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    void theCurrentVisitIsForDoctorsOnlyAndIsNotPublic() throws Exception {
        mvc.perform(get(CURRENT_URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(CURRENT_URL).header("Authorization",
                "Bearer " + registerAndSignIn("Lina Haddad", "03660100")))
                .andExpect(status().isForbidden());
        mvc.perform(get(CURRENT_URL).header("Authorization",
                "Bearer " + signIn("03400009", "Reception Desk One", SEED_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmptyChairIsNullRatherThanAnError() throws Exception {
        // This doctor has no queue open at all today.
        Doctor other = doctorRepository.findAll().get(0);
        String token = signIn(other.getUser().getPhoneNumber(), other.getUser().getFullName(),
                SEED_PASSWORD);

        JsonNode body = read(token);
        assertThat(body.path("nowServing").isNull()).isTrue();
        assertThat(body.path("doctorId").asInt()).isEqualTo(other.getDoctorId());
    }

    // ---- helpers ----

    /** Today's queue for this doctor, opened if it is not already. */
    private Queue queue() {
        return queueRepository.findByDoctorAndQueueDate(doctor, LocalDate.now())
                .orElseGet(() -> queueRepository.save(new Queue(doctor, null, LocalDate.now())));
    }

    /** Tests share one database per class, so start from an empty chair. */
    private void emptyTheChair() {
        ticketRepository.findByQueueAndStatusFetched(queue(), "IN_CHAIR").forEach(t -> {
            t.setStatus("DONE");
            ticketRepository.save(t);
        });
    }

    /** Puts the patient in this doctor's chair, linked to the appointment if there is one. */
    private QueueTicket callIn(Patient patient, Appointment appointment) {
        Queue queue = queue();
        Integer highest = ticketRepository.highestTicketNumber(queue);
        QueueTicket ticket = new QueueTicket(queue, patient, service,
                highest == null ? 1 : highest + 1);
        ticket.setStatus("IN_CHAIR");
        ticket.setStartedAt(LocalDateTime.now());
        ticket.setAppointment(appointment);
        return ticketRepository.save(ticket);
    }

    private JsonNode currentVisit(String token) throws Exception {
        return json.readTree(mvc.perform(get(CURRENT_URL)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

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
