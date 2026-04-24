package mr.btp.api.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LocalizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldLocalizeValidationErrorsInFrench() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Accept-Language", "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation échouée"))
                .andExpect(jsonPath("$.errors.email").value("L'email est requis"))
                .andExpect(jsonPath("$.errors.password").value("Le mot de passe est requis"));
    }

    @Test
    void shouldLocalizeUnauthorizedErrorsInArabic() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .header("Accept-Language", "ar"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("غير مصرح"));
    }

    @Test
    @WithMockUser
    void shouldLocalizeParameterizedApiExceptionInFrench() throws Exception {
        mockMvc.perform(get("/api/suppliers/999999999")
                        .header("Accept-Language", "fr"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Fournisseur introuvable"));
    }
}
