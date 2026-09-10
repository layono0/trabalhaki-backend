package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.CandidateSkill;
import com.trabalhaki.backend.domain.model.Skill;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.dto.candidate.CandidateProfileResponse;
import com.trabalhaki.backend.dto.candidate.UpdateCandidateProfileRequest;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.CandidateProfileRepository;
import com.trabalhaki.backend.repository.SkillRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;

    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfileByEmail(String email) {
        User user = getUserByEmail(email);
        CandidateProfile profile = getProfileByUser(user);
        return mapToResponse(profile);
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfileById(Long candidateId) {
        CandidateProfile profile = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de candidato não encontrado"));
        return mapToResponse(profile);
    }

    @Transactional
    public CandidateProfileResponse updateProfile(String email, UpdateCandidateProfileRequest request) {
        User user = getUserByEmail(email);
        CandidateProfile profile = getProfileByUser(user);

        if (request.name() != null && !request.name().isBlank()) {
            profile.setName(request.name().trim());
        }
        if (request.photoUrl() != null) {
            profile.setPhotoUrl(request.photoUrl().trim());
        }
        if (request.desiredRole() != null) {
            profile.setDesiredRole(request.desiredRole().trim());
        }
        if (request.area() != null) {
            profile.setArea(request.area().trim());
        }
        if (request.experienceYears() != null) {
            profile.setExperienceYears(request.experienceYears());
        }
        if (request.education() != null) {
            profile.setEducation(request.education().trim());
        }
        if (request.city() != null) {
            profile.setCity(request.city().trim());
        }
        if (request.state() != null) {
            profile.setState(request.state().trim());
        }
        if (request.country() != null) {
            profile.setCountry(request.country().trim());
        }
        if (request.minimumSalaryExpectation() != null) {
            profile.setMinimumSalaryExpectation(request.minimumSalaryExpectation());
        }
        if (request.preferredModality() != null) {
            profile.setPreferredModality(request.preferredModality());
        }
        if (request.preferredContractType() != null) {
            profile.setPreferredContractType(request.preferredContractType().trim());
        }

        // Manage skills
        if (request.skillNames() != null) {
            profile.getSkills().clear();
            for (String skillName : request.skillNames()) {
                String cleanName = skillName.trim();
                if (!cleanName.isEmpty()) {
                    Skill skill = skillRepository.findByNameIgnoreCase(cleanName)
                            .orElseGet(() -> skillRepository.save(Skill.builder().name(cleanName).build()));

                    CandidateSkill candidateSkill = CandidateSkill.builder()
                            .candidate(profile)
                            .skill(skill)
                            .build();
                    profile.getSkills().add(candidateSkill);
                }
            }
        }

        profile.setCompletionPercentage(calculateCompletionPercentage(profile));

        CandidateProfile savedProfile = candidateProfileRepository.save(profile);
        return mapToResponse(savedProfile);
    }

    private User getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.getRole() != Role.CANDIDATE) {
            throw new UnauthorizedException("Apenas candidatos podem acessar este perfil");
        }
        return user;
    }

    private CandidateProfile getProfileByUser(User user) {
        return candidateProfileRepository.findByUser(user)
                .orElseGet(() -> candidateProfileRepository.save(
                        CandidateProfile.builder()
                                .user(user)
                                .name("Candidato")
                                .completionPercentage(15)
                                .build()
                ));
    }

    private int calculateCompletionPercentage(CandidateProfile profile) {
        int percentage = 0;
        if (profile.getName() != null && !profile.getName().isBlank()) percentage += 15;
        if (profile.getDesiredRole() != null && !profile.getDesiredRole().isBlank()) percentage += 15;
        if (profile.getArea() != null && !profile.getArea().isBlank()) percentage += 10;
        if (profile.getExperienceYears() != null) percentage += 15;
        if (profile.getEducation() != null && !profile.getEducation().isBlank()) percentage += 10;
        if (profile.getCity() != null && !profile.getCity().isBlank()) percentage += 10;
        if (profile.getMinimumSalaryExpectation() != null) percentage += 10;
        if (profile.getPreferredModality() != null) percentage += 5;
        if (profile.getSkills() != null && !profile.getSkills().isEmpty()) percentage += 10;
        return Math.min(100, percentage);
    }

    private CandidateProfileResponse mapToResponse(CandidateProfile profile) {
        List<String> skillNames = new ArrayList<>();
        if (profile.getSkills() != null) {
            for (CandidateSkill cs : profile.getSkills()) {
                if (cs.getSkill() != null) {
                    skillNames.add(cs.getSkill().getName());
                }
            }
        }

        return new CandidateProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getName(),
                profile.getPhotoUrl(),
                profile.getDesiredRole(),
                profile.getArea(),
                profile.getExperienceYears(),
                profile.getEducation(),
                profile.getCity(),
                profile.getState(),
                profile.getCountry(),
                profile.getMinimumSalaryExpectation(),
                profile.getPreferredModality(),
                profile.getPreferredContractType(),
                profile.getCompletionPercentage(),
                skillNames,
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
