# Trabalhaki — API REST Back-end

API REST central da plataforma **Trabalhaki**, construída em **Java 21** com **Spring Boot 4.1.1**, responsável por autenticação JWT, validação estrutural de CNPJ, motor determinístico de compatibilidade explicada (0–100), regras de interesse recíproco (match com snapshots congelados), chat pós-match, controle de etapas seletivas e upload seguro de imagens.

[![Java](https://img.shields.io/badge/Java-21-12152E?style=flat-square)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-0F6B3A?style=flat-square)](https://spring.io/projects/spring-boot)
[![MariaDB](https://img.shields.io/badge/MariaDB%20%2F%20H2-JPA%20%2F%20Hibernate-393BE7?style=flat-square)](https://mariadb.org/)
[![Tests](https://img.shields.io/badge/JUnit%205-23%20testes%20aprovados-0F6B3A?style=flat-square)](https://junit.org/junit5/)

---

## Repositórios do Ecossistema Trabalhaki

| Módulo | Tecnologia | Repositório |
|---|---|---|
| **API REST e Regras de Negócio** | Java 21 + Spring Boot 4.1 | [`layono0/trabalhaki-backend`](https://github.com/layono0/trabalhaki-backend) |
| **Portal Web (Empresas)** | React 19 + TypeScript + Vite | [`layono0/trabalhaki-web`](https://github.com/layono0/trabalhaki-web) |
| **Aplicativo Mobile (Candidatos)** | Flutter + Dart | [`layono0/trabalhaki-frontend`](https://github.com/layono0/trabalhaki-frontend) |

---

## Arquitetura e Camadas

O servidor segue arquitetura em camadas bem definidas (`controller` → `service` → `repository` → `domain`) com documentação interativa OpenAPI/Swagger (`/swagger-ui.html`) e persistência via Spring Data JPA / Hibernate em **MariaDB** (produção) ou **H2 em memória** (perfil `h2` / testes).

```text
trabalhaki-backend/
├── pom.xml                                  # Dependências Maven (Spring Web, JPA, Security, JJWT, SpringDoc)
├── schema.sql                               # Script DDL completo das 16 tabelas relacionais + seeds
├── trabalhaki-cert-validator/               # Microserviço Python embarcado de validação antifraude de certificados
└── src/
    ├── main/java/com/trabalhaki/backend/
    │   ├── config/                          # SecurityConfig, DataInitializer, CertValidatorProcessManager
    │   ├── controller/                      # 10 Controllers REST (/api/v1/*)
    │   ├── domain/                          # 16 Entidades JPA e 8 Enums de domínio
    │   ├── dto/                             # DTOs de entrada e resposta validados com Bean Validation
    │   ├── exception/                       # GlobalExceptionHandler e exceções de regra de negócio
    │   ├── repository/                      # 14 repositórios Spring Data JPA
    │   ├── security/                        # JwtTokenProvider, filtro JWT e UserDetailsService
    │   └── service/                         # MatchingAlgorithmService, CertificateVerificationService, LikeService, etc.
    └── test/java/com/trabalhaki/backend/    # Suíte automatizada (JUnit 5 + MockMvc)
```

---

## Motor de Compatibilidade Explicada (0–100 pontos)

O serviço `MatchingAlgorithmService` calcula uma pontuação determinística e retorna o detalhamento textual por critério:

| Critério | Peso Máximo | Regra de Cálculo |
|---|---:|---|
| Competências obrigatórias (`REQUIRED`) | 35 pts | Proporção de habilidades obrigatórias presentes no perfil |
| Competências diferenciais (`PREFERRED`) | 15 pts | Proporção de habilidades desejáveis atendidas |
| Experiência profissional | 20 pts | Integral ao atingir o mínimo exigido; proporcional abaixo |
| Pretensão salarial | 15 pts | Integral quando compatível com o teto da vaga; descontos progressivos acima |
| Modalidade e localização | 15 pts | Remoto recebe pontuação integral; presencial/híbrido avalia preferência + cidade/UF |

---

## Principais Endpoints REST (`/api/v1`)

| Domínio | Métodos e Rotas | Descrição |
|---|---|---|
| **Autenticação** | `POST /auth/register-candidate`, `POST /auth/register-company`, `POST /auth/login`, `POST /auth/refresh`, `GET /auth/me` | Cadastro (com validação de CNPJ), autenticação JWT e renovação de sessão |
| **Candidatos** | `GET /candidates/profile/me`, `PUT /candidates/profile/me`, `POST /candidates/profile/me/photo`, `GET /candidates/{id}/profile` | Perfil estruturado, competências, completude e upload seguro de foto |
| **Empresas** | `GET /companies/me`, `PUT /companies/me`, `POST /companies/me/logo`, `GET/POST /companies/members`, `GET /companies/{id}` | Dados institucionais, logotipo e equipe (`ADMIN` / `RECRUITER`) |
| **Vagas (CRUD)** | `POST /jobs`, `GET /jobs`, `GET /jobs/my`, `GET /jobs/{id}`, `PUT /jobs/{id}`, `PATCH /jobs/{id}/close`, `GET /jobs/{id}/applicants` | Ciclo de vida completo da vaga, limite de 3 vagas ativas no plano gratuito e fila de interessados |
| **Recomendações** | `GET /recommendations/jobs`, `GET /recommendations/candidates` | Ranqueamento ordenado por compatibilidade com filtros (modalidade, estado, salário mínimo, paginação) |
| **Interesses & Passes** | `POST /likes/candidate/job/{jobId}`, `GET /likes/candidate/my`, `POST /likes/company/job/{jobId}/candidate/{candidateId}`, `POST/DELETE .../pass` | Demonstração unilateral, detecção de match recíproco e descarte reversível |
| **Matches & Chat** | `GET /matches`, `GET /matches/{id}`, `GET /matches/{matchId}/chat`, `POST /matches/{matchId}/chat/messages` | Matches com snapshots congelados e chat (primeira mensagem exclusiva da empresa) |
| **Processo Seletivo** | `GET /matches/{matchId}/process`, `PATCH /matches/{matchId}/process/advance`, `POST /matches/{matchId}/process/withdraw` | Avanço de etapas (`MATCHED`, `IN_REVIEW`, `INTERVIEW`, `APPROVED`, `REJECTED`, `WITHDRAWN`) com auditoria |
| **Arquivos** | `GET /files/{filename}` | Servir imagens validadas (máx. 3 MB, checagem de magic bytes e proteção contra path traversal) |

---

## Como Executar Localmente

### Pré-requisitos
- JDK 21 (`JAVA_HOME` configurado)
- Python 3 + Flask (`pip install -r trabalhaki-cert-validator/requirements.txt`) — iniciado automaticamente junto com o back-end na porta `8090` (`cert.validator.auto-start=true`)
- Opcional: MariaDB 10.5+ (ou usar o perfil em memória `h2` sem instalação externa)

### Comandos

```bash
# 1. Executar com banco H2 em memória (pronto para testes e depuração local)
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# 2. Acessar documentação interativa OpenAPI / Swagger UI
# http://localhost:8080/swagger-ui.html

# 3. Acessar console web do banco H2 (quando perfil h2 estiver ativo)
# http://localhost:8080/h2-console

# 4. Executar a suíte completa de testes automatizados (23 testes)
./mvnw test
```

---

## Qualidade e Testes Automatizados

- **23/23 testes aprovados** via `./mvnw test`:
  - `AuthControllerTest` (8 testes): login, senha inválida, cadastro candidato/empresa, rejeição de CNPJ inválido, validação de campos em branco, refresh token e `/auth/me`.
  - `JobAndMatchBusinessRulesTest` (5 testes): limite de 3 vagas no plano gratuito, match recíproco com snapshots, remoção de vaga encerrada das curtidas, cálculo determinístico do score e filtros/paginação.
  - `EmployerPortalIntegrationTest` (4 testes): fila de interessados, descarte/desfazer descarte, chat iniciado pela empresa, etapas seletivas e gestão de membros.
  - `FileUploadTest` (5 testes): upload JPEG válido, bloqueio de magic bytes falsos, arquivo vazio, limite de 3 MB e proteção contra path traversal.
  - `BackendApplicationTests` (1 teste): carga íntegra do contexto Spring Boot.
