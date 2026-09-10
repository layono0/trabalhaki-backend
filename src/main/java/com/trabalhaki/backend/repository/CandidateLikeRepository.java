package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.CandidateLike;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateLikeRepository extends JpaRepository<CandidateLike, Long> {

    Optional<CandidateLike> findByCandidateAndJob(CandidateProfile candidate, Job job);

    boolean existsByCandidateAndJob(CandidateProfile candidate, Job job);

    List<CandidateLike> findByCandidate(CandidateProfile candidate);

    List<CandidateLike> findByJob(Job job);
}
