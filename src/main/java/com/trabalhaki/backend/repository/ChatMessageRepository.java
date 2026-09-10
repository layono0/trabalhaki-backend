package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.ChatMessage;
import com.trabalhaki.backend.domain.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
