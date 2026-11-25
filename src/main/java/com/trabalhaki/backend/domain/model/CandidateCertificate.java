package com.trabalhaki.backend.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "candidate_certificates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private CandidateProfile candidate;

    @Column(name = "file_sha256", nullable = false, unique = true, length = 64)
    private String fileSha256;

    @Column(name = "credential_code", length = 180)
    private String credentialCode;

    @Column(name = "course_title", length = 200)
    private String courseTitle;

    @Column(name = "institution_name", nullable = false, length = 150)
    private String institutionName;

    @Column(name = "institution_tier", nullable = false)
    private Integer institutionTier;

    @Column(name = "verified_skills_csv", nullable = false, length = 500)
    private String verifiedSkillsCsv;

    @Column(name = "workload_hours")
    private Integer workloadHours;

    @Column(name = "priority_bonus", nullable = false)
    private Integer priorityBonus;

    @Column(name = "trust_score", nullable = false)
    private Integer trustScore;

    @Column(name = "fraud_risk_score", nullable = false)
    private Integer fraudRiskScore;

    @Column(name = "verification_summary", length = 500)
    private String verificationSummary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public List<String> getVerifiedSkillsList() {
        if (verifiedSkillsCsv == null || verifiedSkillsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(verifiedSkillsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
