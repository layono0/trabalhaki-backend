from __future__ import annotations

import hashlib
from pathlib import Path

from .forensics import analyze_forensics
from .institutions import detect_institution, extract_credential_code
from .models import ForensicCheck, ValidationResult
from .name_matcher import verify_holder_name
from .skill_extractor import (
    extract_course_title,
    extract_dates_and_check_validity,
    extract_skills,
    extract_workload_hours,
    has_completion_statement,
)
from .text_extractor import extract_document_text

MAX_CERTIFICATE_BYTES = 5 * 1024 * 1024  # 5 MB


def validate_certificate_bytes(
    data: bytes,
    candidate_name: str,
    filename: str | None = None,
    file_path: str | None = None,
    target_skill: str | None = None,
) -> ValidationResult:
    file_sha256 = hashlib.sha256(data).hexdigest()
    what_checked = [
        "Assinatura binária (magic bytes) e estrutura do arquivo",
        "Metadados de criação/modificação, revisões incrementais (%%EOF) e histórico XMP",
        "Presença da instituição emissor no catálogo de entidades reconhecidas",
        "Correspondência entre o titular do certificado e o nome completo do candidato",
        "Declaração de conclusão, datas, carga horária e competências técnicas",
    ]

    if not data:
        return ValidationResult(
            accepted=False,
            status="REJECTED",
            trust_score=0,
            fraud_risk_score=100,
            priority_bonus=0,
            file_sha256=file_sha256,
            course_title=None,
            institution=detect_institution("")[0],
            holder_match=verify_holder_name(candidate_name, ""),
            rejection_reasons=["O arquivo enviado está vazio."],
            what_was_checked=what_checked,
            summary="Arquivo vazio rejeitado pela validação.",
        )

    if len(data) > MAX_CERTIFICATE_BYTES:
        return ValidationResult(
            accepted=False,
            status="REJECTED",
            trust_score=0,
            fraud_risk_score=40,
            priority_bonus=0,
            file_sha256=file_sha256,
            course_title=None,
            institution=detect_institution("")[0],
            holder_match=verify_holder_name(candidate_name, ""),
            rejection_reasons=["O certificado excede o tamanho máximo permitido de 5 MB."],
            what_was_checked=what_checked,
            summary="Arquivo acima do limite permitido de 5 MB.",
        )

    # 1. Extract text first so institution policy informs office/Canva strictness
    extracted_text, extraction_method = extract_document_text(data, file_path=file_path)
    institution_match, institution_entry = detect_institution(extracted_text)

    disallow_office = (
        institution_entry.disallow_office_or_canva if institution_entry is not None else True
    )

    # 2. Run metadata & binary anti-fraud forensics
    forensic_checks, fraud_risk_score, meta = analyze_forensics(
        data=data,
        file_path=file_path,
        filename=filename,
        disallow_office_or_canva=disallow_office,
    )
    meta["extractionMethod"] = extraction_method

    rejection_reasons: list[str] = []
    for chk in forensic_checks:
        if not chk.passed and chk.severity in ("CRITICAL", "HIGH"):
            rejection_reasons.append(chk.detail)

    alpha_count = sum(1 for c in extracted_text if c.isalpha())
    if alpha_count < 30:
        fraud_risk_score = min(100, fraud_risk_score + 55)
        detail = (
            "Não foi possível extrair texto verificável do certificado. "
            "Envie o PDF original emitido pela plataforma (evite capturas de tela ou imagens achatadas)."
        )
        forensic_checks.append(
            ForensicCheck(
                code="READABLE_TEXT_LAYER",
                severity="HIGH",
                passed=False,
                points=55,
                detail=detail,
            )
        )
        rejection_reasons.append(detail)
    else:
        forensic_checks.append(
            ForensicCheck(
                code="READABLE_TEXT_LAYER",
                severity="INFO",
                passed=True,
                points=0,
                detail=f"Camada textual legível extraída via {extraction_method} ({alpha_count} caracteres).",
            )
        )

    # 3. Institution whitelist validation
    if not institution_match.matched or institution_entry is None:
        rejection_reasons.append(
            "Instituição emissora não reconhecida no catálogo de instituições confiáveis do Trabalhaki."
        )

    # 4. Holder name verification
    holder_match = verify_holder_name(candidate_name, extracted_text)
    if not holder_match.matched:
        rejection_reasons.append(holder_match.detail)

    # 5. Completion statement, dates, workload, credential code & skills
    has_completion, is_enrollment = has_completion_statement(extracted_text)
    if is_enrollment:
        fraud_risk_score = min(100, fraud_risk_score + 40)
        rejection_reasons.append(
            "O documento parece ser um comprovante de matrícula ou inscrição, não um certificado de conclusão."
        )
    elif alpha_count >= 30 and not has_completion:
        rejection_reasons.append(
            "O documento não contém declaração clara de conclusão ou aprovação do curso."
        )

    issue_date, dates_valid, date_error = extract_dates_and_check_validity(extracted_text)
    if not dates_valid and date_error:
        fraud_risk_score = min(100, fraud_risk_score + 45)
        rejection_reasons.append(date_error)

    workload_hours = extract_workload_hours(extracted_text)
    if workload_hours is not None and workload_hours > 4000:
        fraud_risk_score = min(100, fraud_risk_score + 35)
        rejection_reasons.append(
            f"Carga horária declarada inconsistente ({workload_hours}h)."
        )

    credential_code = extract_credential_code(extracted_text)
    course_title = extract_course_title(extracted_text)
    verified_skills = extract_skills(extracted_text)

    if alpha_count >= 30 and not verified_skills:
        rejection_reasons.append(
            "Nenhuma competência técnica do catálogo Trabalhaki foi identificada no certificado."
        )

    if target_skill and target_skill.strip():
        target_clean = target_skill.strip().lower()
        if not any(s.lower() == target_clean for s in verified_skills):
            rejection_reasons.append(
                f"O certificado não comprova a competência selecionada ('{target_skill.strip()}')."
            )

    # Anti-fraud gate
    if fraud_risk_score >= 45 and not any("Metadados" in r or "revisões" in r or "editor" in r for r in rejection_reasons):
        rejection_reasons.append(
            f"Score de risco antifraude elevado ({fraud_risk_score}/100) pelos metadados do arquivo."
        )

    accepted = len(rejection_reasons) == 0 and fraud_risk_score < 45

    # Compute priority bonus & trust score
    priority_bonus = 0
    if accepted and institution_entry is not None:
        base_by_tier = {1: 6, 2: 4, 3: 3}.get(institution_entry.tier, 3)
        bonus = base_by_tier
        if credential_code:
            bonus += 1
        if workload_hours is not None:
            if workload_hours >= 60:
                bonus += 2
            elif workload_hours >= 20:
                bonus += 1
        priority_bonus = max(3, min(9, bonus))

    trust_base = 100 - fraud_risk_score
    if not institution_match.matched:
        trust_base -= 25
    if not holder_match.matched:
        trust_base -= 25
    if not has_completion:
        trust_base -= 15
    if not credential_code:
        trust_base -= 5
    trust_score = max(0, min(100, trust_base))

    if accepted:
        inst_name = institution_match.name or "Instituição reconhecida"
        skills_text = ", ".join(verified_skills)
        summary = (
            f"Certificado validado ({inst_name}, Tier {institution_match.tier}). "
            f"Competências confirmadas: {skills_text}. Bônus de prioridade: +{priority_bonus} pts."
        )
    else:
        summary = rejection_reasons[0] if rejection_reasons else "Certificado rejeitado na verificação."

    return ValidationResult(
        accepted=accepted,
        status="VERIFIED" if accepted else "REJECTED",
        trust_score=trust_score,
        fraud_risk_score=fraud_risk_score,
        priority_bonus=priority_bonus,
        file_sha256=file_sha256,
        course_title=course_title,
        institution=institution_match,
        holder_match=holder_match,
        verified_skills=verified_skills if accepted else [],
        workload_hours=workload_hours,
        issue_date=issue_date,
        credential_code=credential_code,
        forensic_checks=forensic_checks,
        rejection_reasons=rejection_reasons,
        what_was_checked=what_checked,
        summary=summary,
        metadata=meta,
    )


def validate_certificate_file(
    file_path: str | Path,
    candidate_name: str,
    target_skill: str | None = None,
) -> ValidationResult:
    p = Path(file_path)
    data = p.read_bytes()
    return validate_certificate_bytes(
        data=data,
        candidate_name=candidate_name,
        filename=p.name,
        file_path=str(p),
        target_skill=target_skill,
    )
