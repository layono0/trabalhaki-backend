package com.trabalhaki.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trabalhaki.backend.domain.enums.SkillType;
import com.trabalhaki.backend.domain.enums.WorkModality;
import com.trabalhaki.backend.dto.auth.LoginRequest;
import com.trabalhaki.backend.dto.auth.LoginResponse;
import com.trabalhaki.backend.dto.job.CreateJobRequest;
import com.trabalhaki.backend.dto.job.JobSkillRequest;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CertificateVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobService jobService;

    @Autowired
    private LikeService likeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String loginAndGetToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, LoginResponse.class).getAccessToken();
    }

    private byte[] generateSamplePdfViaPython(String expression) throws Exception {
        Path validatorDir = Paths.get("../trabalhaki-cert-validator").toAbsolutePath().normalize();
        if (!Files.isDirectory(validatorDir)) {
            validatorDir = Paths.get("trabalhaki-cert-validator").toAbsolutePath().normalize();
        }
        ProcessBuilder pb = new ProcessBuilder(
                "python3",
                "-c",
                "import sys; from cert_validator.sample_generator import *; sys.stdout.buffer.write(" + expression + ")"
        );
        pb.directory(validatorDir.toFile());
        Process proc = pb.start();
        byte[] pdfBytes = proc.getInputStream().readAllBytes();
        String stderr = new String(proc.getErrorStream().readAllBytes());
        assertEquals(0, proc.waitFor(), "Python sample generator failed: " + stderr);
        return pdfBytes;
    }

    @Test
    void shouldVerifyAuthenticCertificateBoostApplicantPriorityAndRejectDuplicates() throws Exception {
        String candidateToken = loginAndGetToken("candidato@trabalhaki.com", "123456");
        String companyToken = loginAndGetToken("empresa@trabalhaki.com", "123456");

        byte[] authenticPdf = generateSamplePdfViaPython("genuine_aws_cloud('João Silva')");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "aws-cert.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                authenticPdf
        );

        // 1. Validate authentic AWS certificate for João Silva
        mockMvc.perform(multipart("/api/v1/candidates/profile/me/certificates")
                        .file(file)
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.institutionName").value("Amazon Web Services (AWS)"))
                .andExpect(jsonPath("$.institutionTier").value(1))
                .andExpect(jsonPath("$.priorityBonus", greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$.verifiedSkills", hasItem("AWS")));

        // 2. Profile reflects verified skills and priority bonus
        mockMvc.perform(get("/api/v1/candidates/profile/me")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedCertificatesCount").value(1))
                .andExpect(jsonPath("$.priorityBonusTotal", greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$.verifiedSkills", hasItem("AWS")))
                .andExpect(jsonPath("$.skills", hasItem("AWS")));

        // 3. Duplicate upload of exact same certificate must be rejected
        mockMvc.perform(multipart("/api/v1/candidates/profile/me/certificates")
                        .file(file)
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.summary", containsString("anteriormente")));

        // 4. Priority carries into job applicants ranking for an AWS job
        var awsJob = jobService.createJob("empresa@trabalhaki.com", new CreateJobRequest(
                "Engenheiro Cloud AWS",
                "Vaga para validar prioridade de certificado",
                new BigDecimal("6000.00"),
                new BigDecimal("9000.00"),
                WorkModality.REMOTE,
                null, null, "Brasil", null, null,
                "CLT", "Pleno", 2,
                List.of("VR"),
                List.of(new JobSkillRequest("AWS", SkillType.REQUIRED))
        ));
        likeService.candidateLikeJob("candidato@trabalhaki.com", awsJob.id());

        String applicantsJson = mockMvc.perform(get("/api/v1/jobs/" + awsJob.id() + "/applicants")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("João Silva"))
                .andExpect(jsonPath("$[0].priorityBonus", greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$[0].verifiedSkills", hasItem("AWS")))
                .andReturn().getResponse().getContentAsString();

        JsonNode firstApplicant = objectMapper.readTree(applicantsJson).get(0);
        assertTrue(firstApplicant.get("explanation").has("certificates"));
    }

    @Test
    void shouldRejectPhotoshopTamperedAndWrongHolderCertificates() throws Exception {
        String candidateToken = loginAndGetToken("candidato@trabalhaki.com", "123456");

        byte[] photoshopPdf = generateSamplePdfViaPython("forged_photoshop('João Silva')");
        mockMvc.perform(multipart("/api/v1/candidates/profile/me/certificates")
                        .file(new MockMultipartFile("file", "forged.pdf", MediaType.APPLICATION_PDF_VALUE, photoshopPdf))
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.fraudRiskScore", greaterThanOrEqualTo(45)));

        byte[] wrongHolderPdf = generateSamplePdfViaPython("genuine_alura_java('Carlos Alberto Souza')");
        mockMvc.perform(multipart("/api/v1/candidates/profile/me/certificates")
                        .file(new MockMultipartFile("file", "other-person.pdf", MediaType.APPLICATION_PDF_VALUE, wrongHolderPdf))
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.rejectionReasons[0]", containsString("João Silva")));
    }
}
