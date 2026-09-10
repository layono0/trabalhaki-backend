package com.trabalhaki.backend.domain.model;

import com.trabalhaki.backend.domain.enums.WorkModality;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "desired_role", length = 100)
    private String desiredRole;

    @Column(length = 100)
    private String area;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(length = 150)
    private String education;

    // Structured Location
    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 50)
    private String country;

    private Double latitude;
    private Double longitude;

    // Structured Compensation Expectation
    @Column(name = "minimum_salary_expectation", precision = 10, scale = 2)
    private BigDecimal minimumSalaryExpectation;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_modality", length = 20)
    private WorkModality preferredModality;

    @Column(name = "preferred_contract_type", length = 50)
    private String preferredContractType;

    @Column(name = "completion_percentage")
    @Builder.Default
    private Integer completionPercentage = 0;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CandidateSkill> skills = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
