from __future__ import annotations
import time
from pathlib import Path

def _norm(s): return " ".join(str(s or "").casefold().strip().split())

def run(parameters: dict, **kwargs) -> str:
    p=parameters or {}; receiver=str(p.get("receiver","")).strip(); message=str(p.get("message_text","")).strip()
    if not receiver or not message: return "Informe contato e mensagem."
    try:
        from playwright.sync_api import sync_playwright
    except Exception:
        return "Playwright não está instalado."
    profile=Path.home()/".nico_profiles"/"whatsapp_web"; profile.mkdir(parents=True,exist_ok=True)
    try:
        with sync_playwright() as pw:
            try:
                ctx=pw.chromium.launch_persistent_context(str(profile),channel="chrome",headless=False,no_viewport=True,args=["--start-maximized"])
            except Exception:
                ctx=pw.chromium.launch_persistent_context(str(profile),headless=False,no_viewport=True,args=["--start-maximized"])
            page=ctx.pages[0] if ctx.pages else ctx.new_page()
            page.goto("https://web.whatsapp.com/",wait_until="domcontentloaded",timeout=60000)
            deadline=time.time()+120
            search=None
            while time.time()<deadline:
                for sel in ['div[contenteditable="true"][aria-label*="Pesquisar" i]','div[contenteditable="true"][aria-label*="Search" i]','div[contenteditable="true"][data-tab="3"]']:
                    loc=page.locator(sel)
                    if loc.count() and loc.first.is_visible(): search=loc.first; break
                if search: break
                time.sleep(1)
            if not search:
                ctx.close(); return "Abra o WhatsApp Web do NICO e leia o QR code; depois tente novamente."
            search.click()
            try: search.press("Control+A"); search.press("Backspace")
            except Exception: pass
            page.keyboard.insert_text(receiver); time.sleep(1.2)
            matches=[]
            spans=page.locator("span[title]")
            for i in range(min(spans.count(),250)):
                sp=spans.nth(i)
                try:
                    if sp.is_visible() and _norm(sp.get_attribute("title"))==_norm(receiver): matches.append(sp)
                except Exception: pass
            if len(matches)!=1:
                ctx.close(); return f"Não consegui confirmar exatamente o contato '{receiver}'. Nada foi enviado."
            matches[0].click(); time.sleep(.7)
            composer=None
            for sel in ['footer [role="textbox"][contenteditable="true"]','div[contenteditable="true"][data-tab="10"]']:
                loc=page.locator(sel)
                if loc.count() and loc.first.is_visible(): composer=loc.first; break
            if not composer:
                ctx.close(); return "Não encontrei a caixa de mensagem. Nada foi enviado."
            composer.click(); page.keyboard.insert_text(message); page.keyboard.press("Enter"); time.sleep(.7)
            ctx.close(); return f"Mensagem enviada para {receiver} no WhatsApp Web."
    except Exception as e:
        return f"WhatsApp Web falhou: {e}"

TOOL={
 "name":"send_whatsapp_web",
 "description":"Envia mensagem pelo WhatsApp Web para o contato exato solicitado pelo usuário.",
 "parameters":{"type":"OBJECT","properties":{"receiver":{"type":"STRING"},"message_text":{"type":"STRING"}},"required":["receiver","message_text"]},
 "handler":run,
}
