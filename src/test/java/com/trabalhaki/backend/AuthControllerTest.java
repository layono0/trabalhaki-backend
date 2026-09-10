package com.trabalhaki.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trabalhaki.backend.dto.auth.LoginRequest;
import com.trabalhaki.backend.dto.auth.RegisterCandidateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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
}
