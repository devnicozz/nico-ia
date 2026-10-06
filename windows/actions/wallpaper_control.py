from __future__ import annotations
import ctypes, os, shutil, tempfile
from pathlib import Path
from urllib.request import Request, urlopen

SPI_SETDESKWALLPAPER=20
SPIF_UPDATEINIFILE=0x01
SPIF_SENDCHANGE=0x02

def _download(url: str) -> Path:
    suffix=Path(url.split("?")[0]).suffix.lower()
    if suffix not in (".jpg",".jpeg",".png",".bmp",".webp"): suffix=".jpg"
    out=Path.home()/"Pictures"/"NICO Wallpapers"; out.mkdir(parents=True,exist_ok=True)
    dst=out/("wallpaper"+suffix)
    req=Request(url,headers={"User-Agent":"Mozilla/5.0"})
    with urlopen(req,timeout=25) as r, open(dst,"wb") as f: shutil.copyfileobj(r,f)
    return dst

def run(parameters: dict, **kwargs) -> str:
    p=parameters or {}
    path=str(p.get("path","")).strip()
    url=str(p.get("url","")).strip()
    if url: path=str(_download(url))
    if not path: return "Informe o caminho de uma imagem ou uma URL direta."
    img=Path(os.path.expandvars(os.path.expanduser(path))).resolve()
    if not img.exists(): return f"Imagem não encontrada: {img}"
    if os.name!="nt": return "Troca de wallpaper automática está disponível no Windows."
    ok=ctypes.windll.user32.SystemParametersInfoW(SPI_SETDESKWALLPAPER,0,str(img),SPIF_UPDATEINIFILE|SPIF_SENDCHANGE)
    return f"Wallpaper alterado para {img}" if ok else "O Windows não aceitou a troca de wallpaper."

TOOL={
 "name":"wallpaper_control",
 "description":"Define uma imagem local ou uma URL direta de imagem como wallpaper do Windows. Pode ser usada depois de generate_image para colocar a imagem criada como papel de parede.",
 "parameters":{"type":"OBJECT","properties":{"path":{"type":"STRING"},"url":{"type":"STRING"}}},
 "handler":run,
}
