package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.JobStatus;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.enums.WorkModality;
import com.trabalhaki.backend.domain.model.CandidateLike;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.Job;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.dto.job.JobApplicantResponse;
import com.trabalhaki.backend.dto.match.CandidateRecommendationResponse;
import com.trabalhaki.backend.dto.match.CompatibilityResult;
import com.trabalhaki.backend.dto.match.JobRecommendationResponse;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.CandidateLikeRepository;
import com.trabalhaki.backend.repository.CandidateProfileRepository;
import com.trabalhaki.backend.repository.CompanyLikeRepository;
import com.trabalhaki.backend.repository.CompanyPassRepository;
import com.trabalhaki.backend.repository.JobMatchRepository;
import com.trabalhaki.backend.repository.JobRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final CompanyPassRepository companyPassRepository;
    private final JobMatchRepository jobMatchRepository;
    private final MatchingAlgorithmService matchingAlgorithmService;
    private final JobService jobService;

    @Transactional(readOnly = true)
    public List<JobRecommendationResponse> getJobRecommendationsForCandidate(String candidateEmail) {
        return getJobRecommendationsForCandidate(candidateEmail, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<JobRecommendationResponse> getJobRecommendationsForCandidate(
            String candidateEmail,
            WorkModality modality,
            String state,
            String city,
            BigDecimal minSalary,
            Integer page,
            Integer size) {
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

            // Optional filters
            if (modality != null && job.getModality() != modality) {
                continue;
            }
            if (state != null && !state.isBlank()) {
                if (job.getState() == null || !job.getState().equalsIgnoreCase(state.trim())) {
                    continue;
                }
            }
            if (city != null && !city.isBlank()) {
                if (job.getCity() == null || !job.getCity().equalsIgnoreCase(city.trim())) {
                    continue;
                }
            }
            if (minSalary != null) {
                if (job.getMaximumSalary() == null || job.getMaximumSalary().compareTo(minSalary) < 0) {
                    continue;
                }
            }

            CompatibilityResult compatibility = matchingAlgorithmService.calculateCompatibility(job, candidate);
            recommendations.add(new JobRecommendationResponse(
                    jobService.mapToResponse(job),
                    compatibility.score(),
                    compatibility.explanation()
            ));
        }

        recommendations.sort(Comparator.comparingInt(JobRecommendationResponse::compatibilityScore).reversed());

        if (page != null && size != null && size > 0 && page >= 0) {
            int fromIndex = Math.min(page * size, recommendations.size());
            int toIndex = Math.min(fromIndex + size, recommendations.size());
            return new ArrayList<>(recommendations.subList(fromIndex, toIndex));
        }

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

    @Transactional(readOnly = true)
    public List<JobApplicantResponse> getApplicantsForJob(String userEmail, Long jobId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.getRole() != Role.COMPANY) {
            throw new UnauthorizedException("Apenas usuários empresariais podem visualizar candidatos da vaga");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada com ID: " + jobId));

        if (!job.getCompany().getId().equals(user.getCompanyId())) {
            throw new UnauthorizedException("Apenas a empresa proprietária da vaga pode visualizar os candidatos interessados");
        }

        List<CandidateLike> likes = candidateLikeRepository.findByJob(job);
        List<JobApplicantResponse> applicants = new ArrayList<>();

        for (CandidateLike like : likes) {
            CandidateProfile candidate = like.getCandidate();
            if (companyLikeRepository.existsByCompanyAndJobAndCandidate(job.getCompany(), job, candidate)
                    || companyPassRepository.existsByCompanyAndJobAndCandidate(job.getCompany(), job, candidate)
                    || jobMatchRepository.findByJobAndCandidate(job, candidate).isPresent()) {
                continue;
            }

            CompatibilityResult compatibility = matchingAlgorithmService.calculateCompatibility(job, candidate);
            List<String> skillNames = candidate.getSkills() == null ? List.of() :
                    candidate.getSkills().stream()
                            .filter(cs -> cs.getSkill() != null)
                            .map(cs -> cs.getSkill().getName())
                            .toList();

            applicants.add(new JobApplicantResponse(
                    candidate.getId(),
                    candidate.getName(),
                    candidate.getPhotoUrl(),
                    candidate.getDesiredRole(),
                    candidate.getArea(),
                    candidate.getExperienceYears(),
                    candidate.getEducation(),
                    candidate.getCity(),
                    candidate.getState(),
                    candidate.getCountry(),
                    candidate.getPreferredModality(),
                    candidate.getPreferredContractType(),
                    candidate.getMinimumSalaryExpectation(),
                    candidate.getCompletionPercentage(),
                    skillNames,
                    compatibility.score(),
                    compatibility.explanation(),
                    like.getCreatedAt()
            ));
        }

        applicants.sort(
                Comparator.comparingInt(JobApplicantResponse::compatibilityScore).reversed()
                        .thenComparing(JobApplicantResponse::appliedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        return applicants;
    }
}
