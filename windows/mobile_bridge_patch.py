from __future__ import annotations

import json
import secrets
import shutil
from datetime import datetime
from pathlib import Path

ROOT = Path.home() / "NICO-AI" / "Mark-LV"
SERVER = ROOT / "dashboard" / "server.py"
CFG = ROOT / "config" / "nico_mobile_bridge.json"

if not SERVER.exists():
    raise SystemExit(f"NICO dashboard nao encontrado em: {SERVER}")

# Stable bridge code: generated once, reused forever unless user deletes the file.
CFG.parent.mkdir(parents=True, exist_ok=True)
if CFG.exists():
    try:
        data = json.loads(CFG.read_text(encoding="utf-8"))
    except Exception:
        data = {}
else:
    data = {}

code = str(data.get("code") or "").strip().upper()
if len(code) < 6:
    alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    code = "".join(secrets.choice(alphabet) for _ in range(8))
    CFG.write_text(json.dumps({"code": code, "port": 8765}, indent=2), encoding="utf-8")

stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
backup = SERVER.with_name(f"server_before_mobile_bridge_{stamp}.py")
shutil.copy2(SERVER, backup)

s = SERVER.read_text(encoding="utf-8")

if "MOBILE_PORT" not in s:
    s = s.replace("PORT        = 8000", "PORT        = 8000\nMOBILE_PORT = 8765", 1)

method_marker = "    async def _serve_alias(self) -> None:"
if "async def _serve_mobile_bridge(self)" not in s:
    method = r'''
    async def _serve_mobile_bridge(self) -> None:
        """Small plain-HTTP LAN bridge used only by the native NICO Mobile app.

        This is intentionally separate from the browser dashboard so Android
        never has to deal with the dashboard's self-signed HTTPS certificate.
        Only health, command and explicit power endpoints are exposed here.
        """
        import json as _json
        import platform as _platform
        import subprocess as _subprocess

        cfg_path = BASE_DIR / "config" / "nico_mobile_bridge.json"
        try:
            cfg_data = _json.loads(cfg_path.read_text(encoding="utf-8"))
            bridge_code = str(cfg_data.get("code", "")).strip().upper()
        except Exception:
            bridge_code = ""

        if not bridge_code:
            print("[NICO Mobile] bridge code missing — run the mobile bridge installer again.")
            return

        asyncio.get_event_loop().run_in_executor(None, _ensure_network_access, MOBILE_PORT)

        bridge = FastAPI()

        def _allowed(value: str) -> bool:
            try:
                return secrets.compare_digest(str(value or "").strip().upper(), bridge_code)
            except Exception:
                return False

        @bridge.get("/nico/health")
        async def nico_mobile_health():
            return JSONResponse({
                "ok": True,
                "name": "NICO",
                "bridge": "1.0",
                "pc_ip": self._ip,
            })

        @bridge.post("/nico/command")
        async def nico_mobile_command(req: Request):
            try:
                body = await req.json()
            except Exception:
                return JSONResponse({"ok": False, "error": "invalid_json"}, status_code=400)

            if not _allowed(body.get("code", "")):
                return JSONResponse({"ok": False, "error": "invalid_code"}, status_code=401)

            text = str(body.get("text", "")).strip()
            if not text:
                return JSONResponse({"ok": False, "error": "empty_command"}, status_code=400)

            await self._command_queue.put(text)
            if self._wake_callback:
                try:
                    self._wake_callback()
                except Exception:
                    pass

            return JSONResponse({"ok": True, "queued": True})

        @bridge.post("/nico/power")
        async def nico_mobile_power(req: Request):
            try:
                body = await req.json()
            except Exception:
                return JSONResponse({"ok": False, "error": "invalid_json"}, status_code=400)

            if not _allowed(body.get("code", "")):
                return JSONResponse({"ok": False, "error": "invalid_code"}, status_code=401)

            action = str(body.get("action", "")).strip().lower()
            confirmed = bool(body.get("confirmed", False))
            if not confirmed or action not in ("shutdown", "restart"):
                return JSONResponse({"ok": False, "error": "confirmation_required"}, status_code=400)

            if _platform.system() != "Windows":
                return JSONResponse({"ok": False, "error": "windows_only"}, status_code=400)

            try:
                flags = {}
                if hasattr(_subprocess, "CREATE_NO_WINDOW"):
                    flags["creationflags"] = _subprocess.CREATE_NO_WINDOW
                if action == "shutdown":
                    _subprocess.Popen(["shutdown", "/s", "/t", "10"], **flags)
                else:
                    _subprocess.Popen(["shutdown", "/r", "/t", "10"], **flags)
                return JSONResponse({"ok": True, "action": action})
            except Exception as exc:
                return JSONResponse({"ok": False, "error": str(exc)}, status_code=500)

        cfg = uvicorn.Config(
            bridge,
            host="0.0.0.0",
            port=MOBILE_PORT,
            log_level="warning",
        )

        print(f"[NICO Mobile] bridge: http://{self._ip}:{MOBILE_PORT}")
        print(f"[NICO Mobile] code: {bridge_code}")
        await uvicorn.Server(cfg).serve()

'''
    if method_marker not in s:
        raise SystemExit("Nao encontrei o ponto de insercao do bridge no dashboard.")
    s = s.replace(method_marker, method + method_marker, 1)

start_marker = "        use_ssl  = self._ssl_enabled()"
if "asyncio.create_task(self._serve_mobile_bridge())" not in s:
    if start_marker not in s:
        raise SystemExit("Nao encontrei o ponto de inicializacao do dashboard.")
    s = s.replace(
        start_marker,
        start_marker + "\n\n        # Native mobile bridge: plain HTTP on LAN, independent of dashboard TLS.\n        asyncio.create_task(self._serve_mobile_bridge())",
        1,
    )

SERVER.write_text(s, encoding="utf-8")

print("")
print("============================================================")
print(" NICO MOBILE BRIDGE INSTALADO")
print("============================================================")
print("")
print("PORTA: 8765")
print("CODIGO DO PC:", code)
print("")
print("Coloque esse CODIGO DO PC nas configuracoes do NICO Mobile.")
print("Backup:", backup)
