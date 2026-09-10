package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.JobStatus;
import com.trabalhaki.backend.domain.enums.PlanType;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.enums.WorkModality;
import com.trabalhaki.backend.domain.model.Company;
import com.trabalhaki.backend.domain.model.Job;
import com.trabalhaki.backend.domain.model.JobSkill;
import com.trabalhaki.backend.domain.model.Skill;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.dto.job.CreateJobRequest;
import com.trabalhaki.backend.dto.job.JobResponse;
import com.trabalhaki.backend.dto.job.JobSkillRequest;
import com.trabalhaki.backend.dto.job.UpdateJobRequest;
import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.CompanyRepository;
import com.trabalhaki.backend.repository.JobRepository;
import com.trabalhaki.backend.repository.SkillRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    public static final int FREE_PLAN_MAX_ACTIVE_JOBS = 3;

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;

    @Transactional
    public JobResponse createJob(String userEmail, CreateJobRequest request) {
        User user = getCompanyUser(userEmail);
        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada"));

        // Validate salary consistency
        if (request.minimumSalary().compareTo(request.maximumSalary()) > 0) {
            throw new BusinessRuleException("O salário mínimo não pode ser maior que o salário máximo");
        }

        // Validate location for HYBRID or ONSITE
        if ((request.modality() == WorkModality.ONSITE || request.modality() == WorkModality.HYBRID)
                && (request.city() == null || request.city().isBlank())
                && (request.state() == null || request.state().isBlank())) {
            throw new BusinessRuleException("Cidade ou Estado são obrigatórios para vagas presenciais ou híbridas");
        }

        // Enforce plan limits strictly on backend (Rule 14 & 39)
        if (company.getPlanType() == PlanType.FREE) {
            long activeJobsCount = jobRepository.countByCompanyAndStatus(company, JobStatus.ACTIVE);
            if (activeJobsCount >= FREE_PLAN_MAX_ACTIVE_JOBS) {
                throw new BusinessRuleException("Limite de " + FREE_PLAN_MAX_ACTIVE_JOBS +
                        " vagas ativas atingido para o plano gratuito. Faça upgrade para o plano Premium para publicar vagas ilimitadas.");
            }
        }

        Job job = Job.builder()
                .company(company)
                .title(request.title().trim())
                .description(request.description().trim())
                .minimumSalary(request.minimumSalary())
                .maximumSalary(request.maximumSalary())
                .modality(request.modality())
                .city(request.city() != null ? request.city().trim() : null)
                .state(request.state() != null ? request.state().trim() : null)
                .country(request.country() != null ? request.country().trim() : "Brasil")
                .latitude(request.latitude())
                .longitude(request.longitude())
                .contractType(request.contractType().trim())
                .seniorityLevel(request.seniorityLevel().trim())
                .experienceYears(request.experienceYears())
                .status(JobStatus.ACTIVE)
                .benefits(request.benefits() != null ? new ArrayList<>(request.benefits()) : new ArrayList<>())
                .version(1)
                .build();

        attachSkills(job, request.skills());

        Job savedJob = jobRepository.save(job);
        return mapToResponse(savedJob);
    }

    @Transactional(readOnly = true)
    public JobResponse getJobById(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada com ID: " + jobId));
        return mapToResponse(job);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listActiveJobs() {
        return jobRepository.findByStatus(JobStatus.ACTIVE)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listCompanyJobs(String userEmail) {
        User user = getCompanyUser(userEmail);
        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada"));
        return jobRepository.findByCompany(company)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public JobResponse updateJob(String userEmail, Long jobId, UpdateJobRequest request) {
        User user = getCompanyUser(userEmail);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada com ID: " + jobId));

        if (!job.getCompany().getId().equals(user.getCompanyId())) {
            throw new UnauthorizedException("Apenas membros da empresa responsável podem editar esta vaga");
        }

        // If activating a paused/closed job, re-enforce free plan limit
        if (request.status() == JobStatus.ACTIVE && job.getStatus() != JobStatus.ACTIVE) {
            Company company = job.getCompany();
            if (company.getPlanType() == PlanType.FREE) {
                long activeCount = jobRepository.countByCompanyAndStatus(company, JobStatus.ACTIVE);
                if (activeCount >= FREE_PLAN_MAX_ACTIVE_JOBS) {
                    throw new BusinessRuleException("Limite de " + FREE_PLAN_MAX_ACTIVE_JOBS +
                            " vagas ativas atingido para o plano gratuito.");
                }
            }
        }

        boolean hadMeaningfulChange = false;

        if (request.title() != null && !request.title().isBlank()) {
            job.setTitle(request.title().trim());
            hadMeaningfulChange = true;
        }
        if (request.description() != null && !request.description().isBlank()) {
            job.setDescription(request.description().trim());
            hadMeaningfulChange = true;
        }
        if (request.minimumSalary() != null && request.maximumSalary() != null) {
            if (request.minimumSalary().compareTo(request.maximumSalary()) > 0) {
                throw new BusinessRuleException("O salário mínimo não pode ser maior que o salário máximo");
            }
            job.setMinimumSalary(request.minimumSalary());
            job.setMaximumSalary(request.maximumSalary());
            hadMeaningfulChange = true;
        }
        if (request.modality() != null) {
            job.setModality(request.modality());
            hadMeaningfulChange = true;
        }
        if (request.city() != null) job.setCity(request.city().trim());
        if (request.state() != null) job.setState(request.state().trim());
        if (request.country() != null) job.setCountry(request.country().trim());
        if (request.latitude() != null) job.setLatitude(request.latitude());
        if (request.longitude() != null) job.setLongitude(request.longitude());
        if (request.contractType() != null) job.setContractType(request.contractType().trim());
        if (request.seniorityLevel() != null) job.setSeniorityLevel(request.seniorityLevel().trim());
        if (request.experienceYears() != null) job.setExperienceYears(request.experienceYears());

        if (request.status() != null) {
            job.setStatus(request.status());
        }

        if (request.benefits() != null) {
            job.setBenefits(new ArrayList<>(request.benefits()));
        }

        if (request.skills() != null) {
            job.getSkills().clear();
            attachSkills(job, request.skills());
            hadMeaningfulChange = true;
        }

        // Increment version when job requirements/details are edited (Rule 22)
        if (hadMeaningfulChange) {
            job.setVersion(job.getVersion() + 1);
        }

        Job savedJob = jobRepository.save(job);
        return mapToResponse(savedJob);
    }

    @Transactional
    public JobResponse closeJob(String userEmail, Long jobId) {
        User user = getCompanyUser(userEmail);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada com ID: " + jobId));

        if (!job.getCompany().getId().equals(user.getCompanyId())) {
            throw new UnauthorizedException("Apenas membros da empresa responsável podem encerrar esta vaga");
        }

        job.setStatus(JobStatus.CLOSED);
        Job savedJob = jobRepository.save(job);
        return mapToResponse(savedJob);
    }

    private void attachSkills(Job job, List<JobSkillRequest> skillsRequest) {
        if (skillsRequest == null) return;

        for (JobSkillRequest skillReq : skillsRequest) {
            String cleanName = skillReq.skillName().trim();
            if (!cleanName.isEmpty()) {
                Skill skill = skillRepository.findByNameIgnoreCase(cleanName)
                        .orElseGet(() -> skillRepository.save(Skill.builder().name(cleanName).build()));

                JobSkill jobSkill = JobSkill.builder()
                        .job(job)
                        .skill(skill)
                        .type(skillReq.skillType())
                        .build();
                job.getSkills().add(jobSkill);
            }
        }
    }

    private User getCompanyUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.getRole() != Role.COMPANY) {
            throw new UnauthorizedException("Apenas usuários empresariais podem gerenciar vagas");
        }
        if (user.getCompanyId() == null) {
            throw new BusinessRuleException("Usuário não está vinculado a uma empresa");
        }
        return user;
    }

    public JobResponse mapToResponse(Job job) {
        List<JobResponse.JobSkillResponse> skillResponses = new ArrayList<>();
        if (job.getSkills() != null) {
            for (JobSkill js : job.getSkills()) {
                if (js.getSkill() != null) {
                    skillResponses.add(new JobResponse.JobSkillResponse(
                            js.getSkill().getName(),
                            js.getType()
                    ));
                }
            }
        }

        return new JobResponse(
                job.getId(),
                job.getCompany().getId(),
                job.getCompany().getName(),
                job.getCompany().getLogoUrl(),
                job.getTitle(),
                job.getDescription(),
                job.getMinimumSalary(),
                job.getMaximumSalary(),
                job.getModality(),
                job.getCity(),
                job.getState(),
                job.getCountry(),
                job.getContractType(),
                job.getSeniorityLevel(),
                job.getExperienceYears(),
                job.getStatus(),
                job.getBenefits() != null ? new ArrayList<>(job.getBenefits()) : List.of(),
                skillResponses,
                job.getVersion(),
                job.getCreatedAt()
        );
    }
}
