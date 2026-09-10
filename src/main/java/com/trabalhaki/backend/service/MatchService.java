package com.trabalhaki.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trabalhaki.backend.domain.enums.ProcessStatus;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.*;
import com.trabalhaki.backend.dto.job.JobResponse;
import com.trabalhaki.backend.dto.match.CompatibilityResult;
import com.trabalhaki.backend.dto.match.MatchResponse;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final JobMatchRepository jobMatchRepository;
    private final ConversationRepository conversationRepository;
    private final SelectionProcessRepository selectionProcessRepository;
    private final SelectionProcessHistoryRepository selectionProcessHistoryRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final MatchingAlgorithmService matchingAlgorithmService;
    private final JobService jobService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Transactional
    public JobMatch createMatch(Job job, CandidateProfile candidate, User triggeringUser) {
        // Prevent duplicate match creation (Rule 47)
        Optional<JobMatch> existingMatch = jobMatchRepository.findByJobAndCandidate(job, candidate);
        if (existingMatch.isPresent()) {
            return existingMatch.get();
        }

        // Rule 21 & 24: Freeze immutable JSON snapshots of Job and Candidate Profile
        String jobSnapshotJson = buildJobSnapshot(job);
        String candidateSnapshotJson = buildCandidateSnapshot(candidate);

        JobMatch match = JobMatch.builder()
                .job(job)
                .candidate(candidate)
                .company(job.getCompany())
                .jobSnapshotJson(jobSnapshotJson)
                .candidateSnapshotJson(candidateSnapshotJson)
                .build();

        JobMatch savedMatch = jobMatchRepository.save(match);

        // Auto-create Conversation for post-match messaging (Rule 37)
        Conversation conversation = Conversation.builder()
                .match(savedMatch)
                .build();
        conversationRepository.save(conversation);

        // Auto-create SelectionProcess with initial status MATCHED and history (Rule 34 & 36)
        SelectionProcess process = SelectionProcess.builder()
                .match(savedMatch)
                .status(ProcessStatus.MATCHED)
                .build();
        SelectionProcess savedProcess = selectionProcessRepository.save(process);

        SelectionProcessHistory history = SelectionProcessHistory.builder()
                .selectionProcess(savedProcess)
                .previousStatus(null)
                .newStatus(ProcessStatus.MATCHED)
                .changedBy(triggeringUser)
                .note("Match gerado com sucesso entre o candidato e a vaga da empresa.")
                .build();
        selectionProcessHistoryRepository.save(history);

        log.info("JobMatch #{} created between Job #{} and Candidate #{}",
                savedMatch.getId(), job.getId(), candidate.getId());

        return savedMatch;
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getMyMatches(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        List<JobMatch> matches;
        if (user.getRole() == Role.CANDIDATE) {
            CandidateProfile candidate = candidateProfileRepository.findByUser(user)
                    .orElseThrow(() -> new ResourceNotFoundException("Perfil de candidato não encontrado"));
            matches = jobMatchRepository.findByCandidate(candidate);
        } else if (user.getRole() == Role.COMPANY) {
            if (user.getCompanyId() == null) {
                return List.of();
            }
            matches = jobMatchRepository.findByJobCompanyId(user.getCompanyId());
        } else {
            return List.of();
        }

        return matches.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatchById(String userEmail, Long matchId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        JobMatch match = jobMatchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match não encontrado com ID: " + matchId));

        validateAccess(user, match);
        return mapToResponse(match);
    }

    private void validateAccess(User user, JobMatch match) {
        if (user.getRole() == Role.CANDIDATE) {
            if (!match.getCandidate().getUser().getId().equals(user.getId())) {
                throw new UnauthorizedException("Acesso negado a este match");
            }
        } else if (user.getRole() == Role.COMPANY) {
            if (!match.getCompany().getId().equals(user.getCompanyId())) {
                throw new UnauthorizedException("Acesso negado a este match");
            }
        }
    }

    private String buildJobSnapshot(Job job) {
        try {
            JobResponse response = jobService.mapToResponse(job);
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("jobData", response);
            snapshot.put("snapshotCreatedAt", LocalDateTime.now().toString());
            snapshot.put("jobVersionAtMatch", job.getVersion());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("Erro ao gerar snapshot da vaga: {}", e.getMessage());
            return "{}";
        }
    }

    private String buildCandidateSnapshot(CandidateProfile candidate) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", candidate.getId());
            snapshot.put("name", candidate.getName());
            snapshot.put("photoUrl", candidate.getPhotoUrl());
            snapshot.put("desiredRole", candidate.getDesiredRole());
            snapshot.put("area", candidate.getArea());
            snapshot.put("experienceYears", candidate.getExperienceYears());
            snapshot.put("education", candidate.getEducation());
            snapshot.put("city", candidate.getCity());
            snapshot.put("state", candidate.getState());
            snapshot.put("country", candidate.getCountry());
            snapshot.put("minimumSalaryExpectation", candidate.getMinimumSalaryExpectation());
            snapshot.put("preferredModality", candidate.getPreferredModality());
            snapshot.put("preferredContractType", candidate.getPreferredContractType());
            List<String> skills = candidate.getSkills() == null ? List.of() :
                    candidate.getSkills().stream().map(cs -> cs.getSkill().getName()).toList();
            snapshot.put("skills", skills);
            snapshot.put("snapshotCreatedAt", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("Erro ao gerar snapshot do perfil do candidato: {}", e.getMessage());
            return "{}";
        }
    }

    private MatchResponse mapToResponse(JobMatch match) {
        CompatibilityResult result = matchingAlgorithmService.calculateCompatibility(match.getJob(), match.getCandidate());

        return new MatchResponse(
                match.getId(),
                match.getJob().getId(),
                match.getJob().getTitle(),
                match.getCompany().getName(),
                match.getCompany().getLogoUrl(),
                match.getCandidate().getId(),
                match.getCandidate().getName(),
                match.getCandidate().getPhotoUrl(),
                result.score(),
                result.explanationJson(),
                match.getCreatedAt()
        );
    }
}
