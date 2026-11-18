package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.Company;
import com.trabalhaki.backend.domain.model.CompanyPass;
import com.trabalhaki.backend.domain.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyPassRepository extends JpaRepository<CompanyPass, Long> {

    Optional<CompanyPass> findByCompanyAndJobAndCandidate(Company company, Job job, CandidateProfile candidate);

    boolean existsByCompanyAndJobAndCandidate(Company company, Job job, CandidateProfile candidate);

    List<CompanyPass> findByJob(Job job);
}
