package com.example;

import com.example.entity.Patient;
import com.example.entity.Queue;
import com.example.entity.QueueTicket;
import com.example.entity.User;
import com.example.repository.AppointmentRepository;
import com.example.repository.DoctorRepository;
import com.example.repository.PatientRepository;
import com.example.repository.QueueRepository;
import com.example.repository.QueueTicketRepository;
import com.example.repository.UserRepository;
import com.example.service.DemoDataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo profile, on its own H2 database.
 *
 * The URL below differs from the shared jdbc:h2:mem:qcare every other test
 * uses, so demo data can never leak into them, and @DirtiesContext throws this
 * context away afterwards. Without @ActiveProfiles("demo") DemoDataSeeder is
 * not even created, so no other test is affected.
 */
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:qcare_demo;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
class DemoDataSeederTest {

    private static final String PASSWORD = DemoDataSeeder.DEFAULT_DEMO_PASSWORD;

    @Autowired private MockMvc mvc;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private UserRepository users;
    @Autowired private PatientRepository patients;
    @Autowired private DoctorRepository doctors;
    @Autowired private AppointmentRepository appointments;
    @Autowired private QueueRepository queues;
    @Autowired private QueueTicketRepository tickets;
    @Autowired private PasswordEncoder encoder;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void allFourRolesSignInWithoutBeingForcedToChangePassword() throws Exception {
        String[][] logins = {
                { DemoDataSeeder.DESK_PHONE, DemoDataSeeder.DESK_NAME, "RECEPTIONIST" },
                { DemoDataSeeder.HADDAD_PHONE, DemoDataSeeder.HADDAD_NAME, "DOCTOR" },
                { DemoDataSeeder.ADMIN_PHONE, DemoDataSeeder.ADMIN_NAME, "ADMIN" },
                { DemoDataSeeder.DEMO_PATIENT_PHONE, DemoDataSeeder.DEMO_PATIENT_NAME, "PATIENT" },
        };
        for (String[] l : logins) {
            JsonNode user = signIn(l[0], l[1]).path("user");
            assertThat(user.path("role").asText()).as(l[1]).isEqualTo(l[2]);
            assertThat(user.path("mustChangePassword").asBoolean(true)).as(l[1]).isFalse();
        }
    }

    @Test
    void theReadmeDocumentsExactlyTheSeededLogins() throws Exception {
        Path readme = Path.of("..", "README.md");
        assertThat(readme).exists();
        String text = Files.readString(readme);
        for (String s : List.of(PASSWORD, DemoDataSeeder.DESK_PHONE, DemoDataSeeder.DESK_NAME,
                DemoDataSeeder.HADDAD_PHONE, DemoDataSeeder.HADDAD_NAME, DemoDataSeeder.ADMIN_PHONE,
                DemoDataSeeder.ADMIN_NAME, DemoDataSeeder.DEMO_PATIENT_PHONE, DemoDataSeeder.DEMO_PATIENT_NAME)) {
            assertThat(text).contains(s);
        }
    }

    @Test
    void todayHasAppointmentsAnOpenQueueAndUpcomingVisits() throws Exception {
        String desk = signIn(DemoDataSeeder.DESK_PHONE, DemoDataSeeder.DESK_NAME).path("token").asText();
        assertThat(read(get("/api/appointments"), desk)).isNotEmpty();

        JsonNode today = read(get("/api/queues/today"), desk).path("doctors");
        boolean waiting = false;
        for (JsonNode d : today) {
            if (!d.path("queue").isNull()) {
                assertThat(d.path("queue").path("status").asText()).isEqualTo("OPEN");
                waiting |= d.path("queue").path("waitingCount").asInt(0) >= 3;
            }
        }
        assertThat(waiting).isTrue();

        assertThat(mvc.perform(get("/api/queues/today/board")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isNotEqualTo("[]");

        String patient = signIn(DemoDataSeeder.DEMO_PATIENT_PHONE, DemoDataSeeder.DEMO_PATIENT_NAME)
                .path("token").asText();
        JsonNode mine = read(get("/api/appointments/me"), patient);
        long upcoming = 0;
        for (JsonNode a : mine) {
            if ("BOOKED".equals(a.path("status").asText())) {
                upcoming++;
            }
        }
        assertThat(upcoming).isGreaterThanOrEqualTo(2);
        assertThat(mine.size()).isGreaterThan((int) upcoming); // a past visit exists too
    }

    @Test
    void ticketsAreValidAndNumberedWithoutGaps() {
        Queue queue = queues.findByDoctorAndQueueDate(doctors.findAll().get(0), LocalDate.now()).orElseThrow();
        List<QueueTicket> list = tickets.findByQueue(queue);
        assertThat(list).hasSizeGreaterThanOrEqualTo(3);
        assertThat(list.stream().map(QueueTicket::getTicketNumber).distinct().count()).isEqualTo(list.size());
        assertThat(list.stream().filter(t -> "IN_CHAIR".equals(t.getStatus())).count()).isLessThanOrEqualTo(1);
        assertThat(list).allSatisfy(t -> assertThat(t.getAccessCode()).isNotBlank());
        // the Demo Patient has no active ticket, so they can join through the QR flow
        Patient demo = patients.findByPhoneNumberAndFullName(DemoDataSeeder.DEMO_PATIENT_PHONE,
                DemoDataSeeder.DEMO_PATIENT_NAME).orElseThrow();
        assertThat(tickets.findActiveTicketForPatientOnDate(demo, LocalDate.now())).isEmpty();
    }

    @Test
    void runningTwiceKeepsCountsAndRestoresAChangedPassword() throws Exception {
        seeder.run();
        long u = users.count(), p = patients.count(), a = appointments.count(), q = queues.count(),
                t = tickets.count();

        User desk = users.findByPhoneNumberAndFullName(DemoDataSeeder.DESK_PHONE, DemoDataSeeder.DESK_NAME)
                .orElseThrow();
        desk.setPasswordHash(encoder.encode("Something-Else9!"));
        desk.setMustChangePassword(true);
        users.save(desk);

        seeder.run();

        assertThat(List.of(users.count(), patients.count(), appointments.count(), queues.count(),
                tickets.count())).containsExactly(u, p, a, q, t);
        assertThat(signIn(DemoDataSeeder.DESK_PHONE, DemoDataSeeder.DESK_NAME)
                .path("user").path("mustChangePassword").asBoolean(true)).isFalse();
    }

    @Test
    void appointmentsAreAnchoredToTodayAndLaterDays() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long todays = appointments.findAllBetween(startOfToday, startOfToday.plusDays(1)).size();
        long later = appointments.findAllBetween(startOfToday.plusDays(1), startOfToday.plusDays(11)).size();
        assertThat(todays).isGreaterThanOrEqualTo(5);
        assertThat(later).isGreaterThanOrEqualTo(3);
    }

    private JsonNode signIn(String phone, String name) throws Exception {
        String body = json.writeValueAsString(Map.of("phoneNumber", phone, "fullName", name, "password", PASSWORD));
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(MockHttpServletRequestBuilder request, String token) throws Exception {
        return json.readTree(mvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
