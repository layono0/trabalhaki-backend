-- ==============================================================================
-- TRABALHAKI - Script DDL e Estrutura do Banco de Dados
-- Compatibilidade: MariaDB 10.5+ / MySQL 8.0+
-- Codificação: UTF-8 (utf8mb4 / utf8mb4_unicode_ci)
-- Baseado nas diretrizes de back-end-plan.md
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS trabalhaki 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE trabalhaki;

-- Desativar verificação de FK temporariamente durante a criação das tabelas
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. TABELA: companies (Empresas)
-- Seções 12, 13 e 14 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS companies;
CREATE TABLE companies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    cnpj VARCHAR(20) NOT NULL UNIQUE,
    description TEXT NULL,
    industry VARCHAR(100) NULL,
    company_size VARCHAR(50) NULL,
    website VARCHAR(200) NULL,
    logo_url VARCHAR(500) NULL,
    city VARCHAR(100) NULL,
    state VARCHAR(50) NULL,
    country VARCHAR(50) DEFAULT 'Brasil',
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    plan_type VARCHAR(20) NOT NULL DEFAULT 'FREE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_company_verification_status CHECK (verification_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT chk_company_plan_type CHECK (plan_type IN ('FREE', 'PREMIUM')),
    INDEX idx_companies_cnpj (cnpj),
    INDEX idx_companies_verification (verification_status),
    INDEX idx_companies_plan (plan_type),
    INDEX idx_companies_location (city, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 2. TABELA: users (Usuários e Autenticação)
-- Seções 5 e 6 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    company_role VARCHAR(20) NULL,
    company_id BIGINT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_user_role CHECK (role IN ('CANDIDATE', 'COMPANY')),
    CONSTRAINT chk_user_company_role CHECK (company_role IS NULL OR company_role IN ('ADMIN', 'RECRUITER')),
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE SET NULL,
    INDEX idx_users_email (email),
    INDEX idx_users_company (company_id),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 3. TABELA: refresh_tokens (Renovação de Sessão JWT)
-- Seção 6 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS refresh_tokens;
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date DATETIME NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_token_lookup (token),
    INDEX idx_refresh_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 4. TABELA: candidate_profiles (Perfil Estruturado do Candidato)
-- Seções 7, 10, 11 e 20 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS candidate_profiles;
CREATE TABLE candidate_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    photo_url VARCHAR(500) NULL,
    desired_role VARCHAR(100) NULL,
    area VARCHAR(100) NULL,
    experience_years INT NULL,
    education VARCHAR(150) NULL,
    city VARCHAR(100) NULL,
    state VARCHAR(50) NULL,
    country VARCHAR(50) DEFAULT 'Brasil',
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    minimum_salary_expectation DECIMAL(10, 2) NULL,
    preferred_modality VARCHAR(20) NULL,
    preferred_contract_type VARCHAR(50) NULL,
    completion_percentage INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_candidate_modality CHECK (preferred_modality IS NULL OR preferred_modality IN ('REMOTE', 'HYBRID', 'ONSITE')),
    CONSTRAINT fk_candidate_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_candidate_desired_role (desired_role),
    INDEX idx_candidate_area (area),
    INDEX idx_candidate_location (city, state),
    INDEX idx_candidate_salary (minimum_salary_expectation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 5. TABELA: skills (Catálogo Centralizado de Competências Estruturadas)
-- Seção 9 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS skills;
CREATE TABLE skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,

    INDEX idx_skills_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 6. TABELA: candidate_skills (Relacionamento Candidato <-> Competência)
-- Seção 9 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS candidate_skills;
CREATE TABLE candidate_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,

    CONSTRAINT uk_candidate_skill UNIQUE (candidate_id, skill_id),
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    INDEX idx_cand_skills_candidate (candidate_id),
    INDEX idx_cand_skills_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 7. TABELA: jobs (Oportunidades de Emprego)
-- Seções 11, 14, 15, 16, 22 e 26 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS jobs;
CREATE TABLE jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    minimum_salary DECIMAL(10, 2) NOT NULL,
    maximum_salary DECIMAL(10, 2) NOT NULL,
    modality VARCHAR(20) NOT NULL,
    city VARCHAR(100) NULL,
    state VARCHAR(50) NULL,
    country VARCHAR(50) DEFAULT 'Brasil',
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    contract_type VARCHAR(50) NOT NULL,
    seniority_level VARCHAR(50) NOT NULL,
    experience_years INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_job_modality CHECK (modality IN ('REMOTE', 'HYBRID', 'ONSITE')),
    CONSTRAINT chk_job_status CHECK (status IN ('ACTIVE', 'PAUSED', 'CLOSED')),
    CONSTRAINT chk_job_salary CHECK (minimum_salary <= maximum_salary),
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    INDEX idx_jobs_company_status (company_id, status),
    INDEX idx_jobs_status (status),
    INDEX idx_jobs_modality (modality),
    INDEX idx_jobs_salary (minimum_salary, maximum_salary),
    INDEX idx_jobs_location (city, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 8. TABELA: job_benefits (Benefícios da Vaga)
-- Seção 15 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS job_benefits;
CREATE TABLE job_benefits (
    job_id BIGINT NOT NULL,
    benefit VARCHAR(150) NOT NULL,

    CONSTRAINT fk_job_benefits_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    INDEX idx_job_benefits_job_id (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 9. TABELA: job_skills (Competências da Vaga: REQUIRED vs PREFERRED)
-- Seção 9 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS job_skills;
CREATE TABLE job_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',

    CONSTRAINT chk_job_skill_type CHECK (type IN ('REQUIRED', 'PREFERRED')),
    CONSTRAINT uk_job_skill UNIQUE (job_id, skill_id),
    CONSTRAINT fk_job_skills_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    INDEX idx_job_skills_job (job_id),
    INDEX idx_job_skills_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 10. TABELA: candidate_likes (Demonstração Unilateral: Candidato -> Vaga)
-- Seção 17 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS candidate_likes;
CREATE TABLE candidate_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_candidate_job_like UNIQUE (candidate_id, job_id),
    CONSTRAINT fk_cand_likes_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_cand_likes_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    INDEX idx_cand_likes_candidate (candidate_id),
    INDEX idx_cand_likes_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 11. TABELA: company_likes (Demonstração Unilateral: Empresa + Vaga -> Candidato)
-- Seção 18 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS company_likes;
CREATE TABLE company_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_company_job_candidate_like UNIQUE (company_id, job_id, candidate_id),
    CONSTRAINT fk_comp_likes_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_comp_likes_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_comp_likes_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    INDEX idx_comp_likes_company (company_id),
    INDEX idx_comp_likes_job_candidate (job_id, candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 12. TABELA: job_matches (Match Recíproco com Snapshots Imutáveis Congelados)
-- Seções 19, 21 e 24 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS job_matches;
CREATE TABLE job_matches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    job_snapshot_json LONGTEXT NOT NULL,
    candidate_snapshot_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_job_candidate_match UNIQUE (job_id, candidate_id),
    CONSTRAINT fk_matches_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matches_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matches_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    INDEX idx_matches_candidate (candidate_id),
    INDEX idx_matches_company (company_id),
    INDEX idx_matches_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 13. TABELA: conversations (Canal de Conversa Pós-Match)
-- Seção 37 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS conversations;
CREATE TABLE conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_conversations_match FOREIGN KEY (match_id) REFERENCES job_matches (id) ON DELETE CASCADE,
    INDEX idx_conversations_match (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 14. TABELA: chat_messages (Mensagens de Texto do Chat Pós-Match)
-- Seção 37 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS chat_messages;
CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    text TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_chat_sender_role CHECK (sender_role IN ('CANDIDATE', 'COMPANY')),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_chat_messages_conv_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 15. TABELA: selection_processes (Processo Seletivo Pós-Match)
-- Seção 34 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS selection_processes;
CREATE TABLE selection_processes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'MATCHED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_selection_process_status CHECK (status IN ('MATCHED', 'IN_REVIEW', 'INTERVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT fk_selection_processes_match FOREIGN KEY (match_id) REFERENCES job_matches (id) ON DELETE CASCADE,
    INDEX idx_selection_processes_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 16. TABELA: selection_process_histories (Auditoria Imutável de Transições de Etapa)
-- Seções 35 e 36 do plano
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS selection_process_histories;
CREATE TABLE selection_process_histories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    selection_process_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    note VARCHAR(500) NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_proc_hist_process FOREIGN KEY (selection_process_id) REFERENCES selection_processes (id) ON DELETE CASCADE,
    CONSTRAINT fk_proc_hist_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_proc_hist_process_date (selection_process_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Reativar verificação de FK
SET FOREIGN_KEY_CHECKS = 1;

-- ==============================================================================
-- CARGA INICIAL DE DADOS (SEEDS)
-- ==============================================================================

-- Competências básicas essenciais
INSERT INTO skills (id, name) VALUES
    (1, 'Java'),
    (2, 'Spring Boot'),
    (3, 'Flutter'),
    (4, 'Dart'),
    (5, 'Python'),
    (6, 'SQL'),
    (7, 'Docker'),
    (8, 'Git'),
    (9, 'AWS'),
    (10, 'REST API'),
    (11, 'PostgreSQL'),
    (12, 'React'),
    (13, 'Node.js'),
    (14, 'TypeScript'),
    (15, 'Microservices')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Empresa de Demonstração (CNPJ válido: 33.000.167/0001-01)
INSERT INTO companies (id, name, cnpj, description, industry, company_size, website, city, state, country, verification_status, plan_type, active)
VALUES (
    1,
    'Tech Corp Brasil',
    '33000167000101',
    'Empresa de tecnologia focada em inovação e produtos digitais.',
    'Tecnologia da Informação',
    '50-200',
    'https://techcorp.com.br',
    'São Paulo',
    'SP',
    'Brasil',
    'VERIFIED',
    'FREE',
    TRUE
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Usuários padrão com senhas criptografadas via BCrypt para "123456" ($2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy)
INSERT INTO users (id, email, password, role, company_role, company_id, active)
VALUES 
    (1, 'empresa@trabalhaki.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'COMPANY', 'ADMIN', 1, TRUE),
    (2, 'candidato@trabalhaki.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'CANDIDATE', NULL, NULL, TRUE)
ON DUPLICATE KEY UPDATE email = VALUES(email);

-- Perfil estruturado do candidato João Silva
INSERT INTO candidate_profiles (id, user_id, name, desired_role, area, experience_years, education, city, state, country, minimum_salary_expectation, preferred_modality, completion_percentage)
VALUES (
    1,
    2,
    'João Silva',
    'Desenvolvedor Backend',
    'Tecnologia',
    3,
    'Ciência da Computação',
    'São Paulo',
    'SP',
    'Brasil',
    4000.00,
    'REMOTE',
    85
) ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Vínculo de skills com o candidato João Silva
INSERT INTO candidate_skills (candidate_id, skill_id) VALUES
    (1, 1), -- Java
    (2, 2), -- Spring Boot
    (3, 6), -- SQL
    (4, 7), -- Docker
    (5, 8)  -- Git
ON DUPLICATE KEY UPDATE candidate_id = VALUES(candidate_id);

-- Vaga de demonstração da Tech Corp Brasil
INSERT INTO jobs (id, company_id, title, description, minimum_salary, maximum_salary, modality, city, state, country, contract_type, seniority_level, experience_years, status, version)
VALUES (
    1,
    1,
    'Desenvolvedor Java Backend Pleno',
    'Venha fazer parte do nosso time de engenharia backend desenvolvendo microserviços escaláveis com Spring Boot e Docker.',
    4500.00,
    6500.00,
    'REMOTE',
    'São Paulo',
    'SP',
    'Brasil',
    'CLT',
    'Pleno',
    2,
    'ACTIVE',
    1
) ON DUPLICATE KEY UPDATE title = VALUES(title);

-- Benefícios da vaga
INSERT INTO job_benefits (job_id, benefit) VALUES
    (1, 'Vale Refeição R$ 1.000/mês'),
    (1, 'Plano de Saúde e Odontológico Bradesco'),
    (1, 'Auxílio Home Office R$ 300/mês'),
    (1, 'Gympass')
ON DUPLICATE KEY UPDATE job_id = VALUES(job_id);

-- Skills da vaga (Obrigatórias e Diferenciais)
INSERT INTO job_skills (job_id, skill_id, type) VALUES
    (1, 1, 'REQUIRED'),  -- Java (Obrigatória)
    (1, 2, 'REQUIRED'),  -- Spring Boot (Obrigatória)
    (1, 6, 'REQUIRED'),  -- SQL (Obrigatória)
    (1, 7, 'PREFERRED'), -- Docker (Diferencial)
    (1, 9, 'PREFERRED')  -- AWS (Diferencial)
ON DUPLICATE KEY UPDATE type = VALUES(type);
