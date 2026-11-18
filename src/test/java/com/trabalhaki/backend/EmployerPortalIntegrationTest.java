package com.trabalhaki.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trabalhaki.backend.domain.enums.WorkModality;
import com.trabalhaki.backend.domain.model.Job;
import com.trabalhaki.backend.dto.auth.LoginRequest;
import com.trabalhaki.backend.dto.auth.LoginResponse;
import com.trabalhaki.backend.dto.auth.RegisterCompanyRequest;
import com.trabalhaki.backend.dto.chat.SendMessageRequest;
import com.trabalhaki.backend.dto.job.CreateJobRequest;
import com.trabalhaki.backend.dto.job.JobSkillRequest;
import com.trabalhaki.backend.dto.process.AdvanceProcessRequest;
import com.trabalhaki.backend.domain.enums.ProcessStatus;
import com.trabalhaki.backend.domain.enums.SkillType;
import com.trabalhaki.backend.repository.JobRepository;
import com.trabalhaki.backend.service.AuthService;
import com.trabalhaki.backend.service.JobService;
import com.trabalhaki.backend.service.LikeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployerPortalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobService jobService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JobRepository jobRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String loginAndGetToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, LoginResponse.class).getAccessToken();
    }

    @Test
    void shouldReturnOnlyApplicantsWhoLikedTheJobOrderedByScoreAndRejectUnauthorizedAccess() throws Exception {
        String companyToken = loginAndGetToken("empresa@trabalhaki.com", "123456");
        String candidateToken = loginAndGetToken("candidato@trabalhaki.com", "123456");

        // Create a dedicated job so only João Silva applies to it
        var newJob = jobService.createJob("empresa@trabalhaki.com", new CreateJobRequest(
                "Arquiteto Java Cloud",
                "Vaga dedicada ao teste de fila de interessados",
                new BigDecimal("7000.00"),
                new BigDecimal("10000.00"),
                WorkModality.REMOTE,
                null, null, "Brasil", null, null,
                "CLT", "Sênior", 2,
                List.of("Plano de Saúde"),
                List.of(new JobSkillRequest("Java", SkillType.REQUIRED))
        ));

        // Before any candidate likes, applicants list must be empty
        mockMvc.perform(get("/api/v1/jobs/" + newJob.id() + "/applicants")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Candidate likes the job
        likeService.candidateLikeJob("candidato@trabalhaki.com", newJob.id());

        // Also have another candidate with lower compatibility like the job
        likeService.candidateLikeJob("mariana.rocha@trabalhaki.com", newJob.id());

        mockMvc.perform(get("/api/v1/jobs/" + newJob.id() + "/applicants")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("João Silva"))
                .andExpect(jsonPath("$[0].compatibilityScore", greaterThanOrEqualTo(90)))
                .andExpect(jsonPath("$[0].appliedAt").isNotEmpty());

        // Candidate role must be blocked from viewing job applicants
        mockMvc.perform(get("/api/v1/jobs/" + newJob.id() + "/applicants")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        // Another company must be rejected
        authService.registerCompany(new RegisterCompanyRequest(
                "outra@empresa.com",
                "senha123",
                "Outra Empresa LTDA",
                "00.000.000/0001-91"
        ));
        String otherCompanyToken = loginAndGetToken("outra@empresa.com", "senha123");

        mockMvc.perform(get("/api/v1/jobs/" + newJob.id() + "/applicants")
                        .header("Authorization", "Bearer " + otherCompanyToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPassAndUndoPassApplicantUpdatingPendingQueueAndJobCounters() throws Exception {
        String companyToken = loginAndGetToken("empresa@trabalhaki.com", "123456");
        Job demoJob = jobRepository.findAll().get(0);

        String beforeJson = mockMvc.perform(get("/api/v1/jobs/" + demoJob.getId() + "/applicants")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode beforeList = objectMapper.readTree(beforeJson);
        assertTrue(beforeList.size() >= 1);
        long targetCandidateId = beforeList.get(0).get("candidateId").asLong();
        int initialCount = beforeList.size();

        // Pass the first applicant
        mockMvc.perform(post("/api/v1/likes/company/job/" + demoJob.getId() + "/candidate/" + targetCandidateId + "/pass")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk());

        // Queue and pendingApplicantsCount decrease by 1
        mockMvc.perform(get("/api/v1/jobs/" + demoJob.getId() + "/applicants")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(initialCount - 1)));

        mockMvc.perform(get("/api/v1/jobs/" + demoJob.getId())
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingApplicantsCount").value(initialCount - 1));

        // Undo pass restores the applicant
        mockMvc.perform(delete("/api/v1/likes/company/job/" + demoJob.getId() + "/candidate/" + targetCandidateId + "/pass")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/" + demoJob.getId() + "/applicants")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(initialCount)));
    }

    @Test
    void shouldCompleteFullEmployerMatchChatAndProcessFlow() throws Exception {
        String companyToken = loginAndGetToken("empresa@trabalhaki.com", "123456");
        Job demoJob = jobRepository.findAll().get(0);

        String applicantsJson = mockMvc.perform(get("/api/v1/jobs/" + demoJob.getId() + "/applicants")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long candidateId = objectMapper.readTree(applicantsJson).get(0).get("candidateId").asLong();

        // 1. Company likes applicant -> immediate Match
        String likeJson = mockMvc.perform(post("/api/v1/likes/company/job/" + demoJob.getId() + "/candidate/" + candidateId)
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMatch").value(true))
                .andExpect(jsonPath("$.matchId").isNumber())
                .andReturn().getResponse().getContentAsString();

        long matchId = objectMapper.readTree(likeJson).get("matchId").asLong();

        // 2. Match appears with snapshots and initial process status
        mockMvc.perform(get("/api/v1/matches/" + matchId)
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processStatus").value("MATCHED"))
                .andExpect(jsonPath("$.candidateSnapshotJson").isNotEmpty())
                .andExpect(jsonPath("$.jobSnapshotJson").isNotEmpty());

        // 3. Company initiates post-match chat
        mockMvc.perform(post("/api/v1/matches/" + matchId + "/chat/messages")
                        .header("Authorization", "Bearer " + companyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendMessageRequest("Olá! Gostamos muito do seu perfil."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("COMPANY"))
                .andExpect(jsonPath("$.content").value("Olá! Gostamos muito do seu perfil."));

        // 4. Company advances selection process stage
        mockMvc.perform(patch("/api/v1/matches/" + matchId + "/process/advance")
                        .header("Authorization", "Bearer " + companyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdvanceProcessRequest(ProcessStatus.INTERVIEW, "Entrevista técnica agendada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("INTERVIEW"))
                .andExpect(jsonPath("$.history", hasSize(2)));
    }

    @Test
    void shouldUploadAndServeCompanyLogoForAdminAndBlockCandidate() throws Exception {
        String companyToken = loginAndGetToken("empresa@trabalhaki.com", "123456");
        String candidateToken = loginAndGetToken("candidato@trabalhaki.com", "123456");

        byte[] validPngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
        MockMultipartFile logoFile = new MockMultipartFile(
                "file",
                "logo.png",
                MediaType.IMAGE_PNG_VALUE,
                validPngBytes
        );

        // Candidate cannot upload company logo
        mockMvc.perform(multipart("/api/v1/companies/me/logo")
                        .file(logoFile)
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        // Company admin uploads logo successfully
        String responseJson = mockMvc.perform(multipart("/api/v1/companies/me/logo")
                        .file(logoFile)
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(startsWith("/api/v1/files/")))
                .andReturn().getResponse().getContentAsString();

        String logoUrl = objectMapper.readTree(responseJson).get("logoUrl").asText();
        mockMvc.perform(get(logoUrl))
                .andExpect(status().isOk());
    }
}
