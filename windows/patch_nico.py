from __future__ import annotations
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else (Path.home() / "NICO-AI" / "Mark-LV")
HERE = Path(__file__).resolve().parent

def replace(path: Path, old: str, new: str) -> bool:
    if not path.exists():
        return False
    s = path.read_text(encoding="utf-8")
    if old not in s:
        return False
    path.write_text(s.replace(old, new), encoding="utf-8")
    return True

if not (ROOT / "main.py").exists():
    raise SystemExit(f"NICO/Mark-LV nao encontrado em {ROOT}")

actions = ROOT / "actions"
actions.mkdir(exist_ok=True)
for name in ("generate_image.py", "create_website.py", "wallpaper_control.py", "whatsapp_web.py", "screen_agent.py", "window_control.py", "smart_file_organizer.py", "clipboard_assistant.py"):
    src = HERE / "actions" / name
    if src.exists():
        shutil.copy2(src, actions / name)

# --- Fast/reliable voice defaults and local config ---------------------------
cfgp = ROOT / "config" / "api_keys.json"
if cfgp.exists():
    try:
        cfg = json.loads(cfgp.read_text(encoding="utf-8"))
    except Exception:
        cfg = {}
    cfg["assistant_name"] = "NICO"
    cfg["hud_style"] = "face"
    cfg["thinking_enabled"] = False
    cfg["proactive_audio"] = False
    cfg["morning_brief_enabled"] = False
    cfg["media_resolution"] = "medium"
    cfg["turn_tuning"] = {
        "enabled": True,
        "silence_ms": 420,
        "prefix_ms": 140,
        "end_sensitivity": "high",
        "start_sensitivity": "default",
    }
    cfgp.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")

cm = ROOT / "memory" / "config_manager.py"
replace(cm, 'load_api_keys().get("proactive_audio", True)', 'load_api_keys().get("proactive_audio", False)')
replace(cm, 'bool(cfg.get("enabled", False))', 'bool(cfg.get("enabled", True))')
replace(cm, '_int("silence_ms", 550, 200, 3000)', '_int("silence_ms", 420, 200, 3000)')
replace(cm, 'load_api_keys().get("morning_brief_enabled", True)', 'load_api_keys().get("morning_brief_enabled", False)')

# --- Prevent a slow tool from freezing the whole conversation ----------------
main = ROOT / "main.py"
if main.exists():
    s = main.read_text(encoding="utf-8")
    old = '''r = await loop.run_in_executor(None, lambda: self._action_registry.run(name, args, _ctx))
                result = r or "Done."'''
    new = '''try:
                    r = await asyncio.wait_for(
                        loop.run_in_executor(None, lambda: self._action_registry.run(name, args, _ctx)),
                        timeout=120.0,
                    )
                except asyncio.TimeoutError:
                    r = "Essa ação demorou demais e foi liberada para não travar o NICO. Tente novamente ou simplifique o pedido."
                result = r or "Done."'''
    s = s.replace(old, new)

    old2 = '''r = await loop.run_in_executor(
                        None,
                        lambda: self._plugin_registry.run(name, args, player=self.ui, session_memory=None)
                    )
                    result = r or "Done."'''
    new2 = '''try:
                        r = await asyncio.wait_for(
                            loop.run_in_executor(
                                None,
                                lambda: self._plugin_registry.run(name, args, player=self.ui, session_memory=None)
                            ),
                            timeout=90.0,
                        )
                    except asyncio.TimeoutError:
                        r = "O plugin demorou demais e foi interrompido para o NICO continuar respondendo."
                    result = r or "Done."'''
    s = s.replace(old2, new2)
    main.write_text(s, encoding="utf-8")

