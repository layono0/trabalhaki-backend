package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.ProcessStatus;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.JobMatch;
import com.trabalhaki.backend.domain.model.SelectionProcess;
import com.trabalhaki.backend.domain.model.SelectionProcessHistory;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.dto.process.AdvanceProcessRequest;
import com.trabalhaki.backend.dto.process.SelectionProcessResponse;
import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.JobMatchRepository;
import com.trabalhaki.backend.repository.SelectionProcessHistoryRepository;
import com.trabalhaki.backend.repository.SelectionProcessRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelectionProcessService {

    private final SelectionProcessRepository selectionProcessRepository;
    private final SelectionProcessHistoryRepository historyRepository;
    private final JobMatchRepository jobMatchRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SelectionProcessResponse getProcessByMatchId(String userEmail, Long matchId) {
        User user = getUserByEmail(userEmail);
        JobMatch match = getMatch(matchId);
        validateParticipant(user, match);

        SelectionProcess process = getOrCreateProcess(match, user);
        return mapToResponse(process);
    }

    @Transactional
    public SelectionProcessResponse advanceStatus(String userEmail, Long matchId, AdvanceProcessRequest request) {
        User user = getUserByEmail(userEmail);
        JobMatch match = getMatch(matchId);

        // Rule 34: Only company users can advance selection process stages
        if (user.getRole() != Role.COMPANY || !match.getCompany().getId().equals(user.getCompanyId())) {
            throw new UnauthorizedException("Apenas membros da empresa responsável podem alterar as etapas do processo seletivo");
        }

        if (request.newStatus() == ProcessStatus.WITHDRAWN) {
            throw new BusinessRuleException("O status WITHDRAWN é reservado para desistência informada pelo candidato");
        }

        SelectionProcess process = getOrCreateProcess(match, user);
        ProcessStatus previousStatus = process.getStatus();

        if (previousStatus == request.newStatus()) {
            throw new BusinessRuleException("O processo já se encontra no status: " + request.newStatus());
        }

        process.setStatus(request.newStatus());
        SelectionProcess savedProcess = selectionProcessRepository.save(process);

        // Rule 36: Never overwrite status silently; record audit history
        SelectionProcessHistory history = SelectionProcessHistory.builder()
                .selectionProcess(savedProcess)
                .previousStatus(previousStatus)
                .newStatus(request.newStatus())
                .changedBy(user)
                .note(request.note() != null ? request.note().trim() : null)
                .build();
        historyRepository.save(history);

        log.info("Selection process for match #{} updated from {} to {} by user #{}",
                matchId, previousStatus, request.newStatus(), user.getId());

        return mapToResponse(savedProcess);
    }

    @Transactional
    public SelectionProcessResponse candidateWithdraw(String userEmail, Long matchId, String reason) {
        User user = getUserByEmail(userEmail);
        JobMatch match = getMatch(matchId);

        // Rule 35: Candidate action to withdraw
        if (user.getRole() != Role.CANDIDATE || !match.getCandidate().getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Apenas o candidato participante pode desistir do processo seletivo");
        }

        SelectionProcess process = getOrCreateProcess(match, user);
        ProcessStatus previousStatus = process.getStatus();

        process.setStatus(ProcessStatus.WITHDRAWN);
        SelectionProcess savedProcess = selectionProcessRepository.save(process);

        String auditNote = reason != null && !reason.isBlank() ?
                "Desistência informada pelo candidato: " + reason.trim() :
                "Candidato optou por desistir do processo seletivo";

        SelectionProcessHistory history = SelectionProcessHistory.builder()
                .selectionProcess(savedProcess)
                .previousStatus(previousStatus)
                .newStatus(ProcessStatus.WITHDRAWN)
                .changedBy(user)
                .note(auditNote)
                .build();
        historyRepository.save(history);

        return mapToResponse(savedProcess);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private JobMatch getMatch(Long matchId) {
        return jobMatchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match não encontrado com ID: " + matchId));
    }

    private SelectionProcess getOrCreateProcess(JobMatch match, User user) {
        return selectionProcessRepository.findByMatch(match)
                .orElseGet(() -> {
                    SelectionProcess newProc = SelectionProcess.builder()
                            .match(match)
                            .status(ProcessStatus.MATCHED)
                            .build();
                    SelectionProcess saved = selectionProcessRepository.save(newProc);

                    SelectionProcessHistory initialHistory = SelectionProcessHistory.builder()
                            .selectionProcess(saved)
                            .previousStatus(null)
                            .newStatus(ProcessStatus.MATCHED)
                            .changedBy(user)
                            .note("Início do processo seletivo pós-match")
                            .build();
                    historyRepository.save(initialHistory);

                    return saved;
                });
    }

    private void validateParticipant(User user, JobMatch match) {
        if (user.getRole() == Role.CANDIDATE) {
            if (!match.getCandidate().getUser().getId().equals(user.getId())) {
                throw new UnauthorizedException("Acesso negado ao processo seletivo");
            }
        } else if (user.getRole() == Role.COMPANY) {
            if (!match.getCompany().getId().equals(user.getCompanyId())) {
                throw new UnauthorizedException("Acesso negado ao processo seletivo");
            }
        }
    }

    private SelectionProcessResponse mapToResponse(SelectionProcess process) {
        List<SelectionProcessHistory> histories = historyRepository.findBySelectionProcessOrderByChangedAtAsc(process);

        List<SelectionProcessResponse.HistoryEntry> entries = histories.stream()
                .map(h -> new SelectionProcessResponse.HistoryEntry(
                        h.getPreviousStatus(),
                        h.getNewStatus(),
                        h.getNote(),
                        h.getChangedBy().getId(),
                        h.getChangedAt()
                ))
                .toList();

        return new SelectionProcessResponse(
                process.getId(),
                process.getMatch().getId(),
                process.getStatus(),
                entries,
                process.getCreatedAt(),
                process.getUpdatedAt()
        );
    }
}
