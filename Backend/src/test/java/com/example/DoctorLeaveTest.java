package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.entity.Doctor;
import com.example.entity.MedicalService;
import com.example.repository.DoctorRepository;
import com.example.repository.MedicalServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A doctor blocking vacation or sick leave, and the two places that must
 * agree once they do: the day strip a patient is shown (BookingService.
 * availability) and the booking endpoint itself (BookingService.book, via
 * requireBookable). A test that only checked the day strip would pass even
 * if a patient could still book straight through it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DoctorLeaveTest {

    private static final String SEED_PASSWORD = "changeme123";
    private static final String LEAVE_URL = "/api/doctors/me/leave";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private MedicalServiceRepository serviceRepository;

    private final ObjectMapper json = new ObjectMapper();

    private Doctor doctor;
    private MedicalService service;

    @BeforeEach
    void seedIsPresent() {
        doctor = doctorRepository.findAll().get(0);
        service = serviceRepository.findAll().get(0);
    }

    /**
     * The next Monday after today, plus `weeksAfter` extra weeks. Dr Haddad
     * (doctor index 0, seeded Monday-Friday 09:00-17:00) works it, so it is
     * open unless a test has put it on leave. Using the *next* Monday rather
     * than "N weeks from today" keeps every date this test uses safely
     * under AvailabilityController's 30-day MAX_DAYS ceiling, and strictly
     * in the future so "that time has already passed" never triggers.
     */
    private LocalDate monday(int weeksAfter) {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(weeksAfter);
    }

    @Test
    void onlyASignedInDoctorMaySetTheirOwnLeave() throws Exception {
        LocalDate monday = monday(0);
        mvc.perform(post(LEAVE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveBody(monday, monday)))
                .andExpect(status().isUnauthorized());

        String patient = registerAndSignIn("Rita Aoun", "03660044");
        mvc.perform(post(LEAVE_URL).header("Authorization", "Bearer " + patient)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveBody(monday, monday)))
                .andExpect(status().isForbidden());

        String desk = signIn("03400009", "Reception Desk One", SEED_PASSWORD);
        mvc.perform(post(LEAVE_URL).header("Authorization", "Bearer " + desk)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveBody(monday, monday)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEndDateBeforeTheStartDateIsRejected() throws Exception {
        LocalDate monday = monday(0);
        String token = signInAsThisDoctor();
        mvc.perform(post(LEAVE_URL).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveBody(monday, monday.minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leaveEmptiesTheDayStripAndRefusesABookingOnThatDay() throws Exception {
        LocalDate monday = monday(1);
        // Before the leave: a normal working Monday shows open times.
        JsonNode before = availabilityFor(monday);
        assertThat(before.path("slots")).isNotEmpty();
        assertThat(before.path("openCount").asInt()).isGreaterThan(0);

        String doctorToken = signInAsThisDoctor();
        mvc.perform(post(LEAVE_URL).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveBody(monday, monday.plusDays(2))))
                .andExpect(status().isCreated());

        // After the leave: the same Monday now has no slots at all - the
        // frontend reads an empty list as "Not working", the same as any
        // other day this doctor simply does not hold clinic.
        JsonNode after = availabilityFor(monday);
        assertThat(after.path("slots")).isEmpty();
        assertThat(after.path("openCount").asInt()).isZero();

        // The real gate: booking straight through the endpoint, bypassing
        // the day strip entirely, must also be refused.
        String patientToken = registerAndSignIn("Nadine Farah", "03660055");
        String response = mvc.perform(post("/api/appointments")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "doctorId", doctor.getDoctorId(),
                        "serviceId", service.getServiceId(),
                        "datetime", monday + "T09:00"))))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(response).path("error").asText()).isEqualTo("DOCTOR_ON_LEAVE");
    }

    @Test
    void aDayOutsideTheLeaveRangeIsUnaffected() throws Exception {
        LocalDate monday = monday(3);
        String doctorToken = signInAsThisDoctor();
        mvc.perform(post(LEAVE_URL).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveBody(monday, monday)))
                .andExpect(status().isCreated());

        LocalDate dayAfter = monday.plusDays(1);
        JsonNode stillOpen = availabilityFor(dayAfter);
        assertThat(stillOpen.path("openCount").asInt()).isGreaterThan(0);
    }

    // ---- helpers ----

    private JsonNode availabilityFor(LocalDate date) throws Exception {
        String response = mvc.perform(get("/api/doctors/" + doctor.getDoctorId() + "/availability")
                .param("serviceId", String.valueOf(service.getServiceId()))
                // days=1 always starts from today, so to inspect a date two
                // weeks out reliably we ask for enough days to reach it.
                .param("days", String.valueOf((int) (java.time.temporal.ChronoUnit.DAYS
                        .between(LocalDate.now(), date) + 1))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode days = json.readTree(response);
        for (JsonNode day : days) {
            if (day.path("date").asText().equals(date.toString())) {
                return day;
            }
        }
        throw new AssertionError("Date " + date + " was not in the availability response");
    }

    private String leaveBody(LocalDate start, LocalDate end) throws Exception {
        return json.writeValueAsString(Map.of("startDate", start.toString(), "endDate", end.toString()));
    }

    private String signInAsThisDoctor() throws Exception {
        return signIn(doctor.getUser().getPhoneNumber(), doctor.getUser().getFullName(), SEED_PASSWORD);
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
