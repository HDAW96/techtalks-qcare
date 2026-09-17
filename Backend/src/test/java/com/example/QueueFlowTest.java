package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A morning at the front desk, start to finish: the receptionist signs in
 * through the same form a patient uses, opens a doctor's queue, adds two
 * walk-ins, and calls them in one at a time.
 *
 * The parts worth protecting are the refusals. A queue whose rules can be
 * talked around is worse than no queue, because the people reading it on
 * their phones have no way to tell.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QueueFlowTest {

    /** Matches DataSeeder. A development password, never used anywhere real. */
    private static final String DESK_PHONE = "03400009";
    private static final String DESK_NAME = "Reception Desk One";
    private static final String SEED_PASSWORD = "changeme123";

    @Autowired
    private MockMvc mvc;

    /* Built here rather than injected: this only reads and writes the test's
       own JSON, so it has no reason to be the application's mapper. */
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void theDeskRunsAQueueFromOpeningToTheChair() throws Exception {
        // One sign-in form for everyone: the receptionist uses the patient's
        // endpoint, and the role on the account is what differs.
        JsonNode session = signIn(DESK_PHONE, DESK_NAME, SEED_PASSWORD);
        assertThat(session.path("user").path("role").asText()).isEqualTo("RECEPTIONIST");
        String token = session.path("token").asText();

        // Before anything is opened, every doctor is listed with no queue.
        JsonNode today = read(get("/api/queues/today"), token);
        assertThat(today.path("doctors")).isNotEmpty();
        assertThat(today.path("doctors").get(0).path("queue").isNull()).isTrue();
        int doctorId = today.path("doctors").get(0).path("doctorId").asInt();

        int queueId = read(post("/api/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":" + doctorId + "}"), token, 201)
                .path("queue").path("queueId").asInt();

        // Opening a queue that is already open finds it rather than making a
        // second one - two receptionists pressing the same button is normal.
        int again = read(post("/api/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":" + doctorId + "}"), token, 201)
                .path("queue").path("queueId").asInt();
        assertThat(again).isEqualTo(queueId);

        // /api/services sends { id, name, minutes } - the shape the booking
        // screen already reads, not the entity's field names.
        int consultation = read(get("/api/services"), token).get(0).path("id").asInt();

        JsonNode first = addWalkIn(queueId, token, "Nour Khalil", "71234567", consultation);
        JsonNode second = addWalkIn(queueId, token, "Ali Mansour", "76345678", consultation);

        // Ticket numbers follow each other, and the first in line waits for
        // nobody.
        assertThat(first.path("ticket").asText()).isEqualTo("R-1");
        assertThat(second.path("ticket").asText()).isEqualTo("R-2");
        assertThat(first.path("etaMinutes").asInt()).isZero();
        assertThat(second.path("etaMinutes").asInt()).isPositive();

        // The same person cannot be given a second ticket while they still
        // hold one.
        mvc.perform(post("/api/queues/" + queueId + "/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(walkIn("Nour Khalil", "71234567", consultation)))
                .andExpect(status().isConflict());

        int firstTicket = first.path("ticketId").asInt();
        int secondTicket = second.path("ticketId").asInt();

        JsonNode board = move(queueId, firstTicket, "IN_CHAIR", token);
        assertThat(board.path("entries").get(0).path("status").asText()).isEqualTo("IN_CHAIR");

        // One doctor has one chair. The second person cannot be called in
        // while the first is still in it.
        mvc.perform(patch("/api/queues/" + queueId + "/tickets/" + secondTicket)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_CHAIR\"}"))
                .andExpect(status().isConflict());

        // Nor can a ticket jump straight from waiting to finished.
        mvc.perform(patch("/api/queues/" + queueId + "/tickets/" + secondTicket)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isConflict());

        // Once the chair is free the next person can be called in.
        move(queueId, firstTicket, "DONE", token);
        JsonNode after = move(queueId, secondTicket, "IN_CHAIR", token);
        assertThat(after.path("entries").get(0).path("ticketId").asInt()).isEqualTo(secondTicket);
        // Finished tickets stay on the board but leave the line.
        assertThat(after.path("entries").get(1).path("status").asText()).isEqualTo("DONE");
        assertThat(after.path("entries").get(1).path("position").isNull()).isTrue();
    }

    @Test
    void aPatientCannotReadTheDeskQueue() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Hiba Rahal","phoneNumber":"03998877","password":"a-long-password"}
                        """))
                .andExpect(status().isCreated());

        String token = signIn("03998877", "Hiba Rahal", "a-long-password").path("token").asText();

        // Every queue endpoint carries patients' names and phone numbers, so
        // being signed in is not enough to read one.
        mvc.perform(get("/api/queues/today").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void theQueueIsClosedToAnyoneNotSignedIn() throws Exception {
        mvc.perform(get("/api/queues/today")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private JsonNode signIn(String phone, String fullName, String password) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "phoneNumber", phone, "fullName", fullName, "password", password));
        String response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode addWalkIn(int queueId, String token, String name, String phone, int serviceId)
            throws Exception {
        return read(post("/api/queues/" + queueId + "/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(walkIn(name, phone, serviceId)), token, 201);
    }

    private String walkIn(String name, String phone, int serviceId) throws Exception {
        return json.writeValueAsString(java.util.Map.of(
                "fullName", name, "phoneNumber", phone, "serviceId", serviceId));
    }

    private JsonNode move(int queueId, int ticketId, String status, String token) throws Exception {
        return read(patch("/api/queues/" + queueId + "/tickets/" + ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"), token);
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            request, String token) throws Exception {
        return read(request, token, 200);
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            request, String token, int expected) throws Exception {
        String response = mvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
