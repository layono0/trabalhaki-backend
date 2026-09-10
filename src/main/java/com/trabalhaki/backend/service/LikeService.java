package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.JobStatus;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.*;
import com.trabalhaki.backend.dto.like.CandidateLikeResponse;
import com.trabalhaki.backend.dto.like.LikeResponse;
import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final CandidateLikeRepository candidateLikeRepository;
    private final CompanyLikeRepository companyLikeRepository;
    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;
    private final JobService jobService;

    @Transactional
    public LikeResponse candidateLikeJob(String candidateEmail, Long jobId) {
        User user = userRepository.findByEmail(candidateEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.getRole() != Role.CANDIDATE) {
            throw new UnauthorizedException("Apenas candidatos podem demonstrar interesse em vagas");
        }

        CandidateProfile candidate = candidateProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de candidato não encontrado"));

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada com ID: " + jobId));

        // Rule 26: Closed jobs cannot receive new likes or generate matches
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new BusinessRuleException("Esta vaga está encerrada e não aceita mais demonstrações de interesse");
        }

        // Check if candidate already liked
        Optional<CandidateLike> existingLike = candidateLikeRepository.findByCandidateAndJob(candidate, job);
        if (existingLike.isEmpty()) {
            CandidateLike newLike = CandidateLike.builder()
                    .candidate(candidate)
                    .job(job)
                    .build();
            candidateLikeRepository.save(newLike);
        }

        // Rule 19: Check for reciprocal like from company for this job
        boolean companyLiked = companyLikeRepository.existsByCompanyAndJobAndCandidate(job.getCompany(), job, candidate);
        if (companyLiked) {
            JobMatch match = matchService.createMatch(job, candidate, user);
            return new LikeResponse(true, match.getId(), "Match consumado com sucesso!");
        }

        return new LikeResponse(false, null, "Interesse registrado. Aguardando reciprocidade da empresa.");
    }

    @Transactional
    public LikeResponse companyLikeCandidate(String companyEmail, Long jobId, Long candidateId) {
        User user = userRepository.findByEmail(companyEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.getRole() != Role.COMPANY) {
            throw new UnauthorizedException("Apenas empresas podem demonstrar interesse em candidatos");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada com ID: " + jobId));

        if (!job.getCompany().getId().equals(user.getCompanyId())) {
            throw new UnauthorizedException("Apenas a empresa dona da vaga pode curtir candidatos para ela");
        }

        // Rule 26: Closed jobs cannot generate matches
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new BusinessRuleException("Esta vaga está encerrada e não aceita mais interações");
        }

        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidato não encontrado com ID: " + candidateId));

        // Check if company already liked this candidate for this job
        Optional<CompanyLike> existingLike = companyLikeRepository.findByCompanyAndJobAndCandidate(job.getCompany(), job, candidate);
        if (existingLike.isEmpty()) {
            CompanyLike newLike = CompanyLike.builder()
                    .company(job.getCompany())
                    .job(job)
                    .candidate(candidate)
                    .build();
            companyLikeRepository.save(newLike);
        }

        // Rule 19: Check for reciprocal like from candidate for this job
        boolean candidateLiked = candidateLikeRepository.existsByCandidateAndJob(candidate, job);
        if (candidateLiked) {
            JobMatch match = matchService.createMatch(job, candidate, user);
            return new LikeResponse(true, match.getId(), "Match consumado com sucesso!");
        }

        return new LikeResponse(false, null, "Interesse registrado. Aguardando reciprocidade do candidato.");
    }

    @Transactional(readOnly = true)
    public List<CandidateLikeResponse> getCandidateActiveLikes(String candidateEmail) {
        User user = userRepository.findByEmail(candidateEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        CandidateProfile candidate = candidateProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de candidato não encontrado"));

        // Rule 26: Closed jobs must disappear from candidate's active likes list
        return candidateLikeRepository.findByCandidate(candidate)
                .stream()
                .filter(like -> like.getJob().getStatus() == JobStatus.ACTIVE)
                .map(like -> new CandidateLikeResponse(
                        like.getId(),
                        like.getJob().getId(),
                        jobService.mapToResponse(like.getJob()),
                        like.getCreatedAt()
                ))
                .toList();
    }
}
