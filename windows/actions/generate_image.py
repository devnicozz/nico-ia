from __future__ import annotations
import base64, io, json, os, sys
from datetime import datetime
from pathlib import Path
from google import genai

def _base() -> Path:
    return Path(sys.executable).parent if getattr(sys, "frozen", False) else Path(__file__).resolve().parent.parent

def _key() -> str:
    try:
        return json.loads((_base()/"config"/"api_keys.json").read_text(encoding="utf-8")).get("gemini_api_key","")
    except Exception:
        return ""

def _screen_b64():
    import mss
    from PIL import Image
    with mss.mss() as sct:
        mon=sct.monitors[1] if len(sct.monitors)>1 else sct.monitors[0]
        shot=sct.grab(mon)
        img=Image.frombytes("RGB",shot.size,shot.rgb)
        b=io.BytesIO(); img.save(b,format="PNG")
        return base64.b64encode(b.getvalue()).decode("ascii")

def run(parameters: dict, player=None, **kwargs) -> str:
    p=parameters or {}
    prompt=str(p.get("prompt","")).strip()
    if not prompt: return "Descreva a imagem que você quer criar."
    count=max(1,min(8,int(p.get("count",1) or 1)))
    ratio=str(p.get("aspect_ratio","1:1") or "1:1")
    quality=str(p.get("quality","1K") or "1K").upper()
    use_screen=bool(p.get("use_screen_reference",False))
    high=bool(p.get("high_quality",False)) or quality in ("2K","4K")
    model="gemini-3.1-flash-image" if high else "gemini-3.1-flash-lite-image"
    if model.endswith("lite-image"): quality="1K"
    key=_key()
    if not key: return "A Gemini API Key não está configurada."
    client=genai.Client(api_key=key)
    out=(Path.home()/"Pictures"/"NICO Creations"); out.mkdir(parents=True,exist_ok=True)
    ref=_screen_b64() if use_screen else None
    saved=[]
    for i in range(count):
        inp=prompt
        if ref:
            inp=[
                {"type":"text","text":prompt+"\\nUse a identidade visual visível na referência: cores, tipografia, formas, espaçamento e direção de arte. Ignore barras do navegador e elementos do Windows."},
                {"type":"image","data":ref,"mime_type":"image/png"},
            ]
        interaction=client.interactions.create(
            model=model,
            input=inp,
            response_format={"type":"image","mime_type":"image/png","aspect_ratio":ratio,"image_size":quality},
        )
        img=getattr(interaction,"output_image",None)
        data=getattr(img,"data",None) if img else None
        if not data: continue
        dest=out/(datetime.now().strftime("NICO_%Y%m%d_%H%M%S_")+f"{i+1}.png")
        dest.write_bytes(base64.b64decode(data)); saved.append(dest)
    if not saved: return "A API não retornou nenhuma imagem."
    try:
        if os.name=="nt": os.startfile(str(saved[-1]))
    except Exception: pass
    return f"Criei {len(saved)} imagem(ns) em {out}."

TOOL={
 "name":"generate_image",
 "description":"Cria imagens, logos, banners, thumbnails, mockups, wallpapers e outros visuais. Pode usar a tela atual como referência visual. Salva automaticamente em Imagens/NICO Creations.",
 "parameters":{"type":"OBJECT","properties":{
   "prompt":{"type":"STRING"},
   "aspect_ratio":{"type":"STRING"},
   "quality":{"type":"STRING"},
   "count":{"type":"INTEGER"},
   "use_screen_reference":{"type":"BOOLEAN"},
   "high_quality":{"type":"BOOLEAN"}
 },"required":["prompt"]},
 "handler":run,
}