# --- Premium NICO UI ----------------------------------------------------------
ui = ROOT / "ui.py"
if ui.exists():
    s = ui.read_text(encoding="utf-8")
    swaps = {
        'APP_VERSION  = "MARK LV"': 'APP_VERSION  = "NICO" ',
        '_DEFAULT_W, _DEFAULT_H = 980, 700': '_DEFAULT_W, _DEFAULT_H = 1240, 790',
        '_MIN_W,     _MIN_H     = 820, 580': '_MIN_W,     _MIN_H     = 940, 620',
        '_LEFT_W  = 148': '_LEFT_W  = 176',
        '_RIGHT_W = 340': '_RIGHT_W = 392',
        'BG        = "#00060a"': 'BG        = "#02050a"',
        'PANEL     = "#010d14"': 'PANEL     = "#071019"',
        'PANEL2    = "#010f18"': 'PANEL2    = "#09131d"',
        'BORDER    = "#0d3347"': 'BORDER    = "#18384a"',
        'BORDER_B  = "#1a5c7a"': 'BORDER_B  = "#2a7594"',
        'BORDER_A  = "#0f4060"': 'BORDER_A  = "#205f7a"',
        'PRI       = "#00d4ff"': 'PRI       = "#35d8ff"',
        'PRI_DIM   = "#007a99"': 'PRI_DIM   = "#1689ad"',
        'PRI_GHO   = "#001f2e"': 'PRI_GHO   = "#062735"',
        'ACC       = "#ff6b00"': 'ACC       = "#8c7cff"',
        'ACC2      = "#ffcc00"': 'ACC2      = "#7cecff"',
        'TEXT      = "#8ffcff"': 'TEXT      = "#dcf7ff"',
        'TEXT_DIM  = "#3a8a9a"': 'TEXT_DIM  = "#6b8d9f"',
        'TEXT_MED  = "#5ab8cc"': 'TEXT_MED  = "#9bcede"',
        'WHITE     = "#d8f8ff"': 'WHITE     = "#f5fcff"',
        'DARK      = "#000d14"': 'DARK      = "#03080d"',
        'BAR_BG    = "#011520"': 'BAR_BG    = "#091923"',
        'w.setFixedHeight(54)': 'w.setFixedHeight(64)',
        'lay.addWidget(_badge(APP_VERSION, C.PRI_DIM))': 'lay.addWidget(_badge("NICO // NEURAL DESKTOP", C.PRI_DIM))',
        'else "Personal AI Assistant")': 'else "Windows Intelligence System")',
    }
    for a,b in swaps.items():
        s = s.replace(a,b)
    s = s.replace('"Courier New"', '"Segoe UI"')
    for a,b in [
        ("border-radius: 2px;", "border-radius: 8px;"),
        ("border-radius: 3px;", "border-radius: 10px;"),
        ("border-radius: 4px;", "border-radius: 12px;"),
        ("border-radius: 5px;", "border-radius: 13px;"),
        ("border-radius: 6px;", "border-radius: 14px;"),
    ]:
        s = s.replace(a,b)
    s = s.replace('self.setWindowTitle(f"{_display} — {APP_VERSION}")',
                  'self.setWindowTitle(f"{_display} // Neural Desktop Intelligence")')
    s = s.replace('self.setWindowTitle(f"{display} — {APP_VERSION}")',
                  'self.setWindowTitle(f"{display} // Neural Desktop Intelligence")')

    # NICO HERO V2: premium status rail and localized live states.
    s = s.replace('txt, col = "●  SPEAKING",  qcol(C.ACC)',
                  'txt, col = "●  FALANDO",  qcol(C.ACC)')
    s = s.replace('txt, col = f"{sym}  THINKING",   qcol(C.ACC2)',
                  'txt, col = f"{sym}  PENSANDO",   qcol(C.ACC2)')
    s = s.replace('txt, col = f"{sym}  PROCESSING", qcol(C.ACC2)',
                  'txt, col = f"{sym}  EXECUTANDO", qcol(C.ACC2)')
    s = s.replace('txt, col = f"{sym}  LISTENING",  qcol(C.GREEN)',
                  'txt, col = f"{sym}  OUVINDO",  qcol(C.GREEN)')

    _hero_marker = '''        p.drawPixmap(0, 0, self._grid_cache)

        # ── holographic head'''
    _hero_inject = '''        p.drawPixmap(0, 0, self._grid_cache)

        # NICO HERO V2 — top identity rail + live capability tags.
        p.setBrush(QBrush(qcol(C.PANEL2)))
        p.setPen(QPen(qcol(C.BORDER_A), 1))
        p.drawRoundedRect(QRectF(18, 14, max(40, W - 36), 34), 12, 12)

        p.setFont(QFont("Segoe UI", 9, QFont.Weight.Bold))
        p.setPen(QPen(qcol(C.WHITE), 1))
        p.drawText(QRectF(34, 19, max(40, W * 0.45), 22),
                   Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter,
                   "NICO // NEURAL DESKTOP")

        p.setFont(QFont("Segoe UI", 8, QFont.Weight.Medium))
        p.setPen(QPen(qcol(C.PRI), 1))
        p.drawText(QRectF(W * 0.47, 19, max(40, W * 0.49 - 24), 22),
                   Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter,
                   "VOICE  •  VISION  •  AGENT  •  MOBILE")

        # ── holographic head'''
    if _hero_marker in s and 'NICO HERO V2 — top identity rail' not in s:
        s = s.replace(_hero_marker, _hero_inject)

    _wave_marker = '''        p.end()   # end deterministically so the backing store never flushes an active painter'''
    _wave_inject = '''        # Capability rail below the waveform.
        cap_y = min(H - 28, wy + 30)
        p.setFont(QFont("Segoe UI", 7, QFont.Weight.Medium))
        p.setPen(QPen(qcol(C.TEXT_DIM), 1))
        p.drawText(QRectF(0, cap_y, W, 18), Qt.AlignmentFlag.AlignCenter,
                   "SCREEN AGENT   //   FILES   //   WINDOWS   //   CLIPBOARD   //   MOBILE LINK")

        p.end()   # end deterministically so the backing store never flushes an active painter'''
    if _wave_marker in s and 'Capability rail below the waveform' not in s:
        s = s.replace(_wave_marker, _wave_inject)

    ui.write_text(s, encoding="utf-8")

