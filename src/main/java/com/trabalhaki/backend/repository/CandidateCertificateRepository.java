package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.CandidateCertificate;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateCertificateRepository extends JpaRepository<CandidateCertificate, Long> {
    boolean existsByFileSha256(String fileSha256);

    boolean existsByCredentialCodeIgnoreCaseAndCandidateIdNot(String credentialCode, Long candidateId);

    boolean existsByCredentialCodeIgnoreCaseAndCandidateId(String credentialCode, Long candidateId);

    List<CandidateCertificate> findByCandidateOrderByCreatedAtDesc(CandidateProfile candidate);

    Optional<CandidateCertificate> findByIdAndCandidate(Long id, CandidateProfile candidate);
}
