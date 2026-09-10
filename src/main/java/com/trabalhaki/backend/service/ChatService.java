package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.ChatMessage;
import com.trabalhaki.backend.domain.model.Conversation;
import com.trabalhaki.backend.domain.model.JobMatch;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.dto.chat.ConversationResponse;
import com.trabalhaki.backend.dto.chat.SendMessageRequest;
import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.ChatMessageRepository;
import com.trabalhaki.backend.repository.ConversationRepository;
import com.trabalhaki.backend.repository.JobMatchRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JobMatchRepository jobMatchRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(String userEmail, Long matchId) {
        User user = getUserByEmail(userEmail);
        JobMatch match = getMatch(matchId);
        validateParticipant(user, match);

        Conversation conversation = getOrCreateConversation(match);
        List<ChatMessage> messages = chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation);

        List<ConversationResponse.ChatMessageResponse> messageResponses = messages.stream()
                .map(this::mapToMessageResponse)
                .toList();

        return new ConversationResponse(conversation.getId(), match.getId(), messageResponses);
    }

    @Transactional
    public ConversationResponse.ChatMessageResponse sendMessage(String userEmail, Long matchId, SendMessageRequest request) {
        User user = getUserByEmail(userEmail);
        JobMatch match = getMatch(matchId);
        validateParticipant(user, match);

        Conversation conversation = getOrCreateConversation(match);
        List<ChatMessage> existingMessages = chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation);

        // Rule 37: The company/recruiter must initiate the conversation
        if (existingMessages.isEmpty() && user.getRole() == Role.CANDIDATE) {
            throw new BusinessRuleException("A empresa responsável deve iniciar a conversa após o Match antes do candidato responder.");
        }

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(user)
                .senderRole(user.getRole())
                .text(request.content().trim())
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);
        return mapToMessageResponse(savedMessage);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private JobMatch getMatch(Long matchId) {
        return jobMatchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match não encontrado com ID: " + matchId));
    }

    private Conversation getOrCreateConversation(JobMatch match) {
        return conversationRepository.findByMatch(match)
                .orElseGet(() -> conversationRepository.save(Conversation.builder().match(match).build()));
    }

    private void validateParticipant(User user, JobMatch match) {
        if (user.getRole() == Role.CANDIDATE) {
            if (!match.getCandidate().getUser().getId().equals(user.getId())) {
                throw new UnauthorizedException("Acesso negado: você não participa deste match");
            }
        } else if (user.getRole() == Role.COMPANY) {
            if (!match.getCompany().getId().equals(user.getCompanyId())) {
                throw new UnauthorizedException("Acesso negado: sua empresa não participa deste match");
            }
        }
    }

    private ConversationResponse.ChatMessageResponse mapToMessageResponse(ChatMessage message) {
        String senderName = message.getSenderRole() == Role.COMPANY ?
                message.getConversation().getMatch().getCompany().getName() :
                message.getConversation().getMatch().getCandidate().getName();

        return new ConversationResponse.ChatMessageResponse(
                message.getId(),
                message.getSender().getId(),
                senderName,
                message.getText(),
                message.getCreatedAt()
        );
    }
}
