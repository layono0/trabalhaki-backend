package com.trabalhaki.backend;

import com.trabalhaki.backend.domain.enums.JobStatus;
import com.trabalhaki.backend.domain.enums.PlanType;
import com.trabalhaki.backend.domain.enums.SkillType;
import com.trabalhaki.backend.domain.enums.WorkModality;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.Job;
import com.trabalhaki.backend.dto.job.CreateJobRequest;
import com.trabalhaki.backend.dto.job.JobSkillRequest;
import com.trabalhaki.backend.dto.like.CandidateLikeResponse;
import com.trabalhaki.backend.dto.like.LikeResponse;
import com.trabalhaki.backend.dto.match.CompatibilityResult;
import com.trabalhaki.backend.dto.match.MatchResponse;
import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.repository.CandidateProfileRepository;
import com.trabalhaki.backend.repository.CompanyRepository;
import com.trabalhaki.backend.repository.JobRepository;
import com.trabalhaki.backend.service.JobService;
import com.trabalhaki.backend.service.LikeService;
import com.trabalhaki.backend.service.MatchService;
import com.trabalhaki.backend.service.MatchingAlgorithmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JobAndMatchBusinessRulesTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchingAlgorithmService matchingAlgorithmService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private com.trabalhaki.backend.service.AuthService authService;

    @Test
    void shouldEnforceMaxThreeActiveJobsInFreePlan() {
        String companyEmail = "freeplan@startup.com";
        authService.registerCompany(new com.trabalhaki.backend.dto.auth.RegisterCompanyRequest(
                companyEmail,
                "senha123",
                "Startup Plano Free",
                "33.592.510/0001-54"
        ));

        // Create 3 active jobs within free limit
        for (int i = 1; i <= 3; i++) {
            CreateJobRequest req = new CreateJobRequest(
                    "Vaga de Teste " + i,
                    "Descrição da vaga de teste número " + i,
                    new BigDecimal("3000.00"),
                    new BigDecimal("5000.00"),
                    WorkModality.REMOTE,
                    null, null, "Brasil", null, null,
                    "CLT", "Pleno", 2,
                    List.of("VR", "VT"),
                    List.of(new JobSkillRequest("Java", SkillType.REQUIRED))
            );
            jobService.createJob(companyEmail, req);
        }

        // 4th job must be rejected by backend business rule (Rule 14 & 39)
        CreateJobRequest fourthJobReq = new CreateJobRequest(
                "Vaga Excedente 4",
                "Descrição da quarta vaga que deve falhar",
                new BigDecimal("4000.00"),
                new BigDecimal("6000.00"),
                WorkModality.REMOTE,
                null, null, "Brasil", null, null,
                "CLT", "Sênior", 5,
                List.of(),
                List.of()
        );

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> jobService.createJob(companyEmail, fourthJobReq)
        );

        assertTrue(exception.getMessage().contains("Limite de 3 vagas ativas atingido"));
    }

    @Test
    void shouldTriggerMatchAndSnapshotsWhenBothCandidateAndCompanyLike() {
        String companyEmail = "empresa@trabalhaki.com";
        String candidateEmail = "candidato@trabalhaki.com";

        // Create a new job
        var createdJob = jobService.createJob(companyEmail, new CreateJobRequest(
                "Desenvolvedor Flutter Match",
                "Vaga para testar o fluxo de match com snapshots",
                new BigDecimal("5000.00"),
                new BigDecimal("7000.00"),
                WorkModality.REMOTE,
                null, null, "Brasil", null, null,
                "PJ", "Pleno", 2,
                List.of("Auxílio Home Office"),
                List.of(
                        new JobSkillRequest("Flutter", SkillType.REQUIRED),
                        new JobSkillRequest("Dart", SkillType.PREFERRED)
                )
        ));

        CandidateProfile candidate = candidateProfileRepository.findAll().get(0);

        // 1. Candidate likes the job -> no match yet
        LikeResponse candidateLikeResult = likeService.candidateLikeJob(candidateEmail, createdJob.id());
        assertFalse(candidateLikeResult.isMatch());
        assertNull(candidateLikeResult.matchId());

        // 2. Company likes candidate for the same job -> MATCH TRIGGERED!
        LikeResponse companyLikeResult = likeService.companyLikeCandidate(companyEmail, createdJob.id(), candidate.getId());
        assertTrue(companyLikeResult.isMatch());
        assertNotNull(companyLikeResult.matchId());

        // 3. Verify match and snapshots
        MatchResponse match = matchService.getMatchById(candidateEmail, companyLikeResult.matchId());
        assertEquals("Desenvolvedor Flutter Match", match.jobTitle());
        assertEquals("Tech Corp Brasil", match.companyName());
        assertEquals("João Silva", match.candidateName());
        assertTrue(match.compatibilityScore() >= 0 && match.compatibilityScore() <= 100);
        assertNotNull(match.compatibilityExplanationJson());
    }

    @Test
    void shouldNotShowClosedJobInCandidateActiveLikes() {
        String companyEmail = "empresa@trabalhaki.com";
        String candidateEmail = "candidato@trabalhaki.com";

        var createdJob = jobService.createJob(companyEmail, new CreateJobRequest(
                "Vaga para Encerramento",
                "Descrição da vaga que será fechada",
                new BigDecimal("3500.00"),
                new BigDecimal("4500.00"),
                WorkModality.REMOTE,
                null, null, "Brasil", null, null,
                "CLT", "Júnior", 1,
                List.of(), List.of()
        ));

        // Candidate likes the job
        likeService.candidateLikeJob(candidateEmail, createdJob.id());

        // Verify it appears in active likes
        List<CandidateLikeResponse> activeLikesBefore = likeService.getCandidateActiveLikes(candidateEmail);
        boolean foundBefore = activeLikesBefore.stream().anyMatch(l -> l.jobId().equals(createdJob.id()));
        assertTrue(foundBefore);

        // Company closes the job (Rule 26)
        jobService.closeJob(companyEmail, createdJob.id());

        // Verify it MUST disappear from active likes (Rule 26)
        List<CandidateLikeResponse> activeLikesAfter = likeService.getCandidateActiveLikes(candidateEmail);
        boolean foundAfter = activeLikesAfter.stream().anyMatch(l -> l.jobId().equals(createdJob.id()));
        assertFalse(foundAfter, "Vaga encerrada não pode aparecer na lista de likes ativos do candidato");
    }

    @Test
    void shouldCalculateDeterministicScoreCorrectly() {
        CandidateProfile candidate = candidateProfileRepository.findAll().get(0);
        Job job = jobRepository.findAll().get(0);

        CompatibilityResult result = matchingAlgorithmService.calculateCompatibility(job, candidate);

        assertTrue(result.score() >= 0 && result.score() <= 100);
        assertNotNull(result.explanation().get("skills"));
        assertNotNull(result.explanation().get("experience"));
        assertNotNull(result.explanation().get("salary"));
        assertNotNull(result.explanation().get("modality"));
    }
}
