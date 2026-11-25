from __future__ import annotations

from typing import Iterable


def _pdf_escape_latin1(text: str) -> str:
    encoded = text.encode("latin-1", errors="replace")
    parts: list[str] = []
    for b in encoded:
        if b in (ord("("), ord(")"), ord("\\")):
            parts.append("\\" + chr(b))
        elif 32 <= b <= 126:
            parts.append(chr(b))
        else:
            parts.append(f"\\{b:03o}")
    return "".join(parts)


def build_sample_pdf(
    lines: Iterable[str],
    creator: str = "WeasyPrint 62.3",
    producer: str = "Alura Certificate Engine / Cairo 1.18",
    author: str = "Alura Cursos Online",
    creation_date: str = "D:20260915142000Z",
    mod_date: str = "D:20260915142000Z",
    xmp_modified: bool = False,
    append_incremental_edit: bool = False,
) -> bytes:
    """Build a spec-compliant PDF 1.4 file with exact xref byte offsets."""
    stream_lines = ["BT", "/F1 13 Tf", "50 760 Td", "18 TL"]
    for idx, line in enumerate(lines):
        escaped = _pdf_escape_latin1(line)
        if idx == 0:
            stream_lines.append(f"({escaped}) Tj")
        else:
            stream_lines.append(f"T* ({escaped}) Tj")
    stream_lines.append("ET")
    content_bytes = ("\n".join(stream_lines) + "\n").encode("latin-1")

    objects: list[bytes] = [
        b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
        b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
        (
            b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
            b"/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"
        ),
        b"4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n",
        (
            f"5 0 obj\n<< /Length {len(content_bytes)} >>\nstream\n".encode("latin-1")
            + content_bytes
            + b"endstream\nendobj\n"
        ),
        (
            f"6 0 obj\n<< /Creator ({_pdf_escape_latin1(creator)}) "
            f"/Producer ({_pdf_escape_latin1(producer)}) "
            f"/Author ({_pdf_escape_latin1(author)}) "
            f"/CreationDate ({creation_date}) "
            f"/ModDate ({mod_date}) >>\nendobj\n"
        ).encode("latin-1"),
    ]

    if xmp_modified:
        xmp = (
            '<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>'
            "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>"
            "<rdf:Description xmlns:stEvt='http://ns.adobe.com/xap/1.0/sType/ResourceEvent#'>"
            "<stEvt:action>modified</stEvt:action>"
            "<stEvt:softwareAgent>Adobe Photoshop 25.0</stEvt:softwareAgent>"
            "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end='w'?>"
        ).encode("latin-1")
        objects.append(
            f"7 0 obj\n<< /Type /Metadata /Subtype /XML /Length {len(xmp)} >>\nstream\n".encode("latin-1")
            + xmp
            + b"\nendstream\nendobj\n"
        )

    buf = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets: list[int] = [0]
    for obj in objects:
        offsets.append(len(buf))
        buf.extend(obj)

    xref_pos = len(buf)
    total_objs = len(objects) + 1
    buf.extend(f"xref\n0 {total_objs}\n".encode("latin-1"))
    buf.extend(b"0000000000 65535 f \n")
    for off in offsets[1:]:
        buf.extend(f"{off:010d} 00000 n \n".encode("latin-1"))

    buf.extend(
        f"trailer\n<< /Size {total_objs} /Root 1 0 R /Info 6 0 R >>\nstartxref\n{xref_pos}\n%%EOF\n".encode(
            "latin-1"
        )
    )

    if append_incremental_edit:
        extra_off = len(buf)
        buf.extend(
            b"8 0 obj\n<< /Type /ExtGState /ca 1.0 >>\nendobj\n"
            + f"xref\n8 1\n{extra_off:010d} 00000 n \n".encode("latin-1")
            + f"trailer\n<< /Size 9 /Prev {xref_pos} /Root 1 0 R /Info 6 0 R >>\nstartxref\n{extra_off + 47}\n%%EOF\n".encode(
                "latin-1"
            )
        )

    return bytes(buf)


def genuine_alura_java(holder_name: str = "João Silva") -> bytes:
    return build_sample_pdf(
        lines=[
            "ALURA CURSOS ONLINE",
            "Certificado de Conclusão",
            f"Certificamos que {holder_name} concluiu com aproveitamento",
            "Curso: Formação Java e Spring Boot com APIs REST",
            "Carga horária: 40 horas",
            "Emitido em 15/09/2026",
            "Código de autenticidade: ALURA-982341-JAVA",
            "https://cursos.alura.com.br/certificate/982341-java",
        ],
        creator="Alura Certificate System",
        producer="WeasyPrint 62.3",
        author="Alura",
    )


def genuine_aws_cloud(holder_name: str = "João Silva") -> bytes:
    return build_sample_pdf(
        lines=[
            "Amazon Web Services (AWS)",
            "AWS Training and Certification",
            f"{holder_name} has successfully completed",
            "Course: AWS Certified Solutions Architect & Docker on Cloud",
            "Duration: 60 hours",
            "Issued on 2026-08-10",
            "Credential ID: AWS-SAA-881209",
            "https://www.credly.com/badges/aws-saa-881209",
        ],
        creator="AWS Certification Authority",
        producer="Apache FOP Version 2.9",
        author="Amazon Web Services",
    )


def forged_photoshop(holder_name: str = "João Silva") -> bytes:
    return build_sample_pdf(
        lines=[
            "ALURA CURSOS ONLINE",
            "Certificado de Conclusão",
            f"Certificamos que {holder_name} concluiu com aproveitamento",
            "Curso: Formação Java e Spring Boot",
            "Carga horária: 40 horas",
            "Emitido em 15/09/2026",
            "https://cursos.alura.com.br/certificate/999999-fake",
        ],
        creator="Adobe Photoshop 25.0 (Windows)",
        producer="Adobe PDF Library 17.0",
        author="Editor",
        creation_date="D:20260915140000Z",
        mod_date="D:20260915164500Z",
        xmp_modified=True,
        append_incremental_edit=True,
    )
