from __future__ import annotations

import ctypes
from ctypes import wintypes
import os
import platform

_OS = platform.system()

SW_HIDE=0
SW_SHOWNORMAL=1
SW_SHOWMINIMIZED=2
SW_SHOWMAXIMIZED=3
SW_RESTORE=9
WM_CLOSE=0x0010
SWP_NOZORDER=0x0004
SWP_SHOWWINDOW=0x0040


def _windows():
    if _OS != "Windows":
        return []
    user32 = ctypes.windll.user32
    result = []

    @ctypes.WINFUNCTYPE(ctypes.c_bool, wintypes.HWND, wintypes.LPARAM)
    def cb(hwnd, lparam):
        if not user32.IsWindowVisible(hwnd):
            return True
        length = user32.GetWindowTextLengthW(hwnd)
        if length <= 0:
            return True
        buff = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buff, length + 1)
        title = buff.value.strip()
        if title:
            result.append((hwnd, title))
        return True

    user32.EnumWindows(cb, 0)
    return result


def _find(title: str):
    q = (title or "").casefold().strip()
    wins = _windows()
    exact = [(h,t) for h,t in wins if t.casefold() == q]
    if exact:
        return exact[0]
    partial = [(h,t) for h,t in wins if q in t.casefold()]
    return partial[0] if partial else None


def run(parameters: dict, **kwargs) -> str:
    if _OS != "Windows":
        return "Controle de janelas está disponível no Windows."

    p = parameters or {}
    action = str(p.get("action", "list")).lower().strip()
    title = str(p.get("title", "")).strip()
    user32 = ctypes.windll.user32

    if action == "list":
        wins = _windows()
        if not wins:
            return "Nenhuma janela visível encontrada."
        return "Janelas abertas:\n" + "\n".join(f"- {t}" for _,t in wins[:30])

    found = _find(title)
    if not found:
        return f"Não encontrei uma janela com '{title}'."
    hwnd, real = found

    if action == "focus":
        user32.ShowWindow(hwnd, SW_RESTORE)
        user32.SetForegroundWindow(hwnd)
    elif action == "minimize":
        user32.ShowWindow(hwnd, SW_SHOWMINIMIZED)
    elif action == "maximize":
        user32.ShowWindow(hwnd, SW_SHOWMAXIMIZED)
    elif action == "restore":
        user32.ShowWindow(hwnd, SW_RESTORE)
    elif action == "close":
        user32.PostMessageW(hwnd, WM_CLOSE, 0, 0)
    elif action in ("left", "right"):
        rect = wintypes.RECT()
        user32.SystemParametersInfoW(0x0030, 0, ctypes.byref(rect), 0)  # SPI_GETWORKAREA
        width = rect.right - rect.left
        height = rect.bottom - rect.top
        half = width // 2
        x = rect.left if action == "left" else rect.left + half
        user32.ShowWindow(hwnd, SW_RESTORE)
        user32.SetWindowPos(hwnd, 0, x, rect.top, half, height, SWP_NOZORDER | SWP_SHOWWINDOW)
        user32.SetForegroundWindow(hwnd)
    else:
        return "Ação inválida. Use list, focus, minimize, maximize, restore, close, left ou right."

    return f"Janela '{real}': {action}."


TOOL = {
    "name": "window_control",
    "description": (
        "Controla janelas abertas no Windows: listar, focar, minimizar, maximizar, restaurar, fechar "
        "ou encaixar à esquerda/direita. Use para pedidos como 'coloca o Chrome do lado esquerdo', "
        "'maximiza o VS Code', 'minimiza o Discord' ou 'fecha essa janela'."
    ),
    "parameters": {
        "type": "OBJECT",
        "properties": {
            "action": {"type": "STRING"},
            "title": {"type": "STRING"}
        },
        "required": ["action"]
    },
    "handler": run,
}
