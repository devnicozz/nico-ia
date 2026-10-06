from __future__ import annotations
import io, os, re
from datetime import datetime
from pathlib import Path
from google.genai import types as gtypes
from core import gemini

def _capture():
    import mss
    from PIL import Image
    with mss.mss() as sct:
        mon=sct.monitors[1] if len(sct.monitors)>1 else sct.monitors[0]
        shot=sct.grab(mon)
        img=Image.frombytes("RGB",shot.size,shot.rgb)
        b=io.BytesIO(); img.save(b,format="PNG")
        return b.getvalue()

def _desktop():
    h=Path.home()
    for p in (h/"Desktop",h/"OneDrive"/"Desktop",h/"Área de Trabalho",h/"OneDrive"/"Área de Trabalho"):
        if p.exists(): return p
    return h

def _html(text):
    text=(text or "").strip()
    blocks=re.findall(r"\`\`\`(?:html)?\s*(.*?)\`\`\`",text,re.S|re.I)
    if blocks: text=max(blocks,key=len).strip()
    low=text.lower(); i=low.find("<!doctype html")
    if i<0: i=low.find("<html")
    return text[i:] if i>=0 else text

def run(parameters: dict, player=None, **kwargs) -> str:
    p=parameters or {}
    req=str(p.get("request","")).strip() or "Crie um site completo e sofisticado usando a identidade visual da tela."
    use_screen=bool(p.get("use_screen_reference",True))
    name=re.sub(r"[^a-zA-Z0-9_-]+","-",str(p.get("project_name","site-nico"))).strip("-") or "site-nico"
    prompt=f"""Você é um diretor de arte e desenvolvedor front-end sênior.
PEDIDO: {req}
Crie um site realmente finalizado, sofisticado, responsivo e sem aparência genérica de IA.
Se houver imagem anexada, use somente a identidade visual relevante visível nela: paleta, tipografia, logo, formas, fotografia e ritmo. Ignore navegador, barra de tarefas e interface do sistema.
Retorne SOMENTE um HTML completo com CSS e JavaScript embutidos, sem frameworks, pronto para abrir.
Use português quando o pedido estiver em português. Não invente endereço, preço, depoimento ou fato comercial que não esteja fornecido.
Animações suaves, ótima versão mobile, acessibilidade e hierarquia visual forte."""
    contents=[prompt]
    if use_screen:
        contents=[gtypes.Part.from_bytes(data=_capture(),mime_type="image/png"),prompt]
    resp=gemini.call(contents,tier=gemini.SMART,timeout_ms=90000)
    if resp is None: return "Não consegui gerar o site agora."
    html=_html(getattr(resp,"text","") or "")
    if "<html" not in html.lower(): return "O modelo não retornou um HTML válido."
    folder=_desktop()/"NICO Sites"/name
    if folder.exists(): folder=folder.parent/(name+"-"+datetime.now().strftime("%H%M%S"))
    folder.mkdir(parents=True,exist_ok=True)
    index=folder/"index.html"; index.write_text(html,encoding="utf-8")
    if use_screen:
        try: (folder/"referencia-visual.png").write_bytes(_capture())
        except Exception: pass
    try:
        if os.name=="nt": os.startfile(str(index))
    except Exception: pass
    return f"Site criado e aberto. Pasta: {folder}"

TOOL={
 "name":"create_website",
 "description":"Cria site completo em index.html. Use quando o usuário pedir site, landing page ou disser para olhar uma identidade visual na tela e criar o site.",
 "parameters":{"type":"OBJECT","properties":{
   "request":{"type":"STRING"},
   "project_name":{"type":"STRING"},
   "use_screen_reference":{"type":"BOOLEAN"}
 },"required":["request"]},
 "handler":run,
}
