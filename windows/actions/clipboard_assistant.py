from __future__ import annotations

try:
    import pyperclip
except Exception:
    pyperclip = None

from core import gemini


def run(parameters: dict, **kwargs) -> str:
    if pyperclip is None:
        return "O recurso de área de transferência precisa do pyperclip."

    p = parameters or {}
    action = str(p.get("action", "read")).lower().strip()
    instruction = str(p.get("instruction", "")).strip()

    if action == "read":
        text = pyperclip.paste() or ""
        return text if text else "A área de transferência está vazia."

    if action == "copy":
        text = str(p.get("text", ""))
        pyperclip.copy(text)
        return "Texto copiado para a área de transferência."

    if action == "transform":
        original = pyperclip.paste() or ""
        if not original:
            return "A área de transferência está vazia."
        if not instruction:
            instruction = "Melhore a clareza e a escrita, preservando o sentido e o idioma."
        prompt = f"""Transforme o texto abaixo conforme a instrução.
Retorne somente o texto final, sem explicações.

INSTRUÇÃO:
{instruction}

TEXTO:
{original}
"""
        resp = gemini.call(prompt, tier=gemini.FAST, timeout_ms=25000)
        if resp is None:
            return "Não consegui transformar o texto agora."
        result = (getattr(resp, "text", "") or "").strip()
        if not result:
            return "O modelo não retornou texto."
        pyperclip.copy(result)
        return "Pronto. O resultado foi copiado para a área de transferência."

    return "Ação inválida. Use read, copy ou transform."


TOOL = {
    "name": "clipboard_assistant",
    "description": (
        "Lê, copia ou transforma o texto da área de transferência. Use para pedidos como "
        "'melhora o texto que eu copiei', 'traduz o que está copiado', 'resume isso' ou "
        "'copia esse texto'."
    ),
    "parameters": {
        "type": "OBJECT",
        "properties": {
            "action": {"type": "STRING"},
            "instruction": {"type": "STRING"},
            "text": {"type": "STRING"}
        },
        "required": ["action"]
    },
    "handler": run,
}
