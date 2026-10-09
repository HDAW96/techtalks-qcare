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
 * Completing a visit has to free the chair, and it has to free the right one.
 * A visit is a queue ticket, never a patient: checking a booking in links its
 * ticket to the appointment, a walk-in's ticket has no appointment at all, and
 * one patient can hold two bookings on the same day. So completion works from
 * the ticket (BE-2): a walk-in can be completed, and finishing one visit
 * never touches another visit of the same patient.
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
        // Tests share one database per class: start each with an empty chair.
        queueRepository.findByDoctorAndQueueDate(doctor, LocalDate.now()).ifPresent(q ->
                ticketRepository.findByQueueAndStatusFetched(q, "IN_CHAIR").forEach(t -> {
                    t.setStatus("DONE");
                    ticketRepository.save(t);
                }));
    }

    @Test
    void completingAVisitFreesTheChair() throws Exception {
        Patient patient = patientRepository.save(new Patient("Rami Daher", "03880011"));
        LocalDate today = LocalDate.now();

        Appointment appointment = appointmentRepository.save(
                new Appointment(patient, doctor, today.atTime(LocalTime.of(10, 0)), service));

        // The desk checked them in and called them in, so the ticket is
        // linked to the appointment - that link is how the visit is found.
        QueueTicket ticket = callIn(patient, appointment);
        assertThat(ticket.getAppointment()).isNotNull();

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

    @Test
    void aWalkInInTheChairCanBeCompleted() throws Exception {
        Patient walkIn = patientRepository.save(new Patient("Walid Hajj", "03880101"));
        QueueTicket ticket = callIn(walkIn, null);
        String token = signInAsDoctor();

        // The current visit is the walk-in: a visit with no booking behind it.
        JsonNode current = currentVisit(token);
        assertThat(current.path("ticketId").asInt()).isEqualTo(ticket.getTicketId());
        assertThat(current.path("appointmentId").isNull()).isTrue();
        assertThat(current.path("walkIn").asBoolean()).isTrue();
        assertThat(current.path("patientId").asInt()).isEqualTo(walkIn.getPatientId());

        JsonNode done = completeCurrent(token, "{\"clinicalNotes\":\"Check-up.\"}", 200);
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.path("ticketId").asInt()).isEqualTo(ticket.getTicketId());
        assertThat(done.path("appointmentId").isNull()).isTrue();

        QueueTicket after = ticketRepository.findById(ticket.getTicketId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("DONE");
        assertThat(after.getStartedAt()).isNotNull();

        // The chair is empty on both of the doctor's reads.
        mvc.perform(get("/api/doctors/me/current-visit")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(schedule(token).path("nowServing").isNull()).isTrue();
    }

    @Test
    void theCurrentVisitIsTheCheckedInBookingAndCompletingItClosesOnlyThatBooking()
            throws Exception {
        // One patient, two bookings today. The LATER one is checked in and
        // in the chair, so a lookup by patient (which finds the earliest
        // booking) would pick the wrong one.
        Patient patient = patientRepository.save(new Patient("Samir Tabet", "03880102"));
        LocalDate today = LocalDate.now();
        Appointment morning = appointmentRepository.save(
                new Appointment(patient, doctor, today.atTime(LocalTime.of(10, 0)), service));
        Appointment afternoon = appointmentRepository.save(
                new Appointment(patient, doctor, today.atTime(LocalTime.of(14, 0)), service));
        queue();
        checkIn(afternoon.getAppointmentId());
        QueueTicket ticket = ticketRepository.findByAppointment(afternoon).orElseThrow();
        ticket.setStatus("IN_CHAIR");
        ticket.setStartedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        String token = signInAsDoctor();
        JsonNode current = currentVisit(token);
        assertThat(current.path("ticketId").asInt()).isEqualTo(ticket.getTicketId());
        assertThat(current.path("appointmentId").asInt()).isEqualTo(afternoon.getAppointmentId());
        assertThat(current.path("walkIn").asBoolean()).isFalse();
        // The schedule names the same visit.
        assertThat(schedule(token).path("nowServing").path("appointmentId").asInt())
                .isEqualTo(afternoon.getAppointmentId());

        JsonNode done = completeCurrent(token, "{\"clinicalNotes\":\"Crown fitted.\"}", 200);
        assertThat(done.path("appointmentId").asInt()).isEqualTo(afternoon.getAppointmentId());

        Appointment doneAppointment = appointmentRepository
                .findById(afternoon.getAppointmentId()).orElseThrow();
        assertThat(doneAppointment.getStatus()).isEqualTo("COMPLETED");
        assertThat(doneAppointment.getClinicalNotes()).isEqualTo("Crown fitted.");
        assertThat(ticketRepository.findById(ticket.getTicketId()).orElseThrow().getStatus())
                .isEqualTo("DONE");

        // The other booking is exactly as it was.
        Appointment other = appointmentRepository
                .findById(morning.getAppointmentId()).orElseThrow();
        assertThat(other.getStatus()).isEqualTo("BOOKED");
        assertThat(other.getClinicalNotes()).isNull();
        assertThat(ticketRepository.findByAppointment(other)).isEmpty();
    }

    @Test
    void completingOneOfTwoSameDayBookingsLeavesTheVisitInTheChairAlone() throws Exception {
        // The reverse: the doctor completes the booking that is NOT in the
        // chair. It used to close the chair ticket of the same patient's
        // other booking, leaving that booking CHECKED_IN with nobody in the chair.
        Patient patient = patientRepository.save(new Patient("Dina Karam", "03880103"));
        LocalDate today = LocalDate.now();
        Appointment inChair = appointmentRepository.save(
                new Appointment(patient, doctor, today.atTime(LocalTime.of(10, 0)), service));
        Appointment other = appointmentRepository.save(
                new Appointment(patient, doctor, today.atTime(LocalTime.of(14, 0)), service));
        queue();
        checkIn(inChair.getAppointmentId());
        QueueTicket ticket = ticketRepository.findByAppointment(inChair).orElseThrow();
        ticket.setStatus("IN_CHAIR");
        ticket.setStartedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        String token = signInAsDoctor();
        JsonNode done = complete(other.getAppointmentId(), token, "{\"clinicalNotes\":null}", 200);
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");

        assertThat(ticketRepository.findById(ticket.getTicketId()).orElseThrow().getStatus())
                .isEqualTo("IN_CHAIR");
        assertThat(appointmentRepository.findById(inChair.getAppointmentId()).orElseThrow()
                .getStatus()).isEqualTo("CHECKED_IN");
        assertThat(currentVisit(token).path("appointmentId").asInt())
                .isEqualTo(inChair.getAppointmentId());
    }

    @Test
    void completingABookingDoesNotCloseTheSamePatientsWalkInInTheChair() throws Exception {
        Patient patient = patientRepository.save(new Patient("Elie Rahme", "03880104"));
        Appointment booking = appointmentRepository.save(new Appointment(
                patient, doctor, LocalDate.now().atTime(LocalTime.of(16, 30)), service));
        QueueTicket walkIn = callIn(patient, null);

        complete(booking.getAppointmentId(), signInAsDoctor(), "{\"clinicalNotes\":null}", 200);

        assertThat(ticketRepository.findById(walkIn.getTicketId()).orElseThrow().getStatus())
                .isEqualTo("IN_CHAIR");
    }

    @Test
    void completingTheCurrentVisitWithAnEmptyChairIsRefused() throws Exception {
        String token = signInAsDoctor();

        JsonNode refused = completeCurrent(token, "{\"clinicalNotes\":null}", 409);
        assertThat(refused.path("error").asText()).isEqualTo("NO_CURRENT_VISIT");
        mvc.perform(get("/api/doctors/me/current-visit")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyADoctorMayUseTheCurrentVisitEndpoints() throws Exception {
        Patient walkIn = patientRepository.save(new Patient("Fadi Yazbeck", "03880105"));
        QueueTicket ticket = callIn(walkIn, null);
        String body = "{\"clinicalNotes\":null}";

        mvc.perform(get("/api/doctors/me/current-visit")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/doctors/me/current-visit/complete")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        String patientToken = registerAndSignIn("Rita Aoun", "03880106");
        String desk = signIn("03400009", "Reception Desk One", SEED_PASSWORD);
        for (String token : new String[] {patientToken, desk}) {
            mvc.perform(get("/api/doctors/me/current-visit")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            completeCurrent(token, body, 403);
        }
        // Another doctor has no visit of this doctor's to complete.
        Doctor other = doctorRepository.findAll().get(1);
        String otherToken = signIn(other.getUser().getPhoneNumber(),
                other.getUser().getFullName(), SEED_PASSWORD);
        completeCurrent(otherToken, body, 409);

        assertThat(ticketRepository.findById(ticket.getTicketId()).orElseThrow().getStatus())
                .isEqualTo("IN_CHAIR");
    }

    // ---- helpers ----

    /** Today's queue for the doctor, opened if it is not already. */
    private Queue queue() {
        return queueRepository.findByDoctorAndQueueDate(doctor, LocalDate.now())
                .orElseGet(() -> queueRepository.save(new Queue(doctor, null, LocalDate.now())));
    }

    /**
     * Puts the patient in the doctor's chair: a ticket that is IN_CHAIR, linked
     * to the appointment if there is one and a walk-in's if there is not.
     */
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

    private void checkIn(int appointmentId) throws Exception {
        mvc.perform(post("/api/appointments/" + appointmentId + "/check-in")
                .header("Authorization", "Bearer "
                        + signIn("03400009", "Reception Desk One", SEED_PASSWORD)))
                .andExpect(status().isOk());
    }

    private JsonNode currentVisit(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/doctors/me/current-visit")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode schedule(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/doctors/me/schedule")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode completeCurrent(String token, String body, int expected) throws Exception {
        String response = mvc.perform(post("/api/doctors/me/current-visit/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

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
