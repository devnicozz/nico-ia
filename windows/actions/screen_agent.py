from __future__ import annotations

import io
import json
import re
import time

from google.genai import types as gtypes
from core import gemini

try:
    import pyautogui
    _PYAUTO = True
except Exception:
    _PYAUTO = False


def _shot() -> bytes:
    import mss
    from PIL import Image
    with mss.mss() as sct:
        mon = sct.monitors[1] if len(sct.monitors) > 1 else sct.monitors[0]
        raw = sct.grab(mon)
        img = Image.frombytes("RGB", raw.size, raw.rgb)
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue()


def _json(text: str) -> dict:
    s = (text or "").strip()
    if s.startswith("```"):
        s = re.sub(r"^```(?:json)?\\s*", "", s, flags=re.I)
        s = re.sub(r"\\s*```$", "", s)
    start, end = s.find("{"), s.rfind("}")
    if start >= 0 and end > start:
        s = s[start:end + 1]
    return json.loads(s)


def _plan(task: str, image: bytes, history: list[str]) -> dict:
    prompt = f"""You are NICO's Windows visual-control planner.

USER GOAL:
{task}

WHAT HAS ALREADY HAPPENED:
{history[-6:]}

Look at the attached CURRENT screenshot and return ONLY JSON:
{{
  "done": false,
  "message": "short status in Portuguese",
  "actions": [
    {{"type":"click","x":0.50,"y":0.50}},
    {{"type":"double_click","x":0.50,"y":0.50}},
    {{"type":"type","text":"text to type"}},
    {{"type":"hotkey","keys":["ctrl","l"]}},
    {{"type":"scroll","amount":-600}},
    {{"type":"wait","seconds":1}}
  ]
}}

Rules:
- x/y are normalized 0..1 positions on the screenshot.
- Use at most 3 actions in one response.
- Prefer keyboard shortcuts when reliable.
- Never invent that an action succeeded: inspect the next screenshot.
- Do not use terminal, PowerShell, registry, developer console, shell commands, downloads, purchases, sending messages, deleting files, or changing passwords through visual automation.
- For destructive/sensitive actions, stop with done=true and explain that another dedicated NICO tool should handle it.
- When the goal is visibly complete, return done=true and actions=[].
"""
    response = gemini.call(
        [gtypes.Part.from_bytes(data=image, mime_type="image/png"), prompt],
        tier=gemini.SMART,
        timeout_ms=35000,
    )
    if response is None:
        raise RuntimeError("Gemini vision did not respond")
    return _json(getattr(response, "text", "") or "")


def _execute(action: dict, w: int, h: int) -> str:
    kind = str(action.get("type", "")).strip().lower()
    if kind in ("click", "double_click"):
        x = max(0.0, min(1.0, float(action.get("x", 0.5)))) * w
        y = max(0.0, min(1.0, float(action.get("y", 0.5)))) * h
        if kind == "double_click":
            pyautogui.doubleClick(int(x), int(y), interval=0.12)
        else:
            pyautogui.click(int(x), int(y))
        return f"{kind}@{int(x)},{int(y)}"
    if kind == "type":
        text = str(action.get("text", ""))
        pyautogui.write(text, interval=0.01)
        return "typed text"
    if kind == "hotkey":
        keys = [str(k).lower() for k in action.get("keys", [])][:4]
        allowed = {
            "ctrl","shift","alt","win","enter","tab","esc","space","backspace",
            "left","right","up","down","home","end","pageup","pagedown",
            "a","c","v","x","l","f","t","w"
        }
        if not keys or any(k not in allowed for k in keys):
            raise ValueError("hotkey not allowed")
        pyautogui.hotkey(*keys)
        return "hotkey " + "+".join(keys)
    if kind == "scroll":
        amount = int(action.get("amount", 0))
        pyautogui.scroll(max(-1400, min(1400, amount)))
        return f"scroll {amount}"
    if kind == "wait":
        sec = max(0.1, min(4.0, float(action.get("seconds", 1))))
        time.sleep(sec)
        return f"wait {sec}s"
    raise ValueError(f"unsupported visual action: {kind}")


def run(parameters: dict, player=None, **kwargs) -> str:
    p = parameters or {}
    task = str(p.get("task", "")).strip()
    execute = bool(p.get("execute", True))
    max_steps = max(1, min(8, int(p.get("max_steps", 5) or 5)))
    if not task:
        return "Diga o que você quer que eu observe ou faça na tela."
    if not _PYAUTO and execute:
        return "O controle visual precisa do pyautogui."

    history: list[str] = []
    last_message = ""

    for step in range(max_steps):
        image = _shot()
        if not execute:
            plan = _plan(task, image, history)
            return str(plan.get("message") or "Analisei a tela.")

        import PIL.Image
        img = PIL.Image.open(io.BytesIO(image))
        w, h = img.size

        try:
            plan = _plan(task, image, history)
        except Exception as exc:
            return f"Não consegui analisar a tela: {exc}"

        last_message = str(plan.get("message") or "")
        if bool(plan.get("done", False)):
            return last_message or "Pronto."

        actions = plan.get("actions") or []
        if not actions:
            return last_message or "Não encontrei uma ação visual segura para continuar."

        for action in actions[:3]:
            try:
                history.append(_execute(action, w, h))
                time.sleep(0.35)
            except Exception as exc:
                history.append(f"erro: {exc}")
                return f"Parei o controle visual porque uma ação falhou: {exc}"

    return last_message or "Cheguei ao limite de passos. Posso continuar se você pedir."


TOOL = {
    "name": "screen_agent",
    "description": (
        "Olha a tela atual do Windows e, quando apropriado, controla visualmente mouse/teclado para "
        "tarefas comuns como clicar botões, navegar janelas e preencher campos. Use quando o usuário "
        "disser 'olha minha tela', 'clica nisso', 'faz isso que está na tela' ou pedir uma tarefa "
        "visual que não tenha uma ferramenta dedicada. Não use para ações destrutivas, senhas, compras, "
        "mensagens ou exclusão de arquivos."
    ),
    "parameters": {
        "type": "OBJECT",
        "properties": {
            "task": {"type": "STRING"},
            "execute": {"type": "BOOLEAN"},
            "max_steps": {"type": "INTEGER"}
        },
        "required": ["task"]
    },
    "handler": run,
}
