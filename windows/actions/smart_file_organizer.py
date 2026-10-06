from __future__ import annotations

import hashlib
import os
import shutil
from datetime import datetime
from pathlib import Path

TYPE_MAP = {
    "Imagens": {".jpg",".jpeg",".png",".gif",".webp",".bmp",".svg",".heic",".ico"},
    "Documentos": {".pdf",".doc",".docx",".txt",".rtf",".xls",".xlsx",".ppt",".pptx",".csv"},
    "Videos": {".mp4",".mkv",".mov",".avi",".webm",".m4v"},
    "Musicas": {".mp3",".wav",".flac",".aac",".ogg",".m4a"},
    "Compactados": {".zip",".rar",".7z",".tar",".gz"},
    "Codigo": {".html",".css",".js",".ts",".py",".json",".xml",".java",".cpp",".cs",".php"},
    "Instaladores": {".exe",".msi",".msix",".apk"},
}


def _resolve(raw: str) -> Path:
    raw = (raw or "").strip()
    if not raw or raw.casefold() in ("downloads","download","baixados"):
        return Path.home() / "Downloads"
    if raw.casefold() in ("desktop","area de trabalho","área de trabalho"):
        for p in (Path.home()/"Desktop", Path.home()/"OneDrive"/"Desktop"):
            if p.exists():
                return p
    return Path(os.path.expandvars(os.path.expanduser(raw))).resolve()


def _category(p: Path) -> str:
    ext = p.suffix.lower()
    for name, exts in TYPE_MAP.items():
        if ext in exts:
            return name
    return "Outros"


def _unique(dest: Path) -> Path:
    if not dest.exists():
        return dest
    i = 2
    while True:
        candidate = dest.with_name(f"{dest.stem} ({i}){dest.suffix}")
        if not candidate.exists():
            return candidate
        i += 1


def _hash(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def run(parameters: dict, **kwargs) -> str:
    p = parameters or {}
    folder = _resolve(str(p.get("folder", "Downloads")))
    mode = str(p.get("mode", "by_type")).lower().strip()
    apply = bool(p.get("apply", False))

    if not folder.exists() or not folder.is_dir():
        return f"Pasta não encontrada: {folder}"

    files = [x for x in folder.iterdir() if x.is_file() and not x.name.startswith(".")]

    if mode == "duplicates":
        by_size = {}
        for f in files:
            try:
                by_size.setdefault(f.stat().st_size, []).append(f)
            except Exception:
                pass
        groups = []
        for same in by_size.values():
            if len(same) < 2:
                continue
            hashes = {}
            for f in same:
                try:
                    hashes.setdefault(_hash(f), []).append(f)
                except Exception:
                    pass
            groups.extend(v for v in hashes.values() if len(v) > 1)
        if not groups:
            return f"Nenhum arquivo duplicado encontrado em {folder}."
        lines = []
        for i,g in enumerate(groups[:10],1):
            lines.append(f"Grupo {i}: " + " | ".join(x.name for x in g))
        return f"Encontrei {len(groups)} grupo(s) de duplicados. Não apaguei nada.\n" + "\n".join(lines)

    plan = []
    for f in files:
        if mode == "by_date":
            target_name = datetime.fromtimestamp(f.stat().st_mtime).strftime("%Y-%m")
        else:
            target_name = _category(f)
        plan.append((f, folder/target_name/f.name, target_name))

    if not apply:
        preview = "\n".join(f"- {src.name} → {cat}/" for src,_,cat in plan[:20])
        extra = f"\n... e mais {len(plan)-20}." if len(plan) > 20 else ""
        return (
            f"Prévia para {folder}: {len(plan)} arquivo(s). Nada foi movido ainda.\n"
            + preview + extra +
            "\nSe estiver certo, peça para aplicar a organização."
        )

    moved = 0
    for src,dst,cat in plan:
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst = _unique(dst)
        try:
            shutil.move(str(src), str(dst))
            moved += 1
        except Exception:
            pass

    return f"Organização concluída em {folder}: {moved}/{len(plan)} arquivo(s) movidos."


TOOL = {
    "name": "smart_file_organizer",
    "description": (
        "Organiza uma pasta como Downloads ou Área de Trabalho por tipo ou por mês e também procura "
        "duplicados sem apagá-los. Para mover arquivos, primeiro prefira uma prévia com apply=false; "
        "quando o usuário confirmar ou pedir explicitamente para organizar, use apply=true."
    ),
    "parameters": {
        "type": "OBJECT",
        "properties": {
            "folder": {"type": "STRING"},
            "mode": {"type": "STRING", "description": "by_type, by_date ou duplicates"},
            "apply": {"type": "BOOLEAN"}
        }
    },
    "handler": run,
}
