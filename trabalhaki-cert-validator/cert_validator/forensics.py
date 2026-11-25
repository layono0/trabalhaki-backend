from __future__ import annotations

import io
import re
import subprocess
from datetime import datetime, timedelta, timezone
from typing import Any

from PIL import Image

from .models import ForensicCheck

TAMPER_SOFTWARE_KEYWORDS = (
    "photoshop",
    "adobe photoshop",
    "gimp",
    "inkscape",
    "paint.net",
    "coreldraw",
    "illustrator",
    "sejda",
    "ilovepdf",
    "smallpdf",
    "pdfescape",
    "pdf24",
    "pdfgear",
    "foxit",
    "nitro pdf",
    "master pdf",
    "pdf-xchange",
    "scribus",
    "snipping tool",
    "lightshot",
    "greenshot",
)

MANUAL_OFFICE_KEYWORDS = (
    "microsoft word",
    "microsoft® word",
    "powerpoint",
    "libreoffice",
    "openoffice",
    "wps office",
    "google docs",
    "canva",
)

ACTIVE_PDF_TOKENS = (b"/JavaScript", b"/JS", b"/Launch", b"/OpenAction", b"/EmbeddedFiles")


def detect_file_type(data: bytes, filename: str | None = None) -> str | None:
    if data.startswith(b"%PDF-1.") or data.startswith(b"%PDF-2."):
        return "pdf"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if data.startswith(b"\xff\xd8\xff"):
        return "jpg"
    return None


def _decode_pdf_literal(raw: bytes) -> str:
    # Unescape basic PDF octal sequences and backslash escapes
    out = bytearray()
    i = 0
    n = len(raw)
    while i < n:
        ch = raw[i]
        if ch == 0x5C and i + 1 < n:  # '\'
            nxt = raw[i + 1]
            if 0x30 <= nxt <= 0x37:  # octal \ddd
                oct_digits = bytes([nxt])
                j = i + 2
                while j < min(i + 4, n) and 0x30 <= raw[j] <= 0x37:
                    oct_digits += bytes([raw[j]])
                    j += 1
                out.append(int(oct_digits, 8) & 0xFF)
                i = j
                continue
            escape_map = {
                ord("n"): 10,
                ord("r"): 13,
                ord("t"): 9,
                ord("b"): 8,
                ord("f"): 12,
                ord("("): ord("("),
                ord(")"): ord(")"),
                ord("\\"): ord("\\"),
            }
            if nxt in escape_map:
                out.append(escape_map[nxt])
                i += 2
                continue
        out.append(ch)
        i += 1
    try:
        return out.decode("utf-8")
    except UnicodeDecodeError:
        return out.decode("latin-1", errors="replace")


def _extract_pdf_info_raw(data: bytes) -> dict[str, str]:
    info: dict[str, str] = {}
    for key in ("Creator", "Producer", "Author", "Title", "CreationDate", "ModDate"):
        pattern = re.compile(
            rf"/{key}\s*\(([^()]*(?:\\.[^()]*)*)\)",
            re.DOTALL,
        )
        matches = pattern.findall(data.decode("latin-1", errors="ignore"))
        if matches:
            info[key.lower()] = _decode_pdf_literal(matches[-1].encode("latin-1", errors="ignore")).strip()

    # Also inspect XMP packet if embedded
    text_latin = data.decode("latin-1", errors="ignore")
    xmp_patterns = {
        "xmp_creator_tool": r"<xmp:CreatorTool>([^<]+)</xmp:CreatorTool>",
        "xmp_producer": r"<pdf:Producer>([^<]+)</pdf:Producer>",
        "xmp_create_date": r"<xmp:CreateDate>([^<]+)</xmp:CreateDate>",
        "xmp_modify_date": r"<xmp:ModifyDate>([^<]+)</xmp:ModifyDate>",
        "xmp_software_agents": r"<stEvt:softwareAgent>([^<]+)</stEvt:softwareAgent>",
    }
    for field_key, rx in xmp_patterns.items():
        found = re.findall(rx, text_latin, re.IGNORECASE)
        if found:
            info[field_key] = " | ".join(f.strip() for f in found if f.strip())

    if re.search(r"<stEvt:action>\s*modified\s*</stEvt:action>", text_latin, re.IGNORECASE):
        info["xmp_has_modified_action"] = "true"

    return info


