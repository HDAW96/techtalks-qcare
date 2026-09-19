package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The patient's side of the walk-in queue (SCRUM-56 guide §5-7): scanning the
 * clinic QR, choosing a doctor, joining with or without an account, following
 * the ticket, and the waiting-room board. The parts worth protecting are the
 * refusals and the privacy: a faked QR, a second ticket the same day, and any
 * name leaking onto a public screen.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Every method opens queues. A fresh context (and with it a fresh create-drop
// H2 schema and seed) after each one keeps QueueFlowTest's "nothing open yet"
// true whichever order the classes run in - the same as QueueConcurrencyTest.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PatientQueueTest {

    /** Must match qcare.qr.secret in src/test/resources/application.properties. */
    private static final String QR_SECRET = "test-only-qr-secret-0123456789";
    private static final String DESK_PHONE = "03400009";
    private static final String DESK_NAME = "Reception Desk One";
    private static final String SEED_PASSWORD = "changeme123";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aPatientWithoutAnAccountJoinsByQrAndFollowsTheirTicket() throws Exception {
        String desk = signIn(DESK_PHONE, DESK_NAME, SEED_PASSWORD);
        JsonNode today = read(mvc.perform(get("/api/queues/today").header("Authorization", bearer(desk))));
        String qrUrl = today.path("qrUrl").asText();
        assertThat(qrUrl).startsWith("http://localhost:5173/join?date=" + LocalDate.now() + "&sig=");
        String sig = qrUrl.substring(qrUrl.indexOf("&sig=") + 5);
        int doctorId = today.path("doctors").get(0).path("doctorId").asInt();

        // Nothing is open yet, so there is nobody to choose.
        mvc.perform(get("/api/queues/join-options").param("date", LocalDate.now().toString()).param("sig", sig))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        open(desk, doctorId);
        mvc.perform(get("/api/queues/join-options").param("date", LocalDate.now().toString()).param("sig", sig))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].doctorId").value(doctorId))
                .andExpect(jsonPath("$[0].waitingCount").value(0))
                .andExpect(jsonPath("$[0].etaMinutes").value(0));

        JsonNode joined = read(join(null, sig, doctorId, "Nadia Khoury", "03555111")
                .andExpect(status().isCreated()));
        String code = joined.path("accessCode").asText();
        assertThat(code).hasSize(24);
        assertThat(joined.path("ticket").asText()).endsWith("-1");
        assertThat(joined.path("status").asText()).isEqualTo("WAITING");
        assertThat(joined.path("position").asInt()).isZero();

        // The code opens the ticket again, without handing the code back out.
        mvc.perform(get("/api/tickets/by-code/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value(joined.path("ticket").asText()))
                .andExpect(jsonPath("$.accessCode").doesNotExist());
        mvc.perform(get("/api/tickets/by-code/not-a-real-code"))
                .andExpect(status().isNotFound());

        // The desk calls them in; their phone shows it.
        int ticketId = joined.path("ticketId").asInt();
        int queueId = today(desk).path("doctors").get(0).path("queue").path("queueId").asInt();
        mvc.perform(patch("/api/queues/" + queueId + "/tickets/" + ticketId)
                        .header("Authorization", bearer(desk))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_CHAIR\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tickets/by-code/" + code))
                .andExpect(jsonPath("$.status").value("IN_CHAIR"))
                .andExpect(jsonPath("$.nowServing").value(joined.path("ticket").asText()));
    }

    @Test
    void aFakedOrOldQrIsRefused() throws Exception {
        String today = LocalDate.now().toString();
        mvc.perform(get("/api/queues/join-options").param("date", today).param("sig", "made-up"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_QR"));
        // A real signature, but for another date than the one sent.
        mvc.perform(get("/api/queues/join-options").param("date", today)
                        .param("sig", sign(LocalDate.now().minusDays(1))))
                .andExpect(jsonPath("$.error").value("INVALID_QR"));
        // Yesterday's genuine code, photographed and kept.
        LocalDate yesterday = LocalDate.now().minusDays(1);
        mvc.perform(get("/api/queues/join-options").param("date", yesterday.toString())
                        .param("sig", sign(yesterday)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EXPIRED_QR"));
        join(null, "made-up", 1, "Nadia Khoury", "03555111")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_QR"));
    }

    @Test
    void onePersonHoldsOneTicketADayAcrossAllDoctors() throws Exception {
        String desk = signIn(DESK_PHONE, DESK_NAME, SEED_PASSWORD);
        JsonNode doctors = today(desk).path("doctors");
        int first = doctors.get(0).path("doctorId").asInt();
        int second = doctors.get(1).path("doctorId").asInt();
        open(desk, first);
        open(desk, second);
        String sig = sign(LocalDate.now());

        join(null, sig, first, "Karim Saad", "03555222").andExpect(status().isCreated());
        // The other doctor, the same day: refused, by QR and at the desk.
        join(null, sig, second, "Karim Saad", "03555222")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_IN_QUEUE"));
        int secondQueue = today(desk).path("doctors").get(1).path("queue").path("queueId").asInt();
        mvc.perform(post("/api/queues/" + secondQueue + "/tickets")
                        .header("Authorization", bearer(desk))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Karim Saad", "phoneNumber", "03555222", "serviceId", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_IN_QUEUE"));
    }

    @Test
    void aSignedInPatientJoinsAsThemselvesAndSeesTheirOwnTicket() throws Exception {
        String desk = signIn(DESK_PHONE, DESK_NAME, SEED_PASSWORD);
        int doctorId = today(desk).path("doctors").get(0).path("doctorId").asInt();
        open(desk, doctorId);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Lina Haddad",
                                "phoneNumber", "03555333", "password", "A-long-password1"))))
                .andExpect(status().isCreated());
        String patient = signIn("03555333", "Lina Haddad", "A-long-password1");

        // Not in a queue yet.
        mvc.perform(get("/api/tickets/me").header("Authorization", bearer(patient)))
                .andExpect(status().isNoContent());

        // Any name in the body is ignored: a signed-in patient is who the token says.
        join(patient, sign(LocalDate.now()), doctorId, "Someone Else", "03999999")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/tickets/me").header("Authorization", bearer(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.accessCode").doesNotExist());

        String board = mvc.perform(get("/api/queues/" + queueOf(desk, 0) + "/tickets")
                        .header("Authorization", bearer(desk)))
                .andReturn().getResponse().getContentAsString();
        assertThat(board).contains("Lina Haddad").doesNotContain("Someone Else");

        // The desk joins nobody by QR, and cannot read a patient's ticket.
        join(desk, sign(LocalDate.now()), doctorId, null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_A_PATIENT"));
        mvc.perform(get("/api/tickets/me").header("Authorization", bearer(desk)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tickets/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void theWaitingRoomBoardNamesNobody() throws Exception {
        String desk = signIn(DESK_PHONE, DESK_NAME, SEED_PASSWORD);
        int doctorId = today(desk).path("doctors").get(0).path("doctorId").asInt();
        open(desk, doctorId);
        JsonNode joined = read(join(null, sign(LocalDate.now()), doctorId, "Rima Nassar", "03555444")
                .andExpect(status().isCreated()));

        String board = mvc.perform(get("/api/queues/today/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entries[0].ticket").value(joined.path("ticket").asText()))
                .andReturn().getResponse().getContentAsString();
        assertThat(board)
                .doesNotContain("Rima Nassar")
                .doesNotContain("555444")
                .doesNotContain(joined.path("accessCode").asText())
                .doesNotContain("patientId");
    }

    @Test
    void aClosedQueueTakesNobodyNewUntilItIsOpenedAgain() throws Exception {
        String desk = signIn(DESK_PHONE, DESK_NAME, SEED_PASSWORD);
        int doctorId = today(desk).path("doctors").get(0).path("doctorId").asInt();
        open(desk, doctorId);
        int queueId = queueOf(desk, 0);
        String sig = sign(LocalDate.now());

        mvc.perform(post("/api/queues/" + queueId + "/close").header("Authorization", bearer(desk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.status").value("CLOSED"));

        mvc.perform(get("/api/queues/join-options").param("date", LocalDate.now().toString()).param("sig", sig))
                .andExpect(jsonPath("$.length()").value(0));
        join(null, sig, doctorId, "Omar Fares", "03555555")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("QUEUE_NOT_OPEN"));
        mvc.perform(post("/api/queues/" + queueId + "/tickets")
                        .header("Authorization", bearer(desk))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Omar Fares", "phoneNumber", "03555555", "serviceId", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("QUEUE_CLOSED"));

        // "Open queue" again reopens the same queue, not a second one.
        open(desk, doctorId);
        assertThat(queueOf(desk, 0)).isEqualTo(queueId);
        join(null, sig, doctorId, "Omar Fares", "03555555").andExpect(status().isCreated());
    }

    /* ── helpers ── */

    private ResultActions join(String token, String sig, int doctorId, String name, String phone)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("date", LocalDate.now().toString());
        body.put("sig", sig);
        body.put("doctorId", doctorId);
        body.put("serviceId", 1);
        if (name != null) {
            body.put("fullName", name);
            body.put("phoneNumber", phone);
        }
        var request = post("/api/queues/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (token != null) {
            request = request.header("Authorization", bearer(token));
        }
        return mvc.perform(request);
    }

    private void open(String desk, int doctorId) throws Exception {
        mvc.perform(post("/api/queues")
                        .header("Authorization", bearer(desk))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":" + doctorId + "}"))
                .andExpect(status().isCreated());
    }

    private JsonNode today(String desk) throws Exception {
        return read(mvc.perform(get("/api/queues/today").header("Authorization", bearer(desk))));
    }

    private int queueOf(String desk, int doctorIndex) throws Exception {
        return today(desk).path("doctors").get(doctorIndex).path("queue").path("queueId").asInt();
    }

    private String signIn(String phone, String name, String password) throws Exception {
        return read(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "phoneNumber", phone, "fullName", name, "password", password))))
                .andExpect(status().isOk()))
                .path("token").asText();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** The signature QrSigner makes, computed independently from the guide's §5 recipe. */
    private static String sign(LocalDate date) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(QR_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(("clinic-queue|" + date).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
