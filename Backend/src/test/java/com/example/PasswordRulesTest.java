package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The four rules a new password must meet. Each refusal has its own error
 * code, and the register form's own check (RegisterPage.jsx) mirrors them, so
 * a renamed code or a changed rule should fail here first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordRulesTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void eachRuleIsRefusedWithItsOwnCode() throws Exception {
        refused("03990001", "Short1!", "PASSWORD_TOO_SHORT");
        refused("03990002", "no-capital-1", "PASSWORD_NO_UPPERCASE");
        refused("03990003", "NO-LOWER-1", "PASSWORD_NO_LOWERCASE");
        refused("03990004", "NoSpecial123", "PASSWORD_NO_SPECIAL");
    }

    @Test
    void aPasswordMeetingEveryRuleIsAccepted() throws Exception {
        register("03990005", "Good-password1").andExpect(status().isCreated());
    }

    private void refused(String phone, String password, String code) throws Exception {
        register(phone, password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(code));
    }

    private ResultActions register(String phone, String password) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Rule Test","phoneNumber":"%s","password":"%s"}
                        """.formatted(phone, password)));
    }
}
