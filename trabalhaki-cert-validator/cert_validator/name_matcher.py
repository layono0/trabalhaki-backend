from __future__ import annotations

import re
from difflib import SequenceMatcher
from unidecode import unidecode

from .models import HolderMatch

_STOPWORDS = {"de", "da", "do", "dos", "das", "e", "del", "di"}
_PLACEHOLDER_NAMES = {"candidato", "usuario", "user", "teste", "test", "nome"}


def _tokenize_name(value: str) -> list[str]:
    clean = unidecode(value or "").lower()
    raw_tokens = re.findall(r"[a-z]+", clean)
    return [tok for tok in raw_tokens if tok not in _STOPWORDS and len(tok) > 0]


def verify_holder_name(profile_name: str, document_text: str) -> HolderMatch:
    profile_tokens = _tokenize_name(profile_name)
    if len(profile_tokens) < 2 or profile_tokens[0] in _PLACEHOLDER_NAMES:
        return HolderMatch(
            matched=False,
            similarity=0.0,
            profile_name=profile_name,
            detail="Atualize seu nome completo (nome e sobrenome) no perfil antes de validar certificados.",
        )

    doc_tokens = _tokenize_name(document_text)
    if not doc_tokens:
        return HolderMatch(
            matched=False,
            similarity=0.0,
            profile_name=profile_name,
            detail="Documento sem texto legível para conferência de titularidade.",
        )

    first_name = profile_tokens[0]
    last_name = profile_tokens[-1]
    window_size = len(profile_tokens) + 4

    best_score = 0.0
    best_snippet: str | None = None
    best_full_match = False

    for idx, tok in enumerate(doc_tokens):
        if tok != first_name and SequenceMatcher(None, tok, first_name).ratio() < 0.88:
            continue

        window = doc_tokens[idx : idx + window_size]
        if not window:
            continue

        # Last surname must appear in the same local window (prevents unrelated cross-document token collisions)
        surname_hit = any(
            wt == last_name or SequenceMatcher(None, wt, last_name).ratio() >= 0.88
            for wt in window[1:]
        )
        if not surname_hit:
            continue

        matched_tokens = 0
        for pt in profile_tokens:
            if len(pt) == 1:
                if any(wt.startswith(pt) for wt in window):
                    matched_tokens += 1
            else:
                if any(wt == pt or SequenceMatcher(None, wt, pt).ratio() >= 0.88 for wt in window):
                    matched_tokens += 1

        coverage = matched_tokens / len(profile_tokens)
        # Measure order/edit similarity over candidate-length slice
        slice_end = min(len(window), len(profile_tokens) + 2)
        seq_ratio = SequenceMatcher(
            None,
            " ".join(profile_tokens),
            " ".join(window[:slice_end]),
        ).ratio()
        combined = max(coverage * 0.85 + seq_ratio * 0.15, coverage if coverage == 1.0 else 0.0)

        if combined > best_score:
            best_score = combined
            best_snippet = " ".join(window[:slice_end])
            best_full_match = coverage >= 0.80 and surname_hit

    if best_full_match and best_score >= 0.78:
        return HolderMatch(
            matched=True,
            similarity=min(1.0, best_score),
            profile_name=profile_name,
            extracted_snippet=best_snippet,
            detail=f"Titularidade confirmada: nome compatível com '{profile_name}'.",
        )

    return HolderMatch(
        matched=False,
        similarity=best_score,
        profile_name=profile_name,
        extracted_snippet=best_snippet,
        detail=f"O nome completo '{profile_name}' não corresponde ao titular exibido no certificado.",
    )