# --- Brand dashboard and add authenticated mobile power endpoint -------------
srv = ROOT / "dashboard" / "server.py"
if srv.exists():
    s = srv.read_text(encoding="utf-8")
    marker = '''        @app.post("/api/wake")
        async def wake_ep(req: Request):
            if not _auth(req):
                return JSONResponse({"error": "Unauthorized"}, status_code=401)
            if self._wake_callback:
                self._wake_callback()
            return JSONResponse({"ok": True})
'''
    if marker in s and '/api/mobile-power' not in s:
        endpoint = marker + '''
        @app.post("/api/mobile-power")
        async def mobile_power(req: Request):
            """Explicit phone-confirmed power action. Requires a paired/authenticated device."""
            if not _auth(req):
                return JSONResponse({"error": "Unauthorized"}, status_code=401)
            try:
                body = await req.json()
            except Exception:
                return JSONResponse({"error": "Invalid request"}, status_code=400)
            action = str(body.get("action", "")).strip().lower()
            confirm = str(body.get("confirm", "")).strip().upper()
            if confirm != "YES" or action not in ("shutdown", "restart"):
                return JSONResponse({"error": "Confirmation required"}, status_code=400)
            if platform.system() != "Windows":
                return JSONResponse({"error": "Power control is currently Windows-only"}, status_code=400)
            try:
                if action == "shutdown":
                    subprocess.Popen(["shutdown", "/s", "/t", "10"], **_WIN_HIDE)
                else:
                    subprocess.Popen(["shutdown", "/r", "/t", "10"], **_WIN_HIDE)
                return JSONResponse({"ok": True, "action": action})
            except Exception as exc:
                return JSONResponse({"error": str(exc)}, status_code=500)
'''
        s = s.replace(marker, endpoint)


    srv.write_text(s, encoding="utf-8")

for rel in ("dashboard/static/app.html", "dashboard/static/login.html"):
    p = ROOT / rel
    if p.exists():
        s = p.read_text(encoding="utf-8")
        for a,b in {
            "J.A.R.V.I.S":"NICO",
            "JARVIS":"NICO",
            "Jarvis":"NICO",
            "jarvis":"nico",
        }.items():
            s=s.replace(a,b)
        p.write_text(s, encoding="utf-8")

notice = HERE / "THIRD_PARTY_NOTICES.txt"
if notice.exists():
    shutil.copy2(notice, ROOT / "THIRD_PARTY_NOTICES.txt")

print("NICO PRO aplicado em:", ROOT)
print("- interface premium")
print("- resposta rapida e menos travamentos")
print("- geracao de imagens")
print("- criacao de sites pela tela")
print("- wallpaper")
print("- WhatsApp Web")
print("- endpoint autenticado para desligar/reiniciar pelo celular")
print("- screen agent: olha e controla a tela")
print("- controle de janelas: foco/minimiza/maximiza/encaixa")
print("- organizador inteligente de arquivos")
print("- assistente de area de transferencia")
print("- hero NICO V2 com estados em tempo real")
