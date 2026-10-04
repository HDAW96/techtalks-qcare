package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every admin endpoint, tried by everyone who is not an admin: signed out is a
 * 401, and a patient, doctor or receptionist is a 403. A new admin endpoint
 * belongs in ENDPOINTS so it is checked the same way.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAccessTest {

    private static final String SEED_PASSWORD = "changeme123";

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.GET, "/api/admin/users"),
            new Endpoint(HttpMethod.POST, "/api/admin/users"),
            new Endpoint(HttpMethod.PUT, "/api/admin/users/1"),
            new Endpoint(HttpMethod.DELETE, "/api/admin/users/1"),
            new Endpoint(HttpMethod.GET, "/api/admin/services"),
            new Endpoint(HttpMethod.POST, "/api/admin/services"),
            new Endpoint(HttpMethod.PATCH, "/api/admin/services/1"),
            new Endpoint(HttpMethod.DELETE, "/api/admin/services/1"),
            new Endpoint(HttpMethod.GET, "/api/admin/analytics"));

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void signedOutIsAskedToSignIn() throws Exception {
        for (Endpoint endpoint : ENDPOINTS) {
            assertThat(statusOf(endpoint, null)).as(endpoint.toString()).isEqualTo(401);
        }
    }

    @Test
    void staffWhoAreNotAdminsAreRefused() throws Exception {
        String doctor = signIn("03400001", "Dr. Rania Haddad", SEED_PASSWORD);
        String receptionist = signIn("03400009", "Reception Desk One", SEED_PASSWORD);
        for (Endpoint endpoint : ENDPOINTS) {
            assertThat(statusOf(endpoint, doctor)).as("doctor " + endpoint).isEqualTo(403);
            assertThat(statusOf(endpoint, receptionist)).as("receptionist " + endpoint).isEqualTo(403);
        }
    }

    @Test
    void patientsAreRefused() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "phoneNumber", "03780001", "fullName", "Access Test Patient",
                        "password", "Patient-pass1")))).andExpect(status().isCreated());
        String patient = signIn("03780001", "Access Test Patient", "Patient-pass1");

        for (Endpoint endpoint : ENDPOINTS) {
            assertThat(statusOf(endpoint, patient)).as("patient " + endpoint).isEqualTo(403);
        }
    }

    private int statusOf(Endpoint endpoint, String token) throws Exception {
        MockHttpServletRequestBuilder call = request(endpoint.method(), endpoint.path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (token != null) {
            call.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(call).andReturn().getResponse().getStatus();
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

    private record Endpoint(HttpMethod method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}
