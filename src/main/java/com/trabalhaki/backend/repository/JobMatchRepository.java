package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.Job;
import com.trabalhaki.backend.domain.model.JobMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {

    Optional<JobMatch> findByJobAndCandidate(Job job, CandidateProfile candidate);

    boolean existsByJobAndCandidate(Job job, CandidateProfile candidate);

    List<JobMatch> findByCandidate(CandidateProfile candidate);

    List<JobMatch> findByJob(Job job);

    List<JobMatch> findByJobCompanyId(Long companyId);
}
