package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByCnpj(String cnpj);
    boolean existsByCnpj(String cnpj);
}
