from __future__ import annotations

import os
import tempfile
from pathlib import Path

from flask import Flask, jsonify, request

from .engine import validate_certificate_bytes
from .institutions import list_institutions_catalog

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 6 * 1024 * 1024  # 6 MB

_UI_HTML = """<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Trabalhaki — Verificador de Competências</title>
  <style>
    :root {
      --primary: #393be7;
      --ink: #12152e;
      --mint: #86e5a1;
      --mint-soft: #eafbf0;
      --forest: #0f6b3a;
      --bg: #f8f9fc;
      --surface: #ffffff;
      --border: #e1e6f2;
      --danger: #b42318;
      --danger-bg: #fef3f2;
    }
    * { box-sizing: border-box; }
    body { margin: 0; font-family: system-ui, -apple-system, sans-serif; background: var(--bg); color: var(--ink); padding: 32px 16px; }
    .wrap { max-width: 760px; margin: 0 auto; background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 28px; }
    h1 { margin: 0 0 6px; font-size: 1.5rem; }
    p.sub { margin: 0 0 22px; color: #4a5370; font-size: 0.95rem; }
    .field { margin-bottom: 14px; }
    label { display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 6px; }
    input[type="text"], input[type="file"] { width: 100%; padding: 10px 12px; border: 1px solid #c6cfe4; border-radius: 12px; font-size: 0.95rem; }
    button { min-height: 44px; padding: 0 20px; border: 0; border-radius: 12px; background: var(--primary); color: #fff; font-weight: 700; cursor: pointer; }
    .result { margin-top: 22px; padding: 18px; border-radius: 12px; border: 1px solid var(--border); background: var(--bg); }
    .pill { display: inline-block; padding: 4px 10px; border-radius: 999px; font-size: 0.78rem; font-weight: 700; }
    .ok { background: var(--mint-soft); color: var(--forest); }
    .bad { background: var(--danger-bg); color: var(--danger); }
    pre { overflow-x: auto; font-size: 0.8rem; background: #fff; padding: 12px; border-radius: 8px; border: 1px solid var(--border); }
  </style>
</head>
<body>
  <main class="wrap">
    <h1>Verificador de Competências — Trabalhaki</h1>
    <p class="sub">Auditoria automática de metadados antifraude, instituição reconhecida e titularidade do certificado.</p>
    <form id="form">
      <div class="field">
        <label for="candidateName">Nome completo do candidato</label>
        <input id="candidateName" name="candidateName" type="text" required placeholder="Ex: João Silva" />
      </div>
      <div class="field">
        <label for="targetSkill">Competência alvo (opcional)</label>
        <input id="targetSkill" name="targetSkill" type="text" placeholder="Ex: Java, AWS, Flutter" />
      </div>
      <div class="field">
        <label for="file">Certificado (PDF, PNG ou JPEG)</label>
        <input id="file" name="file" type="file" accept=".pdf,.png,.jpg,.jpeg" required />
      </div>
      <button type="submit">Validar certificado</button>
    </form>
    <div id="out" class="result" hidden></div>
  </main>
  <script>
    const form = document.getElementById('form');
    const out = document.getElementById('out');
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(form);
      const res = await fetch('/api/v1/validate', { method: 'POST', body: fd });
      const data = await res.json();
      out.hidden = false;
      out.innerHTML = `<span class="pill ${data.accepted ? 'ok' : 'bad'}">${data.status}</span>
        <p><strong>${data.summary || ''}</strong></p>
        <pre>${JSON.stringify(data, null, 2)}</pre>`;
    });
  </script>
</body>
</html>
"""


@app.after_request
def add_cors_headers(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    return response


@app.get("/")
def index():
    return _UI_HTML, 200, {"Content-Type": "text/html; charset=utf-8"}


@app.get("/health")
def health():
    return jsonify({"status": "UP", "service": "trabalhaki-cert-validator"})


@app.get("/api/v1/institutions")
def institutions():
    return jsonify({"institutions": list_institutions_catalog()})


@app.post("/api/v1/validate")
def validate_endpoint():
    uploaded = request.files.get("file")
    candidate_name = (request.form.get("candidateName") or "").strip()
    target_skill = (request.form.get("targetSkill") or "").strip() or None

    if uploaded is None or not uploaded.filename:
        return jsonify({"accepted": False, "status": "REJECTED", "summary": "Envie o arquivo do certificado."}), 400
    if not candidate_name:
        return jsonify({"accepted": False, "status": "REJECTED", "summary": "Informe candidateName."}), 400

    data = uploaded.read()
    suffix = Path(uploaded.filename).suffix.lower() or ".pdf"
    if suffix not in (".pdf", ".png", ".jpg", ".jpeg"):
        suffix = ".bin"

    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(data)
            tmp_path = tmp.name
        result = validate_certificate_bytes(
            data=data,
            candidate_name=candidate_name,
            filename=uploaded.filename,
            file_path=tmp_path,
            target_skill=target_skill,
        )
        return jsonify(result.to_dict()), 200
    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except OSError:
                pass


if __name__ == "__main__":
    host = os.environ.get("HOST", "0.0.0.0")
    port = int(os.environ.get("PORT", "8090"))
    app.run(host=host, port=port, debug=False, use_reloader=False, threaded=True)
