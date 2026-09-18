package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * SCRUM-56 bugs 1 and 2: what QueueFlowTest cannot catch because its requests
 * run one after another. These fire several requests at the exact same
 * instant against the same queue, which is the only way to prove the row
 * lock added to QueueRepository.lockById actually serialises them - without
 * it, this test fails the same way the manual run in the ticket did: a
 * "Duplicate entry ... uk_queue_ticket_number" 500, or two tickets IN_CHAIR
 * at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QueueConcurrencyTest {

    private static final String DESK_PHONE = "03400009";
    private static final String DESK_NAME = "Reception Desk One";
    private static final String SEED_PASSWORD = "changeme123";
    private static final int WALK_INS = 8;

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void eightSimultaneousWalkInsAllSucceedWithDistinctNumbers() throws Exception {
        String token = signIn();
        int queueId = openQueueForFirstDoctor(token);
        int consultation = read(get("/api/services"), token).get(0).path("id").asInt();

        ExecutorService pool = Executors.newFixedThreadPool(WALK_INS);
        List<Callable<Integer>> jobs = IntStream.range(0, WALK_INS)
                .<Callable<Integer>>mapToObj(i -> () -> mvc.perform(post("/api/queues/" + queueId + "/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(walkIn("Patient " + i, "7000" + String.format("%04d", i), consultation)))
                        .andReturn().getResponse().getStatus())
                .collect(Collectors.toList());

        List<Future<Integer>> results = pool.invokeAll(jobs);
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // Every one of the 8 must have been accepted (201) - none may hit the
        // 500 the ticket describes ("Duplicate entry ... uk_queue_ticket_number").
        List<Integer> statuses = results.stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return -1;
            }
        }).collect(Collectors.toList());
        assertThat(statuses).as("HTTP status of each of the %d simultaneous walk-ins", WALK_INS)
                .containsOnly(201);

        JsonNode board = read(get("/api/queues/" + queueId + "/tickets"), token);
        assertThat(board.path("entries")).hasSize(WALK_INS);

        // No two tickets may share a number - the exact constraint the
        // race broke (uk_queue_ticket_number).
        Set<String> ticketLabels = new java.util.HashSet<>();
        for (JsonNode entry : board.path("entries")) {
            assertThat(ticketLabels.add(entry.path("ticket").asText()))
                    .as("ticket label %s was given out twice", entry.path("ticket").asText())
                    .isTrue();
        }
    }

    @Test
    void onlyOneOfTwoSimultaneousCallInsSucceeds() throws Exception {
        String token = signIn();
        int queueId = openQueueForFirstDoctor(token);
        int consultation = read(get("/api/services"), token).get(0).path("id").asInt();

        int first = addWalkIn(queueId, token, "Nour Khalil", "71234567", consultation)
                .path("ticketId").asInt();
        int second = addWalkIn(queueId, token, "Ali Mansour", "76345678", consultation)
                .path("ticketId").asInt();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> callFirst = () -> mvc.perform(patch("/api/queues/" + queueId + "/tickets/" + first)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_CHAIR\"}"))
                .andReturn().getResponse().getStatus();
        Callable<Integer> callSecond = () -> mvc.perform(patch("/api/queues/" + queueId + "/tickets/" + second)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_CHAIR\"}"))
                .andReturn().getResponse().getStatus();

        List<Future<Integer>> results = pool.invokeAll(List.of(callFirst, callSecond));
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        long succeeded = results.stream().filter(f -> {
            try {
                return f.get() == 200;
            } catch (Exception e) {
                return false;
            }
        }).count();
        // Exactly one call-in may win; the other must see CHAIR_TAKEN (409),
        // never both succeeding as PR #20's manual test showed.
        assertThat(succeeded).isEqualTo(1);

        JsonNode board = read(get("/api/queues/" + queueId + "/tickets"), token);
        long inChair = 0;
        for (JsonNode entry : board.path("entries")) {
            if ("IN_CHAIR".equals(entry.path("status").asText())) {
                inChair++;
            }
        }
        assertThat(inChair).isEqualTo(1);
    }

    // ---- helpers (same shapes as QueueFlowTest) ----

    private String signIn() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "phoneNumber", DESK_PHONE, "fullName", DESK_NAME, "password", SEED_PASSWORD));
        String response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("token").asText();
    }

    private int openQueueForFirstDoctor(String token) throws Exception {
        JsonNode today = read(get("/api/queues/today"), token);
        int doctorId = today.path("doctors").get(0).path("doctorId").asInt();
        return read(post("/api/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":" + doctorId + "}"), token, 201)
                .path("queue").path("queueId").asInt();
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

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            request, String token) throws Exception {
        return read(request, token, 200);
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            request, String token, int expected) throws Exception {
        String response = mvc.perform(request.header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
