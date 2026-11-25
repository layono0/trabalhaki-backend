# Trabalhaki — Verificador de Competências (Python)

Serviço e CLI em Python para validação de certificados profissionais, análise forense de metadados antifraude e cálculo de bônus de prioridade nas vagas do Trabalhaki.

## Regras implementadas

1. **Assinatura binária e integridade:** verifica *magic bytes* (PDF/PNG/JPEG) e bloqueia conteúdo ativo em PDF (`/JavaScript`, `/JS`, `/Launch`, `/OpenAction`, `/EmbeddedFiles`).
2. **Antifraude por metadados:** analisa `/Info`, `pdfinfo`, pacotes XMP (`CreatorTool`, `Producer`, `xmpMM:History`) e EXIF/PNG chunks para detectar editores pós-emissão (`Photoshop`, `GIMP`, `iLovePDF`, `Smallpdf`, `Sejda`, `Foxit`, etc.), criação manual (`Word`, `Canva`, `LibreOffice`), salvamentos incrementais (`%%EOF` múltiplos sem linearização) e inconsistência de datas (`ModDate > CreationDate` ou timestamps futuros).
3. **Instituições reconhecidas:** catálogo em 3 níveis (Tier 1: AWS, Microsoft, Google, USP, UNICAMP, FIAP, SENAI, SENAC, IFs; Tier 2: Alura, Rocketseat, DIO, Coursera, Udemy, Udacity; Tier 3: Fundação Bradesco, ENAP, SEBRAE, Curso em Vídeo).
4. **Titularidade do candidato:** compara o nome completo do perfil com janela contígua no certificado (normalizando acentos e aceitando nomes do meio, mas bloqueando primeiro nome ou sobrenome divergentes).
5. **Competências e bônus de prioridade:** extrai declaração de conclusão, carga horária, código/URL de autenticidade e habilidades do catálogo Trabalhaki, atribuindo bônus de `+3` a `+9` pontos por certificado.

## Execução local

```bash
# Testes automatizados
python3 -m unittest discover -s tests -v

# CLI JSON
python3 -m cert_validator.cli validate --file certificado.pdf --candidate-name "João Silva"

# Servidor HTTP (porta 8090)
python3 -m cert_validator.app
```
