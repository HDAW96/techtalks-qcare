package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request that is wrong must be answered as the caller's mistake (4xx), not
 * as the server breaking (500). Every case here used to fall to the catch-all
 * handler and come back as 500 INTERNAL_ERROR.
 *
 * All the addresses used are public, so no sign-in is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BadRequestErrorsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anAddressThatDoesNotExistIs404() throws Exception {
        mvc.perform(get("/api/services/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void anIdThatIsNotANumberIs400() throws Exception {
        mvc.perform(get("/api/doctors/abc/availability").param("serviceId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAM_INVALID"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void aRequiredQueryValueThatIsMissingIs400() throws Exception {
        mvc.perform(get("/api/doctors/1/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAM_MISSING"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void theWrongMethodIs405() throws Exception {
        mvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
