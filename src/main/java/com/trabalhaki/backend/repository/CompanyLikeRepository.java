package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.Company;
import com.trabalhaki.backend.domain.model.CompanyLike;
import com.trabalhaki.backend.domain.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyLikeRepository extends JpaRepository<CompanyLike, Long> {

    Optional<CompanyLike> findByCompanyAndJobAndCandidate(Company company, Job job, CandidateProfile candidate);

    boolean existsByCompanyAndJobAndCandidate(Company company, Job job, CandidateProfile candidate);

    List<CompanyLike> findByJob(Job job);

    List<CompanyLike> findByCompany(Company company);
}
