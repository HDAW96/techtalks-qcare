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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Bad input to the controllers the frontend uses must come back as the
 * caller's mistake (4xx) in the one error shape, { message, error }, never as
 * a raw 500. The frontend shows `message` and branches on `error`.
 *
 * One or more cases per controller: auth, appointments, queues, admin users
 * and admin services. BadRequestErrorsTest covers the public-address cases
 * (404, bad id, missing query value, wrong method).
 */
// Opens a queue, like the other queue tests; a fresh context keeps that out
// of the next class.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
class ErrorFormatConsistencyTest {

    private static final String SEED_PASSWORD = "changeme123";
    private static final String LONG_NAME = "A".repeat(300);
    private static final String LONG_NOTES = "N".repeat(3000);

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

    private final ObjectMapper json = new ObjectMapper();

    // --- auth ---

    @Test
    void registeringWithATooLongNameIs400() throws Exception {
        expect(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + LONG_NAME
                        + "\",\"phoneNumber\":\"03555111\",\"password\":\"A-long-password1\"}"),
                400, "NAME_TOO_LONG");
    }

    @Test
    void aLoginSentAsPlainTextIs415() throws Exception {
        expect(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"),
                415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void aLoginSentWithNoContentTypeIs415() throws Exception {
        expect(post("/api/auth/login").content("{}"), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    // --- appointments ---

    @Test
    void aBookingWithNothingInItIs400() throws Exception {
        String patient = registerAndSignIn("Error Format Patient", "03555222");
        expect(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Authorization", "Bearer " + patient), 400, "BOOKING_INCOMPLETE");
    }

    @Test
    void aBookingSentAsPlainTextIs415() throws Exception {
        String patient = registerAndSignIn("Plain Text Patient", "03555333");
        expect(post("/api/appointments").contentType(MediaType.TEXT_PLAIN).content("x")
                .header("Authorization", "Bearer " + patient), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void notesLongerThanTheColumnAreRefusedNotACrash() throws Exception {
        Doctor doctor = doctorRepository.findAll().get(0);
        MedicalService service = serviceRepository.findAll().get(0);
        Patient patient = patientRepository.save(new Patient("Long Notes Patient", "03555444"));
        Appointment appointment = appointmentRepository.save(new Appointment(
                patient, doctor, LocalDate.now().atTime(LocalTime.of(11, 0)), service));

        expect(post("/api/appointments/" + appointment.getAppointmentId() + "/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clinicalNotes\":\"" + LONG_NOTES + "\"}")
                .header("Authorization", "Bearer " + signIn("03400001", "Dr. Rania Haddad")),
                400, "NOTES_TOO_LONG");
    }

    // --- queues ---

    @Test
    void joiningWithNothingInItIs400() throws Exception {
        expect(post("/api/queues/join").contentType(MediaType.APPLICATION_JSON).content("{}"),
                400, "INVALID_QR");
    }

    @Test
    void aWalkInWithATooLongNameIs400() throws Exception {
        String desk = signIn("03400009", "Reception Desk One");
        Doctor doctor = doctorRepository.findAll().get(0);
        MedicalService service = serviceRepository.findAll().get(0);

        int queueId = json.readTree(mvc.perform(post("/api/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":" + doctor.getDoctorId() + "}")
                .header("Authorization", "Bearer " + desk))
                .andReturn().getResponse().getContentAsString())
                .path("queue").path("queueId").asInt();

        expect(post("/api/queues/" + queueId + "/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + LONG_NAME + "\",\"phoneNumber\":\"03555555\",\"serviceId\":"
                        + service.getServiceId() + "}")
                .header("Authorization", "Bearer " + desk), 400, "NAME_TOO_LONG");
    }

    // --- admin users ---

    @Test
    void creatingAUserWithATooLongNameIs400() throws Exception {
        expect(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + LONG_NAME + "\",\"phoneNumber\":\"03555666\","
                        + "\"password\":\"A-long-password1\",\"role\":\"RECEPTIONIST\"}")
                .header("Authorization", "Bearer " + signIn("03400011", "Admin One")),
                400, "NAME_TOO_LONG");
    }

    @Test
    void renamingAUserToATooLongNameIs400() throws Exception {
        String admin = signIn("03400011", "Admin One");
        int someUserId = json.readTree(mvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString())
                .findValue("userId").asInt();

        expect(put("/api/admin/users/" + someUserId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + LONG_NAME + "\"}")
                .header("Authorization", "Bearer " + admin), 400, "NAME_TOO_LONG");
    }

    // --- admin services ---

    @Test
    void aTreatmentWithNoNameIs400() throws Exception {
        expect(post("/api/admin/services").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Authorization", "Bearer " + signIn("03400011", "Admin One")),
                400, "NAME_REQUIRED");
    }

    @Test
    void aTreatmentDurationTooLargeForANumberIs400() throws Exception {
        expect(post("/api/admin/services").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Big\",\"minutes\":99999999999}")
                .header("Authorization", "Bearer " + signIn("03400011", "Admin One")),
                400, "BODY_INVALID");
    }

    // --- helpers ---

    /** The status is what the caller expects and the body is { message, error }. */
    private void expect(MockHttpServletRequestBuilder request, int status, String code)
            throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("status").isEqualTo(status);
        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(body.path("error").asText()).as("error code").isEqualTo(code);
        assertThat(body.path("message").asText()).as("message").isNotBlank();
        assertThat(body.size()).as("only message and error").isEqualTo(2);
    }

    private String signIn(String phone, String fullName) throws Exception {
        return signIn(phone, fullName, SEED_PASSWORD);
    }

    private String signIn(String phone, String fullName, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "phoneNumber", phone, "fullName", fullName, "password", password))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("token").asText();
    }

    private String registerAndSignIn(String fullName, String phone) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "fullName", fullName, "phoneNumber", phone,
                        "password", "A-long-password1"))));
        return signIn(phone, fullName, "A-long-password1");
    }
}
