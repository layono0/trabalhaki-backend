package com.trabalhaki.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trabalhaki.backend.dto.auth.LoginRequest;
import com.trabalhaki.backend.dto.auth.LoginResponse;
import com.trabalhaki.backend.dto.auth.RefreshTokenRequest;
import com.trabalhaki.backend.dto.auth.RegisterCandidateRequest;
import com.trabalhaki.backend.repository.CompanyRepository;
import com.trabalhaki.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void cleanupTestUsers() {
        // Remove test-specific users so registration tests always start clean
        deleteUserAndCompany("novo.candidato@trabalhaki.com");
        deleteUserAndCompany("rh@inovatech.com");
        deleteUserAndCompany("rh2@inovatech.com");
        // Also remove the InovaTech company by CNPJ if it exists as an orphan
        companyRepository.findByCnpj("00000000000191").ifPresent(companyRepository::delete);
    }

    private void deleteUserAndCompany(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getCompanyId() != null) {
                companyRepository.findById(user.getCompanyId()).ifPresent(companyRepository::delete);
            }
            userRepository.delete(user);
        });
    }

    @Test
    void shouldLoginSuccessfullyWithMockAccount() throws Exception {
        LoginRequest request = new LoginRequest("candidato@trabalhaki.com", "123456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("candidato@trabalhaki.com"))
                .andExpect(jsonPath("$.user.role").value("CANDIDATE"));
    }

    @Test
    void shouldFailLoginWithWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest("candidato@trabalhaki.com", "senha_errada");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRegisterNewCandidateSuccessfully() throws Exception {
        RegisterCandidateRequest request = new RegisterCandidateRequest(
                "novo.candidato@trabalhaki.com",
                "senha123",
                "Novo Candidato"
        );

        mockMvc.perform(post("/api/v1/auth/register-candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("novo.candidato@trabalhaki.com"))
                .andExpect(jsonPath("$.user.role").value("CANDIDATE"));
    }

    @Test
    void shouldRegisterNewCompanySuccessfully() throws Exception {
        var request = new com.trabalhaki.backend.dto.auth.RegisterCompanyRequest(
                "rh@inovatech.com",
                "senha123",
                "InovaTech Soluções",
                "00.000.000/0001-91"
        );

        mockMvc.perform(post("/api/v1/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("rh@inovatech.com"))
                .andExpect(jsonPath("$.user.role").value("COMPANY"))
                .andExpect(jsonPath("$.user.companyRole").value("ADMIN"))
                .andExpect(jsonPath("$.user.companyId").isNumber());
    }

    @Test
    void shouldFailRegisterCompanyWithInvalidCnpj() throws Exception {
        var request = new com.trabalhaki.backend.dto.auth.RegisterCompanyRequest(
                "rh2@inovatech.com",
                "senha123",
                "InovaTech Soluções",
                "11.111.111/1111-11"
        );

        mockMvc.perform(post("/api/v1/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CNPJ inválido estruturalmente"));
    }

    @Test
    void shouldReturnValidationErrorsForBlankLoginFields() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        // First login to get tokens
        LoginRequest loginRequest = new LoginRequest("candidato@trabalhaki.com", "123456");

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponse loginResponse = objectMapper.readValue(loginBody, LoginResponse.class);
        String refreshToken = loginResponse.getRefreshToken();

        // Now refresh
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("candidato@trabalhaki.com"));
    }

    @Test
    void shouldGetCurrentUserWithValidToken() throws Exception {
        // First login
        LoginRequest loginRequest = new LoginRequest("candidato@trabalhaki.com", "123456");

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        LoginResponse loginResponse = objectMapper.readValue(loginBody, LoginResponse.class);
        String accessToken = loginResponse.getAccessToken();

        // Call /me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("candidato@trabalhaki.com"))
                .andExpect(jsonPath("$.role").value("CANDIDATE"));
    }
}
