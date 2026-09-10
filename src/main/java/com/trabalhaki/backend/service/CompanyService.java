package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.CompanyRole;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.Company;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.dto.company.AddCompanyMemberRequest;
import com.trabalhaki.backend.dto.company.CompanyMemberResponse;
import com.trabalhaki.backend.dto.company.CompanyResponse;
import com.trabalhaki.backend.dto.company.UpdateCompanyRequest;
import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.CompanyRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public CompanyResponse getMyCompany(String email) {
        User user = getCompanyUser(email);
        Company company = getCompanyEntity(user.getCompanyId());
        return mapToResponse(company);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(Long companyId) {
        Company company = getCompanyEntity(companyId);
        return mapToResponse(company);
    }

    @Transactional
    public CompanyResponse updateMyCompany(String email, UpdateCompanyRequest request) {
        User user = getCompanyUser(email);
        requireAdmin(user);

        Company company = getCompanyEntity(user.getCompanyId());

        if (request.name() != null && !request.name().isBlank()) {
            company.setName(request.name().trim());
        }
        if (request.description() != null) {
            company.setDescription(request.description().trim());
        }
        if (request.industry() != null) {
            company.setIndustry(request.industry().trim());
        }
        if (request.companySize() != null) {
            company.setCompanySize(request.companySize().trim());
        }
        if (request.website() != null) {
            company.setWebsite(request.website().trim());
        }
        if (request.logoUrl() != null) {
            company.setLogoUrl(request.logoUrl().trim());
        }
        if (request.city() != null) {
            company.setCity(request.city().trim());
        }
        if (request.state() != null) {
            company.setState(request.state().trim());
        }
        if (request.country() != null) {
            company.setCountry(request.country().trim());
        }
        if (request.latitude() != null) {
            company.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            company.setLongitude(request.longitude());
        }

        Company savedCompany = companyRepository.save(company);
        return mapToResponse(savedCompany);
    }

    @Transactional
    public CompanyMemberResponse addMember(String adminEmail, AddCompanyMemberRequest request) {
        User adminUser = getCompanyUser(adminEmail);
        requireAdmin(adminUser);

        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email já cadastrado no sistema");
        }

        if (request.companyRole() == null) {
            throw new BusinessRuleException("Papel do membro na empresa é obrigatório (ADMIN ou RECRUITER)");
        }

        User newMember = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.COMPANY)
                .companyRole(request.companyRole())
                .companyId(adminUser.getCompanyId())
                .active(true)
                .build();

        User savedMember = userRepository.save(newMember);
        return mapToMemberResponse(savedMember);
    }

    @Transactional(readOnly = true)
    public List<CompanyMemberResponse> listMembers(String userEmail) {
        User user = getCompanyUser(userEmail);
        return userRepository.findByCompanyId(user.getCompanyId())
                .stream()
                .map(this::mapToMemberResponse)
                .toList();
    }

    private User getCompanyUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (user.getRole() != Role.COMPANY) {
            throw new UnauthorizedException("Acesso exclusivo para contas empresariais");
        }
        if (user.getCompanyId() == null) {
            throw new BusinessRuleException("Usuário não possui vínculo com nenhuma empresa");
        }
        return user;
    }

    private Company getCompanyEntity(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada com ID: " + companyId));
    }

    private void requireAdmin(User user) {
        if (user.getCompanyRole() != CompanyRole.ADMIN) {
            throw new UnauthorizedException("Apenas administradores da empresa têm permissão para esta ação");
        }
    }

    private CompanyResponse mapToResponse(Company c) {
        return new CompanyResponse(
                c.getId(),
                c.getName(),
                c.getCnpj(),
                c.getDescription(),
                c.getIndustry(),
                c.getCompanySize(),
                c.getWebsite(),
                c.getLogoUrl(),
                c.getCity(),
                c.getState(),
                c.getCountry(),
                c.getLatitude(),
                c.getLongitude(),
                c.getVerificationStatus(),
                c.getPlanType(),
                c.isActive(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private CompanyMemberResponse mapToMemberResponse(User u) {
        return new CompanyMemberResponse(
                u.getId(),
                u.getEmail(),
                u.getCompanyRole(),
                u.isActive(),
                u.getCreatedAt()
        );
    }
}
