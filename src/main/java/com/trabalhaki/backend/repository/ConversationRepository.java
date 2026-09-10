package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.Conversation;
import com.trabalhaki.backend.domain.model.JobMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByMatch(JobMatch match);

    Optional<Conversation> findByMatchId(Long matchId);
}
