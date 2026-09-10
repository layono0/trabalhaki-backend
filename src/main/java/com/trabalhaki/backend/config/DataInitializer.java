package com.trabalhaki.backend.config;

import com.trabalhaki.backend.domain.enums.CompanyRole;
import com.trabalhaki.backend.domain.enums.PlanType;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.enums.VerificationStatus;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.Company;
import com.trabalhaki.backend.domain.model.Skill;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.repository.CandidateProfileRepository;
import com.trabalhaki.backend.repository.CompanyRepository;
import com.trabalhaki.backend.repository.SkillRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CompanyRepository companyRepository;
    private final SkillRepository skillRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedSkills();

        // Seed default company and admin account if not exists
        if (!userRepository.existsByEmail("empresa@trabalhaki.com")) {
            Company company = Company.builder()
                    .name("Tech Corp Brasil")
                    .cnpj("33000167000101")
                    .description("Empresa de tecnologia focada em inovação e produtos digitais.")
                    .industry("Tecnologia da Informação")
                    .companySize("50-200")
                    .city("São Paulo")
                    .state("SP")
                    .country("Brasil")
                    .latitude(-23.5505)
                    .longitude(-46.6333)
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .planType(PlanType.FREE)
                    .active(true)
                    .build();
            Company savedCompany = companyRepository.save(company);

            User companyAdmin = User.builder()
                    .email("empresa@trabalhaki.com")
                    .password(passwordEncoder.encode("123456"))
                    .role(Role.COMPANY)
                    .companyRole(CompanyRole.ADMIN)
                    .companyId(savedCompany.getId())
                    .active(true)
                    .build();
            userRepository.save(companyAdmin);
            log.info("Mock company and admin account seeded: empresa@trabalhaki.com");
        }

        // Seed default candidate account and profile if not exists
        if (!userRepository.existsByEmail("candidato@trabalhaki.com")) {
            User candidate = User.builder()
                    .email("candidato@trabalhaki.com")
                    .password(passwordEncoder.encode("123456"))
                    .role(Role.CANDIDATE)
                    .active(true)
                    .build();
            User savedCandidate = userRepository.save(candidate);

            CandidateProfile profile = CandidateProfile.builder()
                    .user(savedCandidate)
                    .name("João Silva")
                    .desiredRole("Desenvolvedor Backend")
                    .area("Tecnologia")
                    .experienceYears(3)
                    .city("São Paulo")
                    .state("SP")
                    .country("Brasil")
                    .latitude(-23.5505)
                    .longitude(-46.6333)
                    .completionPercentage(80)
                    .build();
            candidateProfileRepository.save(profile);
            log.info("Mock candidate account and profile seeded: candidato@trabalhaki.com");
        }
    }

    private void seedSkills() {
        List<String> defaultSkills = List.of(
                "Java", "Spring Boot", "Flutter", "Dart", "Python",
                "SQL", "Docker", "Git", "AWS", "REST API", "PostgreSQL",
                "React", "Node.js", "TypeScript", "Microservices"
        );

        for (String skillName : defaultSkills) {
            if (skillRepository.findByNameIgnoreCase(skillName).isEmpty()) {
                skillRepository.save(Skill.builder().name(skillName).build());
            }
        }
        log.info("Default skills catalog seeded (total: {})", defaultSkills.size());
    }
}
