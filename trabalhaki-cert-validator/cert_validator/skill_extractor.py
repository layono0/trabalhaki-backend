from __future__ import annotations

import re
from datetime import date, datetime
from unidecode import unidecode

# Canonical Trabalhaki skill names aligned with DataInitializer & JobFormDrawer
SKILL_PATTERNS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("Java", ("java", "jdk", "java se", "java ee", "jakarta ee")),
    ("Spring Boot", ("spring boot", "spring framework", "spring data", "spring security", "spring mvc")),
    ("Flutter", ("flutter",)),
    ("Dart", ("dart",)),
    ("Python", ("python", "fastapi", "django", "flask")),
    ("SQL", ("sql", "mysql", "mariadb", "oracle sql", "tsql", "pl/sql")),
    ("PostgreSQL", ("postgresql", "postgres")),
    ("Docker", ("docker", "containers")),
    ("Git", ("git", "github", "gitlab")),
    ("AWS", ("aws", "amazon web services", "cloud practitioner", "solutions architect", "ec2", "s3", "lambda")),
    ("REST API", ("rest api", "api rest", "apis rest", "restful")),
    ("React", ("react", "reactjs", "react.js", "next.js", "nextjs")),
    ("Node.js", ("node.js", "nodejs", "express.js", "nestjs")),
    ("TypeScript", ("typescript",)),
    ("Microservices", ("microservices", "microsservicos", "microservicos")),
    ("Kubernetes", ("kubernetes", "k8s")),
    ("Linux", ("linux", "ubuntu", "red hat enterprise linux")),
    ("Scrum", ("scrum", "scrum master", "agil", "agile")),
    ("Segurança da Informação", ("seguranca da informacao", "cybersecurity", "ciberseguranca", "owasp", "pentest")),
    ("Ciência de Dados", ("ciencia de dados", "data science", "machine learning", "pandas", "power bi")),
)

COMPLETION_PHRASES = (
    "certificamos",
    "certifica",
    "concluiu",
    "conclusao",
    "aprovado",
    "aprovacao",
    "certificate of completion",
    "successfully completed",
    "has completed",
    "is hereby certified",
    "earned this credential",
    "certified",
    "diploma",
)

ENROLLMENT_ONLY_PHRASES = (
    "comprovante de matricula",
    "declaracao de matricula",
    "comprovante de inscricao",
    "recibo de inscricao",
    "boleto",
)

_MONTHS_PT_EN = {
    "janeiro": 1, "january": 1, "jan": 1,
    "fevereiro": 2, "february": 2, "fev": 2, "feb": 2,
    "marco": 3, "march": 3, "mar": 3,
    "abril": 4, "april": 4, "abr": 4, "apr": 4,
    "maio": 5, "may": 5, "mai": 5,
    "junho": 6, "june": 6, "jun": 6,
    "julho": 7, "july": 7, "jul": 7,
    "agosto": 8, "august": 8, "ago": 8, "aug": 8,
    "setembro": 9, "september": 9, "set": 9, "sep": 9,
    "outubro": 10, "october": 10, "out": 10, "oct": 10,
    "novembro": 11, "november": 11, "nov": 11,
    "dezembro": 12, "december": 12, "dez": 12, "dec": 12,
}


def has_completion_statement(text: str) -> tuple[bool, bool]:
    """Return (has_completion_phrase, is_enrollment_only)."""
    norm = unidecode(text or "").lower()
    is_enrollment = any(p in norm for p in ENROLLMENT_ONLY_PHRASES)
    has_completion = any(p in norm for p in COMPLETION_PHRASES)
    return has_completion, is_enrollment


def extract_skills(text: str) -> list[str]:
    norm = unidecode(text or "").lower()
    matched: list[str] = []
    for canonical, aliases in SKILL_PATTERNS:
        for alias in aliases:
            if re.search(rf"(?<![a-z0-9]){re.escape(alias)}(?![a-z0-9])", norm):
                matched.append(canonical)
                break
    return matched


def extract_workload_hours(text: str) -> int | None:
    norm = unidecode(text or "").lower()
    patterns = (
        r"(?:carga\s+horaria(?:\s+total)?|duracao|duration|workload)\s*[:\-]?\s*(\d{1,4})\s*(?:h|horas|hours|hrs)\b",
        r"\b(\d{1,4})\s*(?:horas|hours)\b",
        r"\b(\d{1,4})h\b",
    )
    for rx in patterns:
        m = re.search(rx, norm)
        if m:
            hours = int(m.group(1))
            if hours > 0:
                return hours
    return None


def extract_course_title(text: str) -> str | None:
    for line in (text or "").splitlines():
        clean = " ".join(line.strip().split())
        if not clean:
            continue
        norm = unidecode(clean).lower()
        m = re.search(r"(?:curso|formacao|certificacao|treinamento|course|program)\s*[:\-]\s*(.+)", norm)
        if m and len(m.group(1).strip()) >= 4:
            idx = norm.find(":")
            if idx != -1:
                return clean[idx + 1 :].strip()[:160]
    # Fallback: pick longest line mentioning a skill or completion phrase
    for line in (text or "").splitlines():
        clean = " ".join(line.strip().split())
        if 12 <= len(clean) <= 130 and extract_skills(clean):
            return clean
    return None


def extract_dates_and_check_validity(text: str, today: date | None = None) -> tuple[str | None, bool, str | None]:
    """Return (issue_date_iso, dates_valid, error_detail)."""
    ref_today = today or datetime.now().date()
    norm = unidecode(text or "").lower()

    # Check explicit expiration phrases first
    exp_match = re.search(
        r"(?:valido\s+ate|validade|valid\s+until|expires?\s+on)\s*[:\-]?\s*(\d{2}/\d{2}/\d{4}|\d{4}-\d{2}-\d{2})",
        norm,
    )
    if exp_match:
        raw_exp = exp_match.group(1)
        exp_dt = _parse_date_str(raw_exp)
        if exp_dt and exp_dt < ref_today:
            return None, False, f"Certificado expirado em {exp_dt.isoformat()}."

    found_dates: list[date] = []
    for raw in re.findall(r"\b(\d{2}/\d{2}/\d{4})\b", norm):
        d = _parse_date_str(raw)
        if d:
            found_dates.append(d)
    for raw in re.findall(r"\b(\d{4}-\d{2}-\d{2})\b", norm):
        d = _parse_date_str(raw)
        if d:
            found_dates.append(d)
    for day_s, month_s, year_s in re.findall(
        r"\b(\d{1,2})\s+de\s+([a-z]+)\s+de\s+(\d{4})\b", norm
    ):
        month = _MONTHS_PT_EN.get(month_s)
        if month:
            try:
                found_dates.append(date(int(year_s), month, int(day_s)))
            except ValueError:
                pass

    if not found_dates:
        return None, True, None

    latest = max(found_dates)
    if latest > ref_today:
        return latest.isoformat(), False, f"Data de emissão futura detectada ({latest.isoformat()})."
    return latest.isoformat(), True, None


def _parse_date_str(raw: str) -> date | None:
    for fmt in ("%d/%m/%Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(raw, fmt).date()
        except ValueError:
            continue
    return None
