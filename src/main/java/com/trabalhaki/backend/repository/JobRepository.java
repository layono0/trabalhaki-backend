package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.enums.JobStatus;
import com.trabalhaki.backend.domain.model.Company;
import com.trabalhaki.backend.domain.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByStatus(JobStatus status);
    List<Job> findByCompany(Company company);
    List<Job> findByCompanyAndStatus(Company company, JobStatus status);
    long countByCompanyAndStatus(Company company, JobStatus status);
}
