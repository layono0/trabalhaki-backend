package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.JobStatus;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.Job;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.dto.match.CandidateRecommendationResponse;
import com.trabalhaki.backend.dto.match.CompatibilityResult;
import com.trabalhaki.backend.dto.match.JobRecommendationResponse;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.CandidateLikeRepository;
import com.trabalhaki.backend.repository.CandidateProfileRepository;
import com.trabalhaki.backend.repository.CompanyLikeRepository;
import com.trabalhaki.backend.repository.JobRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;
    private final CandidateLikeRepository candidateLikeRepository;
    private final CompanyLikeRepository companyLikeRepository;
    private final MatchingAlgorithmService matchingAlgorithmService;
    private final JobService jobService;

    @Transactional(readOnly = true)
    public List<JobRecommendationResponse> getJobRecommendationsForCandidate(String candidateEmail) {
        User user = userRepository.findByEmail(candidateEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.getRole() != Role.CANDIDATE) {
            throw new UnauthorizedException("Apenas candidatos podem solicitar recomendações de vagas");
        }

        CandidateProfile candidate = candidateProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de candidato não encontrado"));

        // Rule 33: Only active jobs can enter recommendations
        List<Job> activeJobs = jobRepository.findByStatus(JobStatus.ACTIVE);

        List<JobRecommendationResponse> recommendations = new ArrayList<>();
        for (Job job : activeJobs) {
            // Exclude already liked jobs
            if (candidateLikeRepository.existsByCandidateAndJob(candidate, job)) {
                continue;
            }

            CompatibilityResult compatibility = matchingAlgorithmService.calculateCompatibility(job, candidate);
            recommendations.add(new JobRecommendationResponse(
                    jobService.mapToResponse(job),
                    compatibility.score(),
                    compatibility.explanation()
            ));
        }

        recommendations.sort(Comparator.comparingInt(JobRecommendationResponse::compatibilityScore).reversed());
        return recommendations;
    }

    @Transactional(readOnly = true)
    public List<CandidateRecommendationResponse> getCandidateRecommendationsForJob(String userEmail, Long jobId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.getRole() != Role.COMPANY) {
            throw new UnauthorizedException("Apenas usuários empresariais podem consultar recomendações de candidatos");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada com ID: " + jobId));

        if (!job.getCompany().getId().equals(user.getCompanyId())) {
            throw new UnauthorizedException("Apenas a empresa proprietária da vaga pode consultar candidatos recomendados");
        }

        List<CandidateProfile> allCandidates = candidateProfileRepository.findAll();
        List<CandidateRecommendationResponse> recommendations = new ArrayList<>();

        for (CandidateProfile candidate : allCandidates) {
            // Exclude already liked candidates for this specific job
            if (companyLikeRepository.existsByCompanyAndJobAndCandidate(job.getCompany(), job, candidate)) {
                continue;
            }

            CompatibilityResult compatibility = matchingAlgorithmService.calculateCompatibility(job, candidate);

            List<String> skillNames = candidate.getSkills() == null ? List.of() :
                    candidate.getSkills().stream().map(cs -> cs.getSkill().getName()).toList();

            // Safe pre-match view (Rule 20)
            recommendations.add(new CandidateRecommendationResponse(
                    candidate.getId(),
                    candidate.getName(),
                    candidate.getPhotoUrl(),
                    candidate.getDesiredRole(),
                    candidate.getArea(),
                    candidate.getExperienceYears(),
                    candidate.getCity(),
                    candidate.getState(),
                    candidate.getPreferredModality(),
                    candidate.getMinimumSalaryExpectation(),
                    skillNames,
                    compatibility.score(),
                    compatibility.explanation()
            ));
        }

        recommendations.sort(Comparator.comparingInt(CandidateRecommendationResponse::compatibilityScore).reversed());
        return recommendations;
    }
}
