package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.JobMatch;
import com.trabalhaki.backend.domain.model.SelectionProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SelectionProcessRepository extends JpaRepository<SelectionProcess, Long> {

    Optional<SelectionProcess> findByMatch(JobMatch match);

    Optional<SelectionProcess> findByMatchId(Long matchId);
}