def _extract_pdfinfo_cli(file_path: str) -> dict[str, str]:
    parsed: dict[str, str] = {}
    try:
        proc = subprocess.run(
            ["/usr/bin/pdfinfo", file_path],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
        if proc.returncode == 0:
            for line in proc.stdout.splitlines():
                if ":" in line:
                    k, v = line.split(":", 1)
                    key = k.strip().lower().replace(" ", "_")
                    val = v.strip()
                    if val:
                        parsed[key] = val
    except Exception:
        pass
    return parsed


def _parse_pdf_or_iso_date(raw: str | None) -> datetime | None:
    if not raw:
        return None
    s = raw.strip()
    if s.startswith("D:"):
        s = s[2:]
        m = re.match(r"^(\d{4})(\d{2})?(\d{2})?(\d{2})?(\d{2})?(\d{2})?([+-Z])?(\d{2})?'?(\d{2})?", s)
        if m:
            year = int(m.group(1))
            month = int(m.group(2) or 1)
            day = int(m.group(3) or 1)
            hour = int(m.group(4) or 0)
            minute = int(m.group(5) or 0)
            sec = int(m.group(6) or 0)
            try:
                return datetime(year, month, day, hour, minute, sec, tzinfo=timezone.utc)
            except ValueError:
                return None

    # ISO-8601 or pdfinfo human format
    iso_clean = s.replace("Z", "+00:00")
    try:
        dt = datetime.fromisoformat(iso_clean)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc)
    except ValueError:
        pass

    for fmt in ("%a %b %d %H:%M:%S %Y %Z", "%a %b %d %H:%M:%S %Y", "%Y:%m:%d %H:%M:%S"):
        try:
            return datetime.strptime(s, fmt).replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    return None


