package com.trabalhaki.backend.dto.company;

import jakarta.validation.constraints.Size;

public record UpdateCompanyRequest(
        @Size(max = 150)
        String name,

        String description,

        @Size(max = 100)
        String industry,

        @Size(max = 50)
        String companySize,

        @Size(max = 200)
        String website,

        @Size(max = 500)
        String logoUrl,

        @Size(max = 100)
        String city,

        @Size(max = 50)
        String state,

        @Size(max = 50)
        String country,

        Double latitude,
        Double longitude
) {}
