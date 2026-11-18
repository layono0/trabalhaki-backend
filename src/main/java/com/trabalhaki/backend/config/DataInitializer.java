package com.trabalhaki.backend.config;

import com.trabalhaki.backend.domain.enums.*;
import com.trabalhaki.backend.domain.model.*;
import com.trabalhaki.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CompanyRepository companyRepository;
    private final SkillRepository skillRepository;
    private final JobRepository jobRepository;
    private final CandidateLikeRepository candidateLikeRepository;
    private final CompanyLikeRepository companyLikeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedSkills();

        Company savedCompany = null;
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
            savedCompany = companyRepository.save(company);

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
        } else {
            savedCompany = companyRepository.findByCnpj("33000167000101").orElse(null);
        }

        CandidateProfile savedProfile = null;
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
                    .minimumSalaryExpectation(BigDecimal.valueOf(4000.00))
                    .preferredModality(WorkModality.REMOTE)
                    .preferredContractType("CLT / PJ")
                    .completionPercentage(85)
                    .build();
            savedProfile = candidateProfileRepository.save(profile);
            log.info("Mock candidate account and profile seeded: candidato@trabalhaki.com");
        } else {
            savedProfile = candidateProfileRepository.findAll().stream().findFirst().orElse(null);
        }

        // Link candidate skills if empty
        if (savedProfile != null && (savedProfile.getSkills() == null || savedProfile.getSkills().isEmpty())) {
            final CandidateProfile targetCandidate = savedProfile;
            List<String> skillsToLink = List.of("Java", "Spring Boot", "SQL", "Docker", "Git", "REST API", "Flutter");
            List<CandidateSkill> candidateSkills = new ArrayList<>();
            for (String skillName : skillsToLink) {
                skillRepository.findByNameIgnoreCase(skillName).ifPresent(skill -> {
                    candidateSkills.add(CandidateSkill.builder()
                            .candidate(targetCandidate)
                            .skill(skill)
                            .build());
                });
            }
            savedProfile.getSkills().addAll(candidateSkills);
            candidateProfileRepository.save(savedProfile);
            log.info("Candidate skills linked for João Silva: {}", skillsToLink);
        }

        // Seed demo jobs if none exist (keep max 2 active REMOTE jobs for Tech Corp Brasil so free-plan tests can still add a 3rd)
        Job job1 = null;
        Job job2 = null;
        if (savedCompany != null) {
            List<Job> existingCompanyJobs = jobRepository.findByCompany(savedCompany);
            if (existingCompanyJobs.isEmpty()) {
                job1 = Job.builder()
                        .company(savedCompany)
                        .title("Desenvolvedor Java Backend Pleno")
                        .description("Venha fazer parte do nosso time de engenharia backend desenvolvendo microsserviços escaláveis com Spring Boot e Docker.")
                        .minimumSalary(BigDecimal.valueOf(5000.00))
                        .maximumSalary(BigDecimal.valueOf(7500.00))
                        .modality(WorkModality.REMOTE)
                        .city("São Paulo")
                        .state("SP")
                        .country("Brasil")
                        .contractType("CLT")
                        .seniorityLevel("Pleno")
                        .experienceYears(2)
                        .status(JobStatus.ACTIVE)
                        .benefits(List.of("Vale Refeição R$ 1.200/mês", "Plano de Saúde Bradesco Top", "Auxílio Home Office R$ 350", "Gympass"))
                        .build();
                job1 = jobRepository.save(job1);
                attachJobSkills(job1, List.of("Java", "Spring Boot", "SQL"), List.of("Docker", "AWS"));

                job2 = Job.builder()
                        .company(savedCompany)
                        .title("Pessoa Desenvolvedora Full Stack React & Java")
                        .description("Atue na evolução da plataforma web e APIs de alta disponibilidade em squads multidisciplinares 100% remotas.")
                        .minimumSalary(BigDecimal.valueOf(5500.00))
                        .maximumSalary(BigDecimal.valueOf(8500.00))
                        .modality(WorkModality.REMOTE)
                        .city("São Paulo")
                        .state("SP")
                        .country("Brasil")
                        .contractType("CLT")
                        .seniorityLevel("Pleno")
                        .experienceYears(3)
                        .status(JobStatus.ACTIVE)
                        .benefits(List.of("Vale Refeição R$ 1.200/mês", "Plano de Saúde Nacional", "Auxílio Educação", "Horário Flexível"))
                        .build();
                job2 = jobRepository.save(job2);
                attachJobSkills(job2, List.of("React", "TypeScript", "Java"), List.of("REST API", "Docker"));

                log.info("2 Demo jobs created for company: {}", savedCompany.getName());

                // Pre-seed company interest on João Silva for job1 to enable instant match demonstration in mobile app
                if (savedProfile != null && !companyLikeRepository.existsByCompanyAndJobAndCandidate(savedCompany, job1, savedProfile)) {
                    CompanyLike companyLike = CompanyLike.builder()
                            .company(savedCompany)
                            .job(job1)
                            .candidate(savedProfile)
                            .build();
                    companyLikeRepository.save(companyLike);
                    log.info("Company like pre-registered for candidate {} on job '{}'", savedProfile.getName(), job1.getTitle());
                }
            } else {
                job1 = existingCompanyJobs.get(0);
                if (existingCompanyJobs.size() > 1) {
                    job2 = existingCompanyJobs.get(1);
                }
            }
        }

        // Seed additional demo candidates who already applied to the demo jobs (visible in Employer Swipe)
        if (job1 != null) {
            seedDemoApplicant(
                    "ana.costa@trabalhaki.com",
                    "Ana Clara Costa",
                    "Engenheira de Software Backend",
                    "Tecnologia",
                    4,
                    "Engenharia de Software",
                    "Belo Horizonte",
                    "MG",
                    BigDecimal.valueOf(6200.00),
                    WorkModality.REMOTE,
                    "CLT",
                    95,
                    List.of("Java", "Spring Boot", "SQL", "Docker", "AWS", "Microservices"),
                    job2 != null ? List.of(job1, job2) : List.of(job1)
            );
            seedDemoApplicant(
                    "lucas.mendes@trabalhaki.com",
                    "Lucas Henrique Mendes",
                    "Desenvolvedor Full Stack",
                    "Tecnologia",
                    3,
                    "Sistemas de Informação",
                    "Curitiba",
                    "PR",
                    BigDecimal.valueOf(5800.00),
                    WorkModality.REMOTE,
                    "CLT / PJ",
                    90,
                    List.of("React", "TypeScript", "Node.js", "Java", "REST API", "PostgreSQL"),
                    job2 != null ? List.of(job1, job2) : List.of(job1)
            );
            seedDemoApplicant(
                    "mariana.rocha@trabalhaki.com",
                    "Mariana Rocha",
                    "Desenvolvedora Java Júnior",
                    "Tecnologia",
                    1,
                    "Análise e Desenvolvimento de Sistemas",
                    "Campinas",
                    "SP",
                    BigDecimal.valueOf(4200.00),
                    WorkModality.HYBRID,
                    "CLT",
                    80,
                    List.of("Java", "SQL", "Git", "REST API"),
                    List.of(job1)
            );
            seedDemoApplicant(
                    "pedro.almeida@trabalhaki.com",
                    "Pedro Almeida",
                    "Desenvolvedor Front-end React",
                    "Tecnologia",
                    3,
                    "Ciência da Computação",
                    "Florianópolis",
                    "SC",
                    BigDecimal.valueOf(6500.00),
                    WorkModality.REMOTE,
                    "PJ",
                    85,
                    List.of("React", "TypeScript", "Git", "Docker", "REST API"),
                    job2 != null ? List.of(job2) : List.of(job1)
            );
        }
    }

    private void seedDemoApplicant(
            String email,
            String name,
            String desiredRole,
            String area,
            int experienceYears,
            String education,
            String city,
            String state,
            BigDecimal minimumSalaryExpectation,
            WorkModality preferredModality,
            String preferredContractType,
            int completionPercentage,
            List<String> skillNames,
            List<Job> appliedJobs
    ) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("123456"))
                .role(Role.CANDIDATE)
                .active(true)
                .build());

        CandidateProfile profile = candidateProfileRepository.save(CandidateProfile.builder()
                .user(user)
                .name(name)
                .desiredRole(desiredRole)
                .area(area)
                .experienceYears(experienceYears)
                .education(education)
                .city(city)
                .state(state)
                .country("Brasil")
                .minimumSalaryExpectation(minimumSalaryExpectation)
                .preferredModality(preferredModality)
                .preferredContractType(preferredContractType)
                .completionPercentage(completionPercentage)
                .build());

        List<CandidateSkill> candidateSkills = new ArrayList<>();
        for (String skillName : skillNames) {
            skillRepository.findByNameIgnoreCase(skillName).ifPresent(skill ->
                    candidateSkills.add(CandidateSkill.builder()
                            .candidate(profile)
                            .skill(skill)
                            .build())
            );
        }
        profile.getSkills().addAll(candidateSkills);
        CandidateProfile savedProfile = candidateProfileRepository.save(profile);

        for (Job job : appliedJobs) {
            if (!candidateLikeRepository.existsByCandidateAndJob(savedProfile, job)) {
                candidateLikeRepository.save(CandidateLike.builder()
                        .candidate(savedProfile)
                        .job(job)
                        .build());
            }
        }
    }

    private void attachJobSkills(Job job, List<String> required, List<String> preferred) {
        List<JobSkill> skills = new ArrayList<>();
        for (String sName : required) {
            skillRepository.findByNameIgnoreCase(sName).ifPresent(skill -> {
                skills.add(JobSkill.builder().job(job).skill(skill).type(SkillType.REQUIRED).build());
            });
        }
        for (String sName : preferred) {
            skillRepository.findByNameIgnoreCase(sName).ifPresent(skill -> {
                skills.add(JobSkill.builder().job(job).skill(skill).type(SkillType.PREFERRED).build());
            });
        }
        job.getSkills().addAll(skills);
        jobRepository.save(job);
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
