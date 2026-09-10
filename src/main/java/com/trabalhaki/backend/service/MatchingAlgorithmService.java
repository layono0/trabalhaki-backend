package com.trabalhaki.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trabalhaki.backend.domain.enums.SkillType;
import com.trabalhaki.backend.domain.enums.WorkModality;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.CandidateSkill;
import com.trabalhaki.backend.domain.model.Job;
import com.trabalhaki.backend.domain.model.JobSkill;
import com.trabalhaki.backend.dto.match.CompatibilityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchingAlgorithmService {

    // Weight configuration (Total: 100 points)
    private static final double REQUIRED_SKILLS_WEIGHT = 35.0;
    private static final double PREFERRED_SKILLS_WEIGHT = 15.0;
    private static final double EXPERIENCE_WEIGHT = 20.0;
    private static final double SALARY_WEIGHT = 15.0;
    private static final double MODALITY_LOCATION_WEIGHT = 15.0;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompatibilityResult calculateCompatibility(Job job, CandidateProfile candidate) {
        Map<String, String> explanation = new LinkedHashMap<>();

        double skillsScore = evaluateSkills(job, candidate, explanation);
        double experienceScore = evaluateExperience(job, candidate, explanation);
        double salaryScore = evaluateSalary(job, candidate, explanation);
        double modalityLocationScore = evaluateModalityAndLocation(job, candidate, explanation);

        double totalScore = skillsScore + experienceScore + salaryScore + modalityLocationScore;
        int normalizedScore = (int) Math.round(Math.max(0, Math.min(100, totalScore)));

        String jsonExplanation = "{}";
        try {
            jsonExplanation = objectMapper.writeValueAsString(explanation);
        } catch (Exception e) {
            log.error("Erro ao serializar explicação de compatibilidade: {}", e.getMessage());
        }

        return new CompatibilityResult(normalizedScore, explanation, jsonExplanation);
    }

    private double evaluateSkills(Job job, CandidateProfile candidate, Map<String, String> explanation) {
        Set<String> candidateSkillNames = candidate.getSkills() == null ? Set.of() :
                candidate.getSkills().stream()
                        .map(cs -> cs.getSkill().getName().trim().toLowerCase())
                        .collect(Collectors.toSet());

        List<JobSkill> jobSkills = job.getSkills() == null ? List.of() : job.getSkills();

        List<JobSkill> requiredSkills = jobSkills.stream()
                .filter(js -> js.getType() == SkillType.REQUIRED)
                .toList();

        List<JobSkill> preferredSkills = jobSkills.stream()
                .filter(js -> js.getType() == SkillType.PREFERRED)
                .toList();

        double requiredScore = REQUIRED_SKILLS_WEIGHT;
        int matchedRequired = 0;
        if (!requiredSkills.isEmpty()) {
            for (JobSkill js : requiredSkills) {
                if (candidateSkillNames.contains(js.getSkill().getName().trim().toLowerCase())) {
                    matchedRequired++;
                }
            }
            requiredScore = (double) matchedRequired / requiredSkills.size() * REQUIRED_SKILLS_WEIGHT;
        }

        double preferredScore = PREFERRED_SKILLS_WEIGHT;
        int matchedPreferred = 0;
        if (!preferredSkills.isEmpty()) {
            for (JobSkill js : preferredSkills) {
                if (candidateSkillNames.contains(js.getSkill().getName().trim().toLowerCase())) {
                    matchedPreferred++;
                }
            }
            preferredScore = (double) matchedPreferred / preferredSkills.size() * PREFERRED_SKILLS_WEIGHT;
        }

        String skillMsg = String.format("Habilidades: atende %d de %d obrigatórias e %d de %d diferenciais",
                matchedRequired, requiredSkills.size(),
                matchedPreferred, preferredSkills.size());
        explanation.put("skills", skillMsg);

        return requiredScore + preferredScore;
    }

    private double evaluateExperience(Job job, CandidateProfile candidate, Map<String, String> explanation) {
        int reqExp = job.getExperienceYears() != null ? job.getExperienceYears() : 0;
        int candExp = candidate.getExperienceYears() != null ? candidate.getExperienceYears() : 0;

        if (reqExp == 0) {
            explanation.put("experience", "Experiência compatível (sem requisito mínimo de anos exigido)");
            return EXPERIENCE_WEIGHT;
        }

        if (candExp >= reqExp) {
            explanation.put("experience", String.format("Experiência atende integralmente ao requisito (%d anos vs %d anos exigidos)", candExp, reqExp));
            return EXPERIENCE_WEIGHT;
        }

        // Proportional partial score for candidate with less experience (rule: never hard-block)
        double ratio = (double) candExp / reqExp;
        double score = ratio * EXPERIENCE_WEIGHT;
        explanation.put("experience", String.format("Experiência de %d ano(s) para requisito de %d ano(s) (compatibilidade parcial)", candExp, reqExp));
        return score;
    }

    private double evaluateSalary(Job job, CandidateProfile candidate, Map<String, String> explanation) {
        BigDecimal candSalary = candidate.getMinimumSalaryExpectation();
        BigDecimal jobMin = job.getMinimumSalary();
        BigDecimal jobMax = job.getMaximumSalary();

        if (candSalary == null || candSalary.compareTo(BigDecimal.ZERO) <= 0) {
            explanation.put("salary", "Faixa salarial dentro das preferências gerais");
            return SALARY_WEIGHT;
        }

        // Scope overlap: if candidate minimum expectation is within or below the job offer range
        if (candSalary.compareTo(jobMax) <= 0) {
            explanation.put("salary", String.format("Pretensão salarial de R$ %.2f está dentro ou abaixo da faixa da vaga (R$ %.2f - R$ %.2f)",
                    candSalary, jobMin, jobMax));
            return SALARY_WEIGHT;
        }

        // Partial score if slightly above maximum budget (never hard block)
        double differenceRatio = candSalary.subtract(jobMax).doubleValue() / jobMax.doubleValue();
        if (differenceRatio <= 0.25) {
            explanation.put("salary", String.format("Pretensão de R$ %.2f ligeiramente acima da faixa máxima de R$ %.2f (compatibilidade moderada)",
                    candSalary, jobMax));
            return SALARY_WEIGHT * 0.6;
        } else if (differenceRatio <= 0.50) {
            explanation.put("salary", String.format("Pretensão de R$ %.2f acima da faixa máxima de R$ %.2f", candSalary, jobMax));
            return SALARY_WEIGHT * 0.3;
        }

        explanation.put("salary", String.format("Pretensão de R$ %.2f consideravelmente acima da faixa máxima de R$ %.2f", candSalary, jobMax));
        return SALARY_WEIGHT * 0.1;
    }

    private double evaluateModalityAndLocation(Job job, CandidateProfile candidate, Map<String, String> explanation) {
        // REMOTE: Rule 11 & 31: Location must NOT negatively influence score for remote jobs
        if (job.getModality() == WorkModality.REMOTE) {
            explanation.put("modality", "Modalidade Remota: compatibilidade geográfica total");
            explanation.put("location", "Sem restrição geográfica aplicável");
            return MODALITY_LOCATION_WEIGHT;
        }

        // HYBRID or ONSITE: Location is relevant
        double modalityPoints = (job.getModality() == candidate.getPreferredModality()) ?
                MODALITY_LOCATION_WEIGHT * 0.5 : MODALITY_LOCATION_WEIGHT * 0.25;

        double locationPoints = 0.0;
        boolean sameCity = job.getCity() != null && candidate.getCity() != null &&
                job.getCity().equalsIgnoreCase(candidate.getCity().trim());
        boolean sameState = job.getState() != null && candidate.getState() != null &&
                job.getState().equalsIgnoreCase(candidate.getState().trim());

        if (sameCity) {
            locationPoints = MODALITY_LOCATION_WEIGHT * 0.5;
            explanation.put("location", String.format("Mesma cidade (%s, %s)", job.getCity(), job.getState()));
        } else if (sameState) {
            locationPoints = MODALITY_LOCATION_WEIGHT * 0.25;
            explanation.put("location", String.format("Mesmo estado (%s)", job.getState()));
        } else {
            locationPoints = MODALITY_LOCATION_WEIGHT * 0.1;
            explanation.put("location", "Localização em outro estado/região");
        }

        explanation.put("modality", String.format("Modalidade da vaga: %s (preferência do candidato: %s)",
                job.getModality(), candidate.getPreferredModality() != null ? candidate.getPreferredModality() : "Não especificada"));

        return modalityPoints + locationPoints;
    }
}
