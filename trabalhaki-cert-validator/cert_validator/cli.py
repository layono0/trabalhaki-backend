from __future__ import annotations

import argparse
import json
import sys

from .engine import validate_certificate_file
from .institutions import list_institutions_catalog


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="cert_validator",
        description="Trabalhaki — Verificador de Competências e Antifraude de Certificados",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    val_p = sub.add_parser("validate", help="Validar um arquivo de certificado")
    val_p.add_argument("--file", required=True, help="Caminho do arquivo (PDF, PNG ou JPEG)")
    val_p.add_argument("--candidate-name", required=True, help="Nome completo do candidato")
    val_p.add_argument("--target-skill", default=None, help="Competência alvo opcional")

    sub.add_parser("institutions", help="Listar instituições reconhecidas")

    args = parser.parse_args(argv)

    if args.command == "institutions":
        print(json.dumps(list_institutions_catalog(), ensure_ascii=False, indent=2))
        return 0

    if args.command == "validate":
        result = validate_certificate_file(
            file_path=args.file,
            candidate_name=args.candidate_name,
            target_skill=args.target_skill,
        )
        print(json.dumps(result.to_dict(), ensure_ascii=False))
        return 0

    return 1


if __name__ == "__main__":
    sys.exit(main())
