package com.trabalhaki.backend.dto.company;

import com.trabalhaki.backend.domain.enums.CompanyRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddCompanyMemberRequest(
        @NotBlank(message = "O email é obrigatório")
        @Email(message = "Email em formato inválido")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
        String password,

        @NotNull(message = "O papel do membro na empresa é obrigatório")
        CompanyRole companyRole
) {}
