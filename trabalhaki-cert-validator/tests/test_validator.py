from __future__ import annotations

import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image, PngImagePlugin

from cert_validator.app import app
from cert_validator.engine import validate_certificate_bytes
from cert_validator.sample_generator import (
    build_sample_pdf,
    forged_photoshop,
    genuine_alura_java,
    genuine_aws_cloud,
)


class CertificateValidatorTest(unittest.TestCase):
    def test_accepts_genuine_alura_pdf_and_grants_priority(self) -> None:
        pdf_bytes = genuine_alura_java("João Silva")
        res = validate_certificate_bytes(pdf_bytes, candidate_name="João Silva", filename="cert.pdf")

        self.assertTrue(res.accepted, res.rejection_reasons)
        self.assertEqual(res.status, "VERIFIED")
        self.assertEqual(res.institution.name, "Alura")
        self.assertEqual(res.institution.tier, 2)
        self.assertIn("Java", res.verified_skills)
        self.assertIn("Spring Boot", res.verified_skills)
        self.assertIn("REST API", res.verified_skills)
        self.assertEqual(res.workload_hours, 40)
        self.assertGreaterEqual(res.priority_bonus, 6)
        self.assertLess(res.fraud_risk_score, 20)

    def test_accepts_tier1_aws_certificate_with_higher_priority(self) -> None:
        pdf_bytes = genuine_aws_cloud("João Carlos da Silva")
        res = validate_certificate_bytes(pdf_bytes, candidate_name="João Silva", filename="aws.pdf")

        self.assertTrue(res.accepted, res.rejection_reasons)
        self.assertEqual(res.institution.name, "Amazon Web Services (AWS)")
        self.assertEqual(res.institution.tier, 1)
        self.assertIn("AWS", res.verified_skills)
        self.assertIn("Docker", res.verified_skills)
        self.assertGreaterEqual(res.priority_bonus, 8)

    def test_rejects_photoshop_and_incremental_tampering_metadata(self) -> None:
        pdf_bytes = forged_photoshop("João Silva")
        res = validate_certificate_bytes(pdf_bytes, candidate_name="João Silva", filename="forged.pdf")

        self.assertFalse(res.accepted)
        self.assertEqual(res.status, "REJECTED")
        self.assertGreaterEqual(res.fraud_risk_score, 45)
        self.assertEqual(res.priority_bonus, 0)
        codes = {c.code: c for c in res.forensic_checks}
        self.assertFalse(codes["METADATA_SOFTWARE_SIGNATURE"].passed)
        self.assertFalse(codes["INCREMENTAL_SAVE_TAMPERING"].passed)
        self.assertFalse(codes["XMP_EDIT_HISTORY"].passed)
        self.assertFalse(codes["TIMESTAMP_CONSISTENCY"].passed)

    def test_rejects_canva_forged_lms_certificate(self) -> None:
        pdf_bytes = build_sample_pdf(
            lines=[
                "Rocketseat Educação",
                "Certificamos que Ana Clara Costa concluiu a formação",
                "Curso: React e TypeScript na Prática",
                "Carga horária: 30 horas",
            ],
            creator="Canva",
            producer="Canva",
        )
        res = validate_certificate_bytes(pdf_bytes, candidate_name="Ana Clara Costa")
        self.assertFalse(res.accepted)
        self.assertGreaterEqual(res.fraud_risk_score, 50)

    def test_rejects_active_javascript_payload_in_pdf(self) -> None:
        pdf_bytes = genuine_alura_java("João Silva") + b"\n% /JavaScript /OpenAction\n"
        res = validate_certificate_bytes(pdf_bytes, candidate_name="João Silva")
        self.assertFalse(res.accepted)
        self.assertEqual(res.fraud_risk_score, 100)

    def test_rejects_invalid_magic_bytes(self) -> None:
        res = validate_certificate_bytes(b"NOT_A_REAL_PDF_FILE_CONTENT", candidate_name="João Silva")
        self.assertFalse(res.accepted)
        self.assertEqual(res.fraud_risk_score, 100)

    def test_rejects_holder_name_mismatch(self) -> None:
        pdf_bytes = genuine_alura_java("Maria Oliveira")
        res = validate_certificate_bytes(pdf_bytes, candidate_name="João Silva")
        self.assertFalse(res.accepted)
        self.assertFalse(res.holder_match.matched)

    def test_rejects_unknown_institution(self) -> None:
        pdf_bytes = build_sample_pdf(
            lines=[
                "Academia Genérica Sem Registro",
                "Certificamos que João Silva concluiu o curso de Python",
                "Carga horária: 20 horas",
                "Emitido em 10/10/2026",
            ],
            creator="Automated Cert Gen",
            producer="ReportLab PDF Library",
        )
        res = validate_certificate_bytes(pdf_bytes, candidate_name="João Silva")
        self.assertFalse(res.accepted)
        self.assertFalse(res.institution.matched)

    def test_rejects_enrollment_declaration_instead_of_completion(self) -> None:
        pdf_bytes = build_sample_pdf(
            lines=[
                "Universidade de São Paulo (USP)",
                "Comprovante de Matrícula",
                "Aluno: João Silva - Disciplina: Engenharia de Software Java",
                "Carga horária: 60 horas",
            ],
            creator="Sistema Júpiter",
            producer="iText 7",
        )
        res = validate_certificate_bytes(pdf_bytes, candidate_name="João Silva")
        self.assertFalse(res.accepted)
        self.assertTrue(any("matrícula" in r.lower() for r in res.rejection_reasons))

    def test_rejects_future_issue_date_and_target_skill_mismatch(self) -> None:
        pdf_bytes = build_sample_pdf(
            lines=[
                "Alura Cursos Online",
                "Certificamos que João Silva concluiu o curso de Python",
                "Carga horária: 20 horas",
                "Emitido em 15/09/2099",
            ],
            creator="Alura",
            producer="WeasyPrint 62.3",
        )
        res = validate_certificate_bytes(
            pdf_bytes, candidate_name="João Silva", target_skill="Flutter"
        )
        self.assertFalse(res.accepted)
        self.assertTrue(any("futura" in r.lower() for r in res.rejection_reasons))
        self.assertTrue(any("flutter" in r.lower() for r in res.rejection_reasons))

    def test_rejects_flattened_png_screenshot_without_text_or_with_gimp_metadata(self) -> None:
        img = Image.new("RGB", (900, 620), color=(248, 249, 252))
        meta = PngImagePlugin.PngInfo()
        meta.add_text("Software", "GIMP 2.10")
        buf = io.BytesIO()
        img.save(buf, format="PNG", pnginfo=meta)
        res = validate_certificate_bytes(buf.getvalue(), candidate_name="João Silva", filename="print.png")
        self.assertFalse(res.accepted)
        self.assertGreaterEqual(res.fraud_risk_score, 65)

    def test_cli_and_flask_api_end_to_end(self) -> None:
        pdf_bytes = genuine_alura_java("João Silva")
        with tempfile.NamedTemporaryFile(suffix=".pdf") as tmp:
            tmp.write(pdf_bytes)
            tmp.flush()

            proc = subprocess.run(
                [
                    sys.executable,
                    "-m",
                    "cert_validator.cli",
                    "validate",
                    "--file",
                    tmp.name,
                    "--candidate-name",
                    "João Silva",
                ],
                cwd=str(Path(__file__).resolve().parents[1]),
                capture_output=True,
                text=True,
                check=True,
            )
            cli_payload = json.loads(proc.stdout)
            self.assertTrue(cli_payload["accepted"])
            self.assertEqual(cli_payload["institution"]["name"], "Alura")

        client = app.test_client()
        health_resp = client.get("/health")
        self.assertEqual(health_resp.status_code, 200)

        api_resp = client.post(
            "/api/v1/validate",
            data={
                "candidateName": "João Silva",
                "file": (io.BytesIO(pdf_bytes), "alura-java.pdf"),
            },
            content_type="multipart/form-data",
        )
        self.assertEqual(api_resp.status_code, 200)
        api_json = api_resp.get_json()
        self.assertTrue(api_json["accepted"])
        self.assertIn("Java", api_json["verifiedSkills"])


if __name__ == "__main__":
    unittest.main()