def analyze_forensics(
    data: bytes,
    file_path: str | None = None,
    filename: str | None = None,
    disallow_office_or_canva: bool = True,
) -> tuple[list[ForensicCheck], int, dict[str, Any]]:
    checks: list[ForensicCheck] = []
    risk = 0
    meta: dict[str, Any] = {}

    file_type = detect_file_type(data, filename)
    if file_type is None:
        checks.append(
            ForensicCheck(
                code="MAGIC_SIGNATURE",
                severity="CRITICAL",
                passed=False,
                points=80,
                detail="Assinatura binária inválida ou formato não suportado. Envie PDF, PNG ou JPEG autêntico.",
            )
        )
        return checks, 100, {"fileType": "unknown"}

    meta["fileType"] = file_type
    checks.append(
        ForensicCheck(
            code="MAGIC_SIGNATURE",
            severity="INFO",
            passed=True,
            points=0,
            detail=f"Assinatura binária íntegra confirmada ({file_type.upper()}).",
        )
    )

    if file_type == "pdf":
        # 1. Active / dangerous PDF payloads
        found_active = [tok.decode("ascii") for tok in ACTIVE_PDF_TOKENS if tok in data]
        if found_active:
            risk += 100
            checks.append(
                ForensicCheck(
                    code="PDF_ACTIVE_CONTENT",
                    severity="CRITICAL",
                    passed=False,
                    points=100,
                    detail=f"Conteúdo ativo suspeito detectado no PDF ({', '.join(found_active)}).",
                )
            )
        else:
            checks.append(
                ForensicCheck(
                    code="PDF_ACTIVE_CONTENT",
                    severity="INFO",
                    passed=True,
                    points=0,
                    detail="Nenhum script embarcado, ação automática ou anexo oculto encontrado.",
                )
            )

        # 2. Extract metadata from raw PDF dictionary + XMP + pdfinfo
        raw_meta = _extract_pdf_info_raw(data)
        cli_meta = _extract_pdfinfo_cli(file_path) if file_path else {}

        creator = raw_meta.get("creator") or cli_meta.get("creator") or raw_meta.get("xmp_creator_tool") or ""
        producer = raw_meta.get("producer") or cli_meta.get("producer") or raw_meta.get("xmp_producer") or ""
        author = raw_meta.get("author") or cli_meta.get("author") or ""
        software_agents = raw_meta.get("xmp_software_agents") or ""
        creation_raw = raw_meta.get("creationdate") or cli_meta.get("creationdate") or raw_meta.get("xmp_create_date") or ""
        mod_raw = raw_meta.get("moddate") or cli_meta.get("moddate") or raw_meta.get("xmp_modify_date") or ""

        meta.update(
            {
                "creator": creator or None,
                "producer": producer or None,
                "author": author or None,
                "creationDate": creation_raw or None,
                "modDate": mod_raw or None,
            }
        )

        # 3. Check software signature for tampering / editors
        combined_sw = " | ".join(filter(None, [creator, producer, author, software_agents])).lower()
        tamper_hits = [kw for kw in TAMPER_SOFTWARE_KEYWORDS if kw in combined_sw]
        office_hits = [kw for kw in MANUAL_OFFICE_KEYWORDS if kw in combined_sw]

        if tamper_hits:
            pts = 65
            risk += pts
            checks.append(
                ForensicCheck(
                    code="METADATA_SOFTWARE_SIGNATURE",
                    severity="CRITICAL",
                    passed=False,
                    points=pts,
                    detail=f"Metadados indicam uso de editor de imagem/PDF pós-emissão ({', '.join(tamper_hits)}).",
                )
            )
        elif office_hits:
            pts = 50 if disallow_office_or_canva else 25
            risk += pts
            checks.append(
                ForensicCheck(
                    code="METADATA_SOFTWARE_SIGNATURE",
                    severity="HIGH" if disallow_office_or_canva else "WARN",
                    passed=not disallow_office_or_canva,
                    points=pts,
                    detail=(
                        f"Documento gerado manualmente em editor de texto/design ({', '.join(office_hits)}), "
                        "incompatível com emissão automática da instituição."
                        if disallow_office_or_canva
                        else f"Metadado de editor de texto detectado ({', '.join(office_hits)}); atenção moderada."
                    ),
                )
            )
        elif not creator and not producer and not creation_raw:
            pts = 30
            risk += pts
            checks.append(
                ForensicCheck(
                    code="METADATA_STRIPPED",
                    severity="WARN",
                    passed=False,
                    points=pts,
                    detail="Metadados de origem (Creator/Producer/Data) ausentes ou removidos.",
                )
            )
        else:
            checks.append(
                ForensicCheck(
                    code="METADATA_SOFTWARE_SIGNATURE",
                    severity="INFO",
                    passed=True,
                    points=0,
                    detail=f"Assinatura de software compatível com geração original ({producer or creator}).",
                )
            )

        # 4. Incremental PDF revisions (%%EOF count & /Prev)
        eof_count = len(re.findall(rb"%%EOF", data))
        prev_count = len(re.findall(rb"/Prev\s+\d+", data))
        is_linearized = b"/Linearized" in data[:2048]
        meta["eofCount"] = eof_count
        meta["isLinearized"] = is_linearized

        suspicious_revisions = (eof_count >= 3) or (not is_linearized and eof_count >= 2) or prev_count >= 1
        if suspicious_revisions:
            pts = 60 if eof_count >= 3 else 45
            risk += pts
            checks.append(
                ForensicCheck(
                    code="INCREMENTAL_SAVE_TAMPERING",
                    severity="HIGH",
                    passed=False,
                    points=pts,
                    detail=f"Estrutura PDF possui {eof_count} revisões incrementais (%%EOF), indicando modificação após a emissão.",
                )
            )
        else:
            checks.append(
                ForensicCheck(
                    code="INCREMENTAL_SAVE_TAMPERING",
                    severity="INFO",
                    passed=True,
                    points=0,
                    detail="Estrutura de fluxo único sem salvamentos incrementais posteriores.",
                )
            )

        # 5. XMP modification history
        if raw_meta.get("xmp_has_modified_action") == "true":
            pts = 50
            risk += pts
            checks.append(
                ForensicCheck(
                    code="XMP_EDIT_HISTORY",
                    severity="HIGH",
                    passed=False,
                    points=pts,
                    detail="Histórico XMP registra evento de edição ('modified') posterior à criação.",
                )
            )

        # 6. Timestamp consistency
        c_dt = _parse_pdf_or_iso_date(creation_raw)
        m_dt = _parse_pdf_or_iso_date(mod_raw)
        now = datetime.now(timezone.utc)

        if (c_dt and c_dt > now + timedelta(days=1)) or (m_dt and m_dt > now + timedelta(days=1)):
            pts = 60
            risk += pts
            checks.append(
                ForensicCheck(
                    code="TIMESTAMP_CONSISTENCY",
                    severity="CRITICAL",
                    passed=False,
                    points=pts,
                    detail="Data de criação ou modificação nos metadados está no futuro.",
                )
            )
        elif c_dt and m_dt and (m_dt - c_dt).total_seconds() > 300:
            pts = 45
            risk += pts
            diff_min = int((m_dt - c_dt).total_seconds() // 60)
            checks.append(
                ForensicCheck(
                    code="TIMESTAMP_CONSISTENCY",
                    severity="HIGH",
                    passed=False,
                    points=pts,
                    detail=f"Metadados indicam modificação {diff_min} minuto(s) após a criação original.",
                )
            )
        else:
            checks.append(
                ForensicCheck(
                    code="TIMESTAMP_CONSISTENCY",
                    severity="INFO",
                    passed=True,
                    points=0,
                    detail="Datas de criação e modificação consistentes.",
                )
            )

    else:
        # PNG / JPEG image forensics via Pillow
        try:
            with Image.open(io.BytesIO(data)) as img:
                width, height = img.size
                meta["dimensions"] = f"{width}x{height}"
                img_info = {str(k): str(v) for k, v in img.info.items()}
                exif = img.getexif()
                exif_software = str(exif.get(0x0131, "")) if exif else ""
                exif_dt = str(exif.get(0x0132, "")) if exif else ""
                exif_orig = str(exif.get(0x9003, "")) if exif else ""
                sw_combined = " | ".join(
                    filter(
                        None,
                        [
                            img_info.get("Software", ""),
                            img_info.get("Creator", ""),
                            img_info.get("Author", ""),
                            img_info.get("XML:com.adobe.xmp", ""),
                            exif_software,
                        ],
                    )
                ).lower()
                meta["software"] = sw_combined or None

                if width < 450 or height < 320:
                    risk += 50
                    checks.append(
                        ForensicCheck(
                            code="IMAGE_RESOLUTION",
                            severity="HIGH",
                            passed=False,
                            points=50,
                            detail=f"Resolução muito baixa ({width}x{height}) para certificado legítimo.",
                        )
                    )

                tamper_hits = [kw for kw in TAMPER_SOFTWARE_KEYWORDS if kw in sw_combined]
                office_hits = [kw for kw in MANUAL_OFFICE_KEYWORDS if kw in sw_combined]
                if tamper_hits:
                    risk += 65
                    checks.append(
                        ForensicCheck(
                            code="METADATA_SOFTWARE_SIGNATURE",
                            severity="CRITICAL",
                            passed=False,
                            points=65,
                            detail=f"Metadados da imagem indicam edição em {', '.join(tamper_hits)}.",
                        )
                    )
                elif office_hits and disallow_office_or_canva:
                    risk += 50
                    checks.append(
                        ForensicCheck(
                            code="METADATA_SOFTWARE_SIGNATURE",
                            severity="HIGH",
                            passed=False,
                            points=50,
                            detail=f"Imagem exportada de editor manual ({', '.join(office_hits)}).",
                        )
                    )

                if exif_dt and exif_orig and exif_dt != exif_orig:
                    risk += 40
                    checks.append(
                        ForensicCheck(
                            code="TIMESTAMP_CONSISTENCY",
                            severity="HIGH",
                            passed=False,
                            points=40,
                            detail="Data EXIF de modificação diverge da data original.",
                        )
                    )
        except Exception as exc:
            risk += 80
            checks.append(
                ForensicCheck(
                    code="IMAGE_DECODE",
                    severity="CRITICAL",
                    passed=False,
                    points=80,
                    detail=f"Falha ao ler estrutura da imagem: {exc}",
                )
            )

    risk = min(100, risk)
    return checks, risk, meta
