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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin's analytics. All test classes share one database and others add
 * appointments around today, so this one keeps its data in March 2020 (and
 * one appointment in 2099) and asks for exactly those ranges. The counts are
 * then exact whatever ran before.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsTest {

    private static final String SEED_PASSWORD = "changeme123";
    private static final String MARCH_2020 = "from=2020-03-01&to=2020-03-31";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private MedicalServiceRepository serviceRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private QueueRepository queueRepository;
    @Autowired
    private QueueTicketRepository ticketRepository;

    private final ObjectMapper json = new ObjectMapper();

    private Doctor doctorA;
    private Doctor doctorB;
    private MedicalService cleaning;
    private MedicalService filling;

    /**
     * March 2020:
     *   a1  P1 / A  2 Mar 09:00  COMPLETED  cleaning
     *   a2  P2 / A  2 Mar 10:00  CANCELLED  cleaning
     *   a4  P1 / A 10 Mar 09:30  COMPLETED  cleaning
     *   a3  P3 / B 31 Mar 16:00  BOOKED (past, so a no-show) filling - the last-day trap
     *   t1  P2 / A  2 Mar joined 10:05, called 10:25, DONE     filling - a walk-in: P2's booking was cancelled
     *   t2  P1 / A  2 Mar joined 08:50, called 09:00, DONE     cleaning - NOT a walk-in: P1 had a booking
     *   t3  P4 / B  5 Mar joined 14:10, never called, SKIPPED  cleaning - a walk-in all the same
     *   t4  P5 / B  5 Mar joined 11:00, called 11:30, DONE     cleaning - a walk-in
     * and one BOOKED appointment in June 2099, for "upcoming".
     */
    @BeforeAll
    void seedTestData() {
        doctorA = doctorRepository.findAll().get(0);
        doctorB = doctorRepository.findAll().get(1);
        cleaning = serviceRepository.save(new MedicalService("Analytics cleaning " + System.nanoTime(), 30));
        filling = serviceRepository.save(new MedicalService("Analytics filling " + System.nanoTime(), 45));

        Patient p1 = patientRepository.save(new Patient("Analytics One", "03781001"));
        Patient p2 = patientRepository.save(new Patient("Analytics Two", "03781002"));
        Patient p3 = patientRepository.save(new Patient("Analytics Three", "03781003"));
        Patient p4 = patientRepository.save(new Patient("Analytics Four", "03781004"));
        Patient p5 = patientRepository.save(new Patient("Analytics Five", "03781005"));

        appointment(p1, doctorA, at(2, 9, 0), cleaning, "COMPLETED");
        appointment(p2, doctorA, at(2, 10, 0), cleaning, "CANCELLED");
        appointment(p1, doctorA, at(10, 9, 30), cleaning, "COMPLETED");
        appointment(p3, doctorB, at(31, 16, 0), filling, "BOOKED");
        appointment(p1, doctorA, LocalDateTime.of(2099, 6, 15, 10, 0), cleaning, "BOOKED");

        Queue queueA = queueRepository.save(new Queue(doctorA, null, LocalDate.of(2020, 3, 2)));
        Queue queueB = queueRepository.save(new Queue(doctorB, null, LocalDate.of(2020, 3, 5)));
        ticket(queueA, p2, filling, 1, at(2, 10, 5), at(2, 10, 25), "DONE");
        ticket(queueA, p1, cleaning, 2, at(2, 8, 50), at(2, 9, 0), "DONE");
        ticket(queueB, p4, cleaning, 1, at(5, 14, 10), null, "SKIPPED");
        ticket(queueB, p5, cleaning, 2, at(5, 11, 0), at(5, 11, 30), "DONE");
    }

    @Test
    void onlyAnAdminMaySeeIt() throws Exception {
        mvc.perform(get("/api/admin/analytics")).andExpect(status().isUnauthorized());

        String doctor = signIn("03400001", "Dr. Rania Haddad", SEED_PASSWORD);
        mvc.perform(get("/api/admin/analytics").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isForbidden());
    }

    @Test
    void aBadRangeIsRefusedNotACrash() throws Exception {
        expectError(analytics("from=abc"), "RANGE_INVALID");
        expectError(analytics("to=2020-13-01"), "RANGE_INVALID");
        expectError(analytics("from=2020-03-10&to=2020-03-09"), "RANGE_INVALID");
        expectError(analytics("from=2020-01-01&to=2021-02-04"), "RANGE_TOO_LONG");

        // 366 days is still allowed.
        analytics("from=2020-01-01&to=2020-12-31").andExpect(status().isOk());
    }

    @Test
    void withNoRangeItIsTheLastThirtyDays() throws Exception {
        JsonNode body = body(analytics("").andExpect(status().isOk()));
        LocalDate today = LocalDate.now();
        assertThat(body.path("to").asText()).isEqualTo(today.toString());
        assertThat(body.path("from").asText()).isEqualTo(today.minusDays(29).toString());
    }

    @Test
    void theNumbersForARangeAreExact() throws Exception {
        JsonNode body = body(analytics(MARCH_2020).andExpect(status().isOk()));

        assertThat(body.path("from").asText()).isEqualTo("2020-03-01");
        assertThat(body.path("to").asText()).isEqualTo("2020-03-31");

        JsonNode totals = body.path("appointments");
        assertThat(totals.path("total").asLong()).isEqualTo(4);
        assertThat(totals.path("completed").asLong()).isEqualTo(2);
        assertThat(totals.path("cancelled").asLong()).isEqualTo(1);
        // The 16:00 booking on the last day of the range is counted.
        assertThat(totals.path("noShow").asLong()).isEqualTo(1);
        assertThat(totals.path("upcoming").asLong()).isZero();
        assertThat(totals.path("completed").asLong() + totals.path("cancelled").asLong()
                + totals.path("noShow").asLong() + totals.path("upcoming").asLong())
                .isEqualTo(totals.path("total").asLong());

        // t1, t3, t4. Not t2: that patient had a booking with that doctor that day.
        assertThat(body.path("walkIns").asLong()).isEqualTo(3);
        // (20 + 10 + 30) / 3. The skipped ticket was never called in.
        assertThat(body.path("averageWaitMinutes").asLong()).isEqualTo(20);

        JsonNode perDoctor = body.path("patientsPerDoctor");
        assertThat(perDoctor.size()).isEqualTo((int) doctorRepository.count());
        assertThat(patientsOf(perDoctor, doctorA)).isEqualTo(2);
        assertThat(patientsOf(perDoctor, doctorB)).isEqualTo(1);
        assertThat(perDoctor.get(0).path("doctorId").asInt()).isEqualTo(doctorA.getDoctorId());
        assertThat(perDoctor.get(0).path("doctorName").asText()).isNotBlank();
        for (int i = 1; i < perDoctor.size(); i++) {
            assertThat(perDoctor.get(i).path("patients").asLong())
                    .isLessThanOrEqualTo(perDoctor.get(i - 1).path("patients").asLong());
        }

        // Kept appointments a1, a4, a3 and walk-ins t1, t3, t4.
        JsonNode services = body.path("busiestServices");
        assertThat(services.size()).isEqualTo(2);
        assertThat(services.get(0).path("serviceId").asInt()).isEqualTo(cleaning.getServiceId());
        assertThat(services.get(0).path("name").asText()).isEqualTo(cleaning.getServiceName());
        assertThat(services.get(0).path("count").asLong()).isEqualTo(4);
        assertThat(services.get(1).path("serviceId").asInt()).isEqualTo(filling.getServiceId());
        assertThat(services.get(1).path("count").asLong()).isEqualTo(2);

        JsonNode hours = body.path("busiestHours");
        assertThat(hourCounts(hours)).isEqualTo("9:2 10:1 11:1 14:1 16:1");
    }

    @Test
    void aFutureBookingIsUpcomingNotANoShow() throws Exception {
        JsonNode totals = body(analytics("from=2099-06-01&to=2099-06-30")
                .andExpect(status().isOk())).path("appointments");
        assertThat(totals.path("total").asLong()).isEqualTo(1);
        assertThat(totals.path("upcoming").asLong()).isEqualTo(1);
        assertThat(totals.path("noShow").asLong()).isZero();
    }

    @Test
    void withNobodyCalledInTheWaitIsNullNotZero() throws Exception {
        JsonNode body = body(analytics("from=2020-06-01&to=2020-06-30").andExpect(status().isOk()));
        assertThat(body.path("averageWaitMinutes").isNull()).isTrue();
        assertThat(body.path("walkIns").asLong()).isZero();
        assertThat(body.path("appointments").path("total").asLong()).isZero();
        assertThat(body.path("busiestServices").size()).isZero();
        assertThat(body.path("busiestHours").size()).isZero();
        // Every doctor is still listed, with 0.
        assertThat(body.path("patientsPerDoctor").size()).isEqualTo((int) doctorRepository.count());
    }

    private void appointment(Patient patient, Doctor doctor, LocalDateTime when,
            MedicalService service, String status) {
        Appointment appointment = new Appointment(patient, doctor, when, service);
        appointment.setStatus(status);
        appointmentRepository.save(appointment);
    }

    private void ticket(Queue queue, Patient patient, MedicalService service, int number,
            LocalDateTime joinedAt, LocalDateTime startedAt, String status) {
        QueueTicket ticket = new QueueTicket(queue, patient, service, number);
        ticket.setJoinedAt(joinedAt);
        ticket.setStartedAt(startedAt);
        ticket.setStatus(status);
        ticketRepository.save(ticket);
    }

    private static LocalDateTime at(int marchDay, int hour, int minute) {
        return LocalDateTime.of(2020, 3, marchDay, hour, minute);
    }

    private static long patientsOf(JsonNode perDoctor, Doctor doctor) {
        for (JsonNode row : perDoctor) {
            if (row.path("doctorId").asInt() == doctor.getDoctorId()) {
                return row.path("patients").asLong();
            }
        }
        throw new AssertionError("Doctor " + doctor.getDoctorId() + " is missing");
    }

    private static String hourCounts(JsonNode hours) {
        StringBuilder out = new StringBuilder();
        for (JsonNode row : hours) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(row.path("hour").asInt()).append(':').append(row.path("count").asLong());
        }
        return out.toString();
    }

    private ResultActions analytics(String query) throws Exception {
        String admin = signIn("03400011", "Admin One", SEED_PASSWORD);
        return mvc.perform(get("/api/admin/analytics" + (query.isEmpty() ? "" : "?" + query))
                .header("Authorization", "Bearer " + admin));
    }

    private void expectError(ResultActions result, String code) throws Exception {
        result.andExpect(status().isBadRequest());
        assertThat(body(result).path("error").asText()).isEqualTo(code);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
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
    
    @Test
    void aPastBookedAppointmentCountsAsANoShow() throws Exception {
        JsonNode totals = body(analytics(MARCH_2020)
            .andExpect(status().isOk()))
            .path("appointments");

         assertThat(totals.path("noShow").asLong()).isEqualTo(1);
        }

    @Test
    void aCheckedInAppointmentDoesNotCountAsANoShow() throws Exception {

        long noShowsBefore = body(analytics(MARCH_2020)
            .andExpect(status().isOk()))
            .path("appointments")
            .path("noShow")
            .asLong();

        Patient patient =
            patientRepository.save(
                    new Patient("Checked In", "03781999"));

        appointment(
            patient,
            doctorA,
           LocalDateTime.of(2020, 4, 20, 9, 0),
            cleaning,
            "CHECKED_IN");

         JsonNode totals = body(analytics(MARCH_2020)
            .andExpect(status().isOk()))
            .path("appointments");

        assertThat(totals.path("noShow").asLong())
            .isEqualTo(noShowsBefore);
    }

    @Test
    void aCompletedAppointmentDoesNotCountAsANoShow() throws Exception {

        long noShowsBefore = body(analytics(MARCH_2020)
            .andExpect(status().isOk()))
            .path("appointments")
            .path("noShow")
            .asLong();

         Patient patient =
            patientRepository.save(
                    new Patient("Completed", "03781998"));

        appointment(
            patient,
            doctorA,
            LocalDateTime.of(2020, 4, 21, 9, 0),
            cleaning,
            "COMPLETED");

         JsonNode totals = body(analytics(MARCH_2020)
            .andExpect(status().isOk()))
            .path("appointments");

         assertThat(totals.path("noShow").asLong())
            .isEqualTo(noShowsBefore);
    }

}
