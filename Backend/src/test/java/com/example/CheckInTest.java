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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checking in a booked patient at the desk (SCRUM-99).
 *
 * Until this existed a booked patient had no way onto the live queue, and no
 * ticket was ever linked to its appointment. What matters: today's booking
 * becomes a WAITING ticket linked to it, the booking is marked CHECKED_IN,
 * and every booking that should not be checked in is refused with a reason.
 */
// Opens a queue and issues tickets, like the other queue tests. A fresh
// context afterwards keeps that out of the next class.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
class CheckInTest {

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
    void theDeskChecksInTodaysBookingAndThePatientJoinsTheQueue() throws Exception {
        Appointment booking = booking("Nour Haddad", "03440001", LocalDate.now());

        JsonNode ticket = checkIn(booking.getAppointmentId(), desk(), 200);
        assertThat(ticket.path("status").asText()).isEqualTo("WAITING");

        Appointment after = appointmentRepository.findById(booking.getAppointmentId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("CHECKED_IN");

        // The ticket is linked to the booking - the link nothing made before.
        // It is in today's queue for this doctor.
        QueueTicket linked = ticketRepository.findByAppointment(after).orElseThrow();
        Queue today = queueRepository.findByDoctorAndQueueDate(doctor, LocalDate.now()).orElseThrow();
        assertThat(linked.getStatus()).isEqualTo("WAITING");
        assertThat(linked.getQueue().getQueueId()).isEqualTo(today.getQueueId());
    }

    @Test
    void aCheckedInBookingInTheChairIsTheDoctorsCurrentVisitWithItsAppointmentId()
            throws Exception {
        Appointment booking = booking("Rim Azar", "03440008", LocalDate.now());
        checkIn(booking.getAppointmentId(), desk(), 200);
        QueueTicket ticket = ticketRepository.findByAppointment(booking).orElseThrow();
        ticket.setStatus("IN_CHAIR");
        ticket.setStartedAt(java.time.LocalDateTime.now());
        ticketRepository.save(ticket);

        String doctorToken = signIn(doctor.getUser().getPhoneNumber(),
                doctor.getUser().getFullName(), SEED_PASSWORD);
        JsonNode current = json.readTree(mvc.perform(get("/api/doctors/me/current-visit")
                .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(current.path("appointmentId").asInt()).isEqualTo(booking.getAppointmentId());
        assertThat(current.path("ticketId").asInt()).isEqualTo(ticket.getTicketId());

        // Same on the schedule.
        JsonNode schedule = json.readTree(mvc.perform(get("/api/doctors/me/schedule")
                .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(schedule.path("nowServing").path("appointmentId").asInt())
                .isEqualTo(booking.getAppointmentId());
    }

    @Test
    void checkingInTwiceIsRefused() throws Exception {
        Appointment booking = booking("Karim Saab", "03440002", LocalDate.now());
        String desk = desk();

        checkIn(booking.getAppointmentId(), desk, 200);
        JsonNode again = checkIn(booking.getAppointmentId(), desk, 409);
        assertThat(again.path("error").asText()).isEqualTo("ALREADY_CHECKED_IN");
    }

    @Test
    void aBookingForAnotherDayIsRefused() throws Exception {
        Appointment booking = booking("Maya Fares", "03440003", LocalDate.now().plusDays(1));

        JsonNode refused = checkIn(booking.getAppointmentId(), desk(), 409);
        assertThat(refused.path("error").asText()).isEqualTo("NOT_TODAY");
        assertThat(ticketRepository.findByAppointment(booking)).isEmpty();
    }

    @Test
    void aCancelledBookingIsRefused() throws Exception {
        Appointment booking = booking("Omar Khalil", "03440004", LocalDate.now());
        booking.setStatus("CANCELLED");
        appointmentRepository.save(booking);

        JsonNode refused = checkIn(booking.getAppointmentId(), desk(), 409);
        assertThat(refused.path("error").asText()).isEqualTo("APPOINTMENT_CANCELLED");
        assertThat(ticketRepository.findByAppointment(booking)).isEmpty();
    }

    @Test
    void onlyTheDeskOrAnAdminMayCheckIn() throws Exception {
        Appointment booking = booking("Sara Nassar", "03440005", LocalDate.now());
        int id = booking.getAppointmentId();

        mvc.perform(post("/api/appointments/" + id + "/check-in"))
                .andExpect(status().isUnauthorized());
        checkIn(id, registerAndSignIn("Tony Rizk", "03440006"), 403);
        checkIn(id, signIn(doctor.getUser().getPhoneNumber(), doctor.getUser().getFullName(),
                SEED_PASSWORD), 403);
        assertThat(ticketRepository.findByAppointment(booking)).isEmpty();

        // An admin may stand in for the desk.
        checkIn(id, signIn("03400011", "Admin One", SEED_PASSWORD), 200);
    }

    @Test
    void aCheckedInVisitCanStillBeCompletedByTheDoctor() throws Exception {
        Appointment booking = booking("Lea Mansour", "03440007", LocalDate.now());
        checkIn(booking.getAppointmentId(), desk(), 200);

        mvc.perform(post("/api/appointments/" + booking.getAppointmentId() + "/complete")
                .header("Authorization", "Bearer " + signIn(doctor.getUser().getPhoneNumber(),
                        doctor.getUser().getFullName(), SEED_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clinicalNotes\":null}"))
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    /** A booking on the given day, with today's queue for its doctor open. */
    private Appointment booking(String name, String phone, LocalDate day) {
        Patient patient = patientRepository.save(new Patient(name, phone));
        queueRepository.findByDoctorAndQueueDate(doctor, LocalDate.now())
                .orElseGet(() -> queueRepository.save(new Queue(doctor, null, LocalDate.now())));
        return appointmentRepository.save(
                new Appointment(patient, doctor, day.atTime(LocalTime.of(11, 0)), service));
    }

    private JsonNode checkIn(int appointmentId, String token, int expected) throws Exception {
        String response = mvc.perform(post("/api/appointments/" + appointmentId + "/check-in")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String desk() throws Exception {
        return signIn("03400009", "Reception Desk One", SEED_PASSWORD);
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
     //for registred patient
    private Appointment bookingForRegistredPatient(Patient patient, LocalDate day) {
    queueRepository.findByDoctorAndQueueDate(doctor, LocalDate.now())
            .orElseGet(() -> queueRepository.save(
                    new Queue(doctor, null, LocalDate.now())));

    return appointmentRepository.save(
            new Appointment(patient, doctor,
                    day.atTime(LocalTime.of(11, 0)), service));
   }
   
    @Test
    void leavingQueueAfterCheckInReturnsAppointmentToBooked() throws Exception {
    String patientToken = registerAndSignIn("sara yousef", "03400012");

    Patient patient = patientRepository
        .findByPhoneNumberAndFullName("03400012", "sara yousef")
        .orElseThrow();

    Appointment booking = bookingForRegistredPatient( patient, LocalDate.now());
        
    JsonNode ticket = checkIn(booking.getAppointmentId(), desk(), 200);
        
    QueueTicket linkedTicket = ticketRepository
            .findByAppointment(booking)
            .orElseThrow();

    mvc.perform(delete("/api/tickets/" + linkedTicket.getTicketId())
            .header("Authorization", "Bearer " + patientToken))
            .andExpect(status().isNoContent());  

    QueueTicket refreshedTicket = ticketRepository.findById(linkedTicket.getTicketId()).orElseThrow();
    
     assertThat(refreshedTicket.getStatus()).isEqualTo("SKIPPED");

    Appointment afterBooking = appointmentRepository
            .findById(booking.getAppointmentId())
            .orElseThrow();

    assertThat(afterBooking.getStatus()).isEqualTo("BOOKED");
    }
}
