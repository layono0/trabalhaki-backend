from __future__ import annotations

import re
from dataclasses import dataclass
from unidecode import unidecode

from .models import InstitutionMatch


@dataclass(frozen=True)
class InstitutionEntry:
    name: str
    tier: int  # 1 = Global/MEC/Sistema S, 2 = Tech LMS, 3 = Foundations/Public Schools
    category: str
    patterns: tuple[str, ...]
    domains: tuple[str, ...] = ()
    disallow_office_or_canva: bool = True


INSTITUTIONS: tuple[InstitutionEntry, ...] = (
    # Tier 1 — Global Cloud/Tech Certifiers & Universities / Sistema S
    InstitutionEntry(
        name="Amazon Web Services (AWS)",
        tier=1,
        category="Certificação Cloud & Tecnologia",
        patterns=("amazon web services", "aws training and certification", "aws certified", "aws academy"),
        domains=("aws.amazon.com", "credly.com", "aws.training"),
    ),
    InstitutionEntry(
        name="Microsoft",
        tier=1,
        category="Certificação Cloud & Tecnologia",
        patterns=("microsoft certified", "microsoft learn", "azure fundamentals", "microsoft corporation"),
        domains=("learn.microsoft.com", "credly.com"),
    ),
    InstitutionEntry(
        name="Google Cloud / Google",
        tier=1,
        category="Certificação Cloud & Tecnologia",
        patterns=("google cloud", "google career certificates", "grow with google", "google llc"),
        domains=("cloud.google.com", "coursera.org", "credly.com"),
    ),
    InstitutionEntry(
        name="Oracle University",
        tier=1,
        category="Certificação Cloud & Tecnologia",
        patterns=("oracle university", "oracle certified", "oracle cloud infrastructure"),
        domains=("education.oracle.com", "catalog-education.oracle.com"),
    ),
    InstitutionEntry(
        name="Cisco Networking Academy",
        tier=1,
        category="Certificação Redes & Segurança",
        patterns=("cisco networking academy", "cisco systems", "ccna", "netacad"),
        domains=("netacad.com", "credly.com"),
    ),
    InstitutionEntry(
        name="Linux Foundation / CNCF",
        tier=1,
        category="Certificação Open Source & Cloud",
        patterns=("linux foundation", "cloud native computing foundation", "cncf"),
        domains=("training.linuxfoundation.org", "credly.com"),
    ),
    InstitutionEntry(
        name="Red Hat",
        tier=1,
        category="Certificação Enterprise Linux",
        patterns=("red hat", "rhcsa", "rhce"),
        domains=("redhat.com", "credly.com"),
    ),
    InstitutionEntry(
        name="GitHub",
        tier=1,
        category="Certificação Engenharia de Software",
        patterns=("github certifications", "github foundations", "github actions"),
        domains=("github.com", "credly.com"),
    ),
    InstitutionEntry(
        name="HashiCorp",
        tier=1,
        category="Certificação Infraestrutura",
        patterns=("hashicorp certified", "hashicorp"),
        domains=("hashicorp.com", "credly.com"),
    ),
    InstitutionEntry(
        name="CompTIA",
        tier=1,
        category="Certificação Segurança & TI",
        patterns=("comptia", "security+", "network+"),
        domains=("comptia.org", "credly.com"),
    ),
    InstitutionEntry(
        name="IBM",
        tier=1,
        category="Certificação Tecnologia",
        patterns=("ibm skillsbuild", "ibm certified", "international business machines"),
        domains=("ibm.com", "credly.com"),
    ),
    InstitutionEntry(
        name="Meta",
        tier=1,
        category="Certificação Desenvolvimento",
        patterns=("meta front-end", "meta back-end", "meta platforms"),
        domains=("coursera.org", "credly.com"),
    ),
    InstitutionEntry(
        name="Scrum.org",
        tier=1,
        category="Certificação Agilidade",
        patterns=("scrum.org", "professional scrum master", "psm i"),
        domains=("scrum.org",),
    ),
    InstitutionEntry(
        name="PMI (Project Management Institute)",
        tier=1,
        category="Certificação Gestão",
        patterns=("project management institute", "pmi", "pmp", "capm"),
        domains=("pmi.org", "credly.com"),
    ),
    InstitutionEntry(
        name="Universidade de São Paulo (USP)",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade de sao paulo", "usp", "usp/esalq", "poliusp", "ime-usp"),
        domains=("usp.br", "coursera.org"),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UNICAMP",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade estadual de campinas", "unicamp"),
        domains=("unicamp.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UFMG",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade federal de minas gerais", "ufmg"),
        domains=("ufmg.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UFRJ",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade federal do rio de janeiro", "ufrj"),
        domains=("ufrj.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UFRGS",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade federal do rio grande do sul", "ufrgs"),
        domains=("ufrgs.br", "lumina.ufrgs.br"),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UFSC",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade federal de santa catarina", "ufsc"),
        domains=("ufsc.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UnB",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade de brasilia",),
        domains=("unb.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UNESP",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade estadual paulista", "unesp"),
        domains=("unesp.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UFPE",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade federal de pernambuco", "ufpe", "cin-ufpe"),
        domains=("ufpe.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="UFC",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade federal do ceara",),
        domains=("ufc.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="ITA",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("instituto tecnologico de aeronautica",),
        domains=("ita.br", "coursera.org"),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="Fundação Getulio Vargas (FGV)",
        tier=1,
        category="Ensino Superior & Educação Executiva",
        patterns=("fundacao getulio vargas", "fgv", "fgv online"),
        domains=("fgv.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="PUC (Pontifícia Universidade Católica)",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("pontificia universidade catolica", "puc-sp", "puc-rio", "puc-mg", "pucrs", "pucpr"),
        domains=("puc.br", "pucrs.br", "puc-rio.br", "pucminas.br"),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="Insper",
        tier=1,
        category="Ensino Superior",
        patterns=("insper instituto de ensino e pesquisa", "insper"),
        domains=("insper.edu.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="FIAP",
        tier=1,
        category="Ensino Superior & Tecnologia",
        patterns=("fiap", "faculdade de informatica e administracao paulista"),
        domains=("fiap.com.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="Universidade Presbiteriana Mackenzie",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("universidade presbiteriana mackenzie", "mackenzie"),
        domains=("mackenzie.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="SENAI",
        tier=1,
        category="Sistema S / Formação Técnica",
        patterns=("senai", "servico nacional de aprendizagem industrial"),
        domains=("senai.br", "sp.senai.br", "mundosenai.com.br"),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="SENAC",
        tier=1,
        category="Sistema S / Formação Técnica",
        patterns=("senac", "servico nacional de aprendizagem comercial"),
        domains=("senac.br", "sp.senac.br"),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="Instituto Federal (IF)",
        tier=1,
        category="Rede Federal de Ensino Técnico e Superior",
        patterns=(
            "instituto federal",
            "ifsp",
            "ifrs",
            "ifmg",
            "ifsc",
            "ifba",
            "ifce",
            "ifpb",
            "ifrn",
        ),
        domains=("edu.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="FATEC / ETEC (Centro Paula Souza)",
        tier=1,
        category="Ensino Técnico & Tecnológico",
        patterns=("centro paula souza", "fatec", "etec"),
        domains=("cps.sp.gov.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="HarvardX / MITx / edX",
        tier=1,
        category="Universidade / Ensino Superior",
        patterns=("harvardx", "mitx", "stanford online", "edx inc"),
        domains=("edx.org", "cs50.harvard.edu"),
    ),
    # Tier 2 — Consolidated Tech Education Platforms
    InstitutionEntry(
        name="Alura",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("alura", "aovivo.alura", "alura cursos online", "grupo alura"),
        domains=("cursos.alura.com.br", "alura.com.br"),
    ),
    InstitutionEntry(
        name="Rocketseat",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("rocketseat",),
        domains=("app.rocketseat.com.br", "rocketseat.com.br"),
    ),
    InstitutionEntry(
        name="DIO (Digital Innovation One)",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("digital innovation one", "dio.me"),
        domains=("dio.me", "certificates.digitalinnovation.one"),
    ),
    InstitutionEntry(
        name="Coursera",
        tier=2,
        category="Plataforma Educacional",
        patterns=("coursera",),
        domains=("coursera.org",),
    ),
    InstitutionEntry(
        name="Udemy",
        tier=2,
        category="Plataforma Educacional",
        patterns=("udemy",),
        domains=("udemy.com", "ude.my"),
    ),
    InstitutionEntry(
        name="Udacity",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("udacity", "nanodegree"),
        domains=("udacity.com", "confirm.udacity.com"),
    ),
    InstitutionEntry(
        name="Pluralsight",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("pluralsight",),
        domains=("pluralsight.com",),
    ),
    InstitutionEntry(
        name="DataCamp",
        tier=2,
        category="Plataforma de Dados & IA",
        patterns=("datacamp",),
        domains=("datacamp.com",),
    ),
    InstitutionEntry(
        name="freeCodeCamp",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("freecodecamp",),
        domains=("freecodecamp.org",),
    ),
    InstitutionEntry(
        name="Origamid",
        tier=2,
        category="Plataforma de Tecnologia & Design",
        patterns=("origamid",),
        domains=("origamid.com",),
    ),
    InstitutionEntry(
        name="Ada Tech",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("ada tech", "let's code by ada"),
        domains=("ada.tech",),
    ),
    InstitutionEntry(
        name="Trybe",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("trybe", "betrybe"),
        domains=("betrybe.com",),
    ),
    InstitutionEntry(
        name="EBAC",
        tier=2,
        category="Escola de Tecnologia & Design",
        patterns=("ebac", "escola britanica de artes criativas"),
        domains=("ebaconline.com.br",),
    ),
    InstitutionEntry(
        name="TreinaWeb",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("treinaweb",),
        domains=("treinaweb.com.br",),
    ),
    InstitutionEntry(
        name="DevMedia",
        tier=2,
        category="Plataforma de Tecnologia",
        patterns=("devmedia",),
        domains=("devmedia.com.br",),
    ),
    InstitutionEntry(
        name="HackerRank",
        tier=2,
        category="Avaliação Técnica",
        patterns=("hackerrank",),
        domains=("hackerrank.com",),
    ),
    # Tier 3 — Recognized Foundations & Public Schools
    InstitutionEntry(
        name="Fundação Bradesco (Escola Virtual)",
        tier=3,
        category="Fundação Educacional",
        patterns=("fundacao bradesco", "escola virtual fundacao bradesco"),
        domains=("ev.org.br",),
    ),
    InstitutionEntry(
        name="ENAP / Escola Virtual.Gov",
        tier=3,
        category="Escola de Governo",
        patterns=("escola nacional de administracao publica", "enap", "escola virtual.gov"),
        domains=("escolavirtual.gov.br", "enap.gov.br"),
    ),
    InstitutionEntry(
        name="SEBRAE",
        tier=3,
        category="Sistema S / Empreendedorismo",
        patterns=("sebrae", "servico brasileiro de apoio as micro e pequenas empresas"),
        domains=("sebrae.com.br",),
        disallow_office_or_canva=False,
    ),
    InstitutionEntry(
        name="Curso em Vídeo",
        tier=3,
        category="Plataforma Aberta de Tecnologia",
        patterns=("curso em video",),
        domains=("cursoemvideo.com",),
    ),
    InstitutionEntry(
        name="Santander Open Academy",
        tier=3,
        category="Programa Educacional",
        patterns=("santander open academy", "becas santander", "santander universidades"),
        domains=("santanderopenacademy.com",),
    ),
    InstitutionEntry(
        name="Fundação Estudar",
        tier=3,
        category="Fundação Educacional",
        patterns=("fundacao estudar", "na pratica"),
        domains=("estudar.org.br",),
    ),
)

_CREDENTIAL_URL_RE = re.compile(
    r"https?://[^\s<>()\"']*(?:alura\.com\.br|ude\.my|udemy\.com|coursera\.org|credly\.com|"
    r"dio\.me|digitalinnovation\.one|rocketseat\.com\.br|ev\.org\.br|escolavirtual\.gov\.br|"
    r"edx\.org|freecodecamp\.org|datacamp\.com|hackerrank\.com|learn\.microsoft\.com|"
    r"scrum\.org|senai\.br|senac\.br|fgv\.br|fiap\.com\.br|udacity\.com)[^\s<>()\"']*",
    re.IGNORECASE,
)

_EXPLICIT_CODE_RE = re.compile(
    r"(?:c[oó]digo(?:\s+de)?(?:\s+autenticidade|\s+verifica[cç][aã]o|\s+valida[cç][aã]o)?|"
    r"autenticidade|credencial|protocolo|chave\s+de\s+valida[cç][aã]o|"
    r"certificate\s+(?:id|code|number)|credential\s+(?:id|code)|verify\s+id)"
    r"\s*[:#-]?\s*([A-Z0-9][A-Z0-9._/-]{5,38})",
    re.IGNORECASE,
)

_UDEMY_CODE_RE = re.compile(r"\b(UC-[A-Z0-9-]{8,32})\b", re.IGNORECASE)


def _match_phrase(normalized_text: str, phrase: str) -> bool:
    norm_phrase = unidecode(phrase).lower().strip()
    if not norm_phrase:
        return False
    # Short acronyms need strict word boundaries to avoid matching random substrings
    if len(norm_phrase) <= 5 and " " not in norm_phrase and "." not in norm_phrase:
        return bool(re.search(rf"(?<![a-z0-9]){re.escape(norm_phrase)}(?![a-z0-9])", normalized_text))
    return bool(re.search(rf"(?<![a-z0-9]){re.escape(norm_phrase)}(?![a-z0-9])", normalized_text))


def detect_institution(text: str) -> tuple[InstitutionMatch, InstitutionEntry | None]:
    normalized = unidecode(text or "").lower()
    if not normalized.strip():
        return InstitutionMatch(matched=False), None

    best_entry: InstitutionEntry | None = None
    best_pattern: str | None = None

    for entry in INSTITUTIONS:
        for domain in entry.domains:
            if domain.lower() in normalized:
                return (
                    InstitutionMatch(
                        matched=True,
                        name=entry.name,
                        tier=entry.tier,
                        category=entry.category,
                        matched_pattern=domain,
                    ),
                    entry,
                )
        for pattern in entry.patterns:
            if _match_phrase(normalized, pattern):
                if best_entry is None or entry.tier < best_entry.tier:
                    best_entry = entry
                    best_pattern = pattern

    if best_entry is not None:
        return (
            InstitutionMatch(
                matched=True,
                name=best_entry.name,
                tier=best_entry.tier,
                category=best_entry.category,
                matched_pattern=best_pattern,
            ),
            best_entry,
        )

    return InstitutionMatch(matched=False), None


def extract_credential_code(text: str) -> str | None:
    if not text:
        return None
    url_match = _CREDENTIAL_URL_RE.search(text)
    if url_match:
        return url_match.group(0).rstrip(".,;)")

    udemy_match = _UDEMY_CODE_RE.search(text)
    if udemy_match:
        return udemy_match.group(1).upper()

    code_match = _EXPLICIT_CODE_RE.search(text)
    if code_match:
        candidate = code_match.group(1).strip().rstrip(".,;)")
        if len(candidate) >= 6:
            return candidate

    return None


def list_institutions_catalog() -> list[dict]:
    return [
        {
            "name": entry.name,
            "tier": entry.tier,
            "category": entry.category,
            "domains": list(entry.domains),
        }
        for entry in INSTITUTIONS
    ]
