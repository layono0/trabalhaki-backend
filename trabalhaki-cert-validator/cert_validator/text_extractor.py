from __future__ import annotations

import io
import re
import subprocess
import zlib

from PIL import Image

from .forensics import _decode_pdf_literal, detect_file_type


def _extract_pdf_text_fallback(data: bytes) -> str:
    """Pure-Python fallback extracting literal text operators from uncompressed or Flate streams."""
    chunks: list[str] = []

    def scan_stream(stream_bytes: bytes) -> None:
        text_latin = stream_bytes.decode("latin-1", errors="ignore")
        for match in re.findall(r"\(([^()]*(?:\\.[^()]*)*)\)\s*Tj", text_latin):
            chunks.append(_decode_pdf_literal(match.encode("latin-1", errors="ignore")))
        for arr in re.findall(r"\[(.*?)\]\s*TJ", text_latin, re.DOTALL):
            parts = re.findall(r"\(([^()]*(?:\\.[^()]*)*)\)", arr)
            if parts:
                chunks.append(
                    "".join(_decode_pdf_literal(p.encode("latin-1", errors="ignore")) for p in parts)
                )

    scan_stream(data)
    for stream_match in re.finditer(rb"stream\r?\n(.*?)\r?\nendstream", data, re.DOTALL):
        raw_stream = stream_match.group(1)
        try:
            inflated = zlib.decompress(raw_stream)
            scan_stream(inflated)
        except Exception:
            pass

    return "\n".join(c.strip() for c in chunks if c.strip())


def extract_document_text(data: bytes, file_path: str | None = None) -> tuple[str, str]:
    """Return (extracted_text, extraction_method)."""
    file_type = detect_file_type(data)
    if file_type == "pdf":
        if file_path:
            try:
                proc = subprocess.run(
                    ["/usr/bin/pdftotext", "-enc", "UTF-8", file_path, "-"],
                    capture_output=True,
                    text=True,
                    timeout=5,
                    check=False,
                )
                if proc.returncode == 0 and proc.stdout.strip():
                    return proc.stdout.strip(), "pdftotext"
            except Exception:
                pass
        return _extract_pdf_text_fallback(data), "pdf-stream-parser"

    if file_type in ("png", "jpg"):
        # Inspect embedded metadata text (e.g. PNG tEXt/iTXt or EXIF UserComment) if present
        try:
            with Image.open(io.BytesIO(data)) as img:
                text_parts = []
                for key in ("Description", "CertificatePayload", "Comment", "Title", "XML:com.adobe.xmp"):
                    val = img.info.get(key)
                    if isinstance(val, str) and val.strip():
                        text_parts.append(val.strip())
                return "\n".join(text_parts), "image-metadata-text"
        except Exception:
            return "", "image-unreadable"

    return "", "unsupported"
