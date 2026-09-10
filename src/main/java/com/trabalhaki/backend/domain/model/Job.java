package com.trabalhaki.backend.domain.model;

import com.trabalhaki.backend.domain.enums.JobStatus;
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
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // Structured Salary (Obligatory min and max)
    @Column(name = "minimum_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumSalary;

    @Column(name = "maximum_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal maximumSalary;

    // Modality (Exactly one of REMOTE, HYBRID, ONSITE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkModality modality;

    // Location
    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 50)
    private String country;

    private Double latitude;
    private Double longitude;

    @Column(name = "contract_type", nullable = false, length = 50)
    private String contractType; // e.g. CLT, PJ

    @Column(name = "seniority_level", nullable = false, length = 50)
    private String seniorityLevel; // e.g. Júnior, Pleno, Sênior

    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_benefits", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "benefit", length = 150)
    @Builder.Default
    private List<String> benefits = new ArrayList<>();

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JobSkill> skills = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
