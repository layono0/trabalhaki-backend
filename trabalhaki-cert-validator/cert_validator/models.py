from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass
class ForensicCheck:
    code: str
    severity: str  # INFO | WARN | HIGH | CRITICAL
    passed: bool
    points: int
    detail: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "code": self.code,
            "severity": self.severity,
            "passed": self.passed,
            "points": self.points,
            "detail": self.detail,
        }


@dataclass
class InstitutionMatch:
    matched: bool
    name: str | None = None
    tier: int | None = None
    category: str | None = None
    matched_pattern: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "matched": self.matched,
            "name": self.name,
            "tier": self.tier,
            "category": self.category,
            "matchedPattern": self.matched_pattern,
        }


@dataclass
class HolderMatch:
    matched: bool
    similarity: float
    profile_name: str
    extracted_snippet: str | None = None
    detail: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "matched": self.matched,
            "similarity": round(self.similarity, 3),
            "profileName": self.profile_name,
            "extractedSnippet": self.extracted_snippet,
            "detail": self.detail,
        }


@dataclass
class ValidationResult:
    accepted: bool
    status: str  # VERIFIED | REJECTED
    trust_score: int
    fraud_risk_score: int
    priority_bonus: int
    file_sha256: str
    course_title: str | None
    institution: InstitutionMatch
    holder_match: HolderMatch
    verified_skills: list[str] = field(default_factory=list)
    workload_hours: int | None = None
    issue_date: str | None = None
    credential_code: str | None = None
    forensic_checks: list[ForensicCheck] = field(default_factory=list)
    rejection_reasons: list[str] = field(default_factory=list)
    what_was_checked: list[str] = field(default_factory=list)
    summary: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "accepted": self.accepted,
            "status": self.status,
            "trustScore": self.trust_score,
            "fraudRiskScore": self.fraud_risk_score,
            "priorityBonus": self.priority_bonus,
            "fileSha256": self.file_sha256,
            "courseTitle": self.course_title,
            "institution": self.institution.to_dict(),
            "holderMatch": self.holder_match.to_dict(),
            "verifiedSkills": list(self.verified_skills),
            "workloadHours": self.workload_hours,
            "issueDate": self.issue_date,
            "credentialCode": self.credential_code,
            "forensicChecks": [c.to_dict() for c in self.forensic_checks],
            "rejectionReasons": list(self.rejection_reasons),
            "whatWasChecked": list(self.what_was_checked),
            "summary": self.summary,
            "metadata": dict(self.metadata),
        }
