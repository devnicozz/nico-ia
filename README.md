# NICO AI — Windows Assistant

NICO is a customized Windows AI assistant build based on **Mark-LV** by FatihMakes.

This repository contains the **distribution/build layer** for NICO. GitHub Actions downloads the pinned Mark-LV source, applies the NICO interface and creator modules, packages the app with PyInstaller, and creates a normal Windows installer with Inno Setup.

## What the installer provides

- Native Windows desktop application
- Animated holographic face with mouth/lip-sync while NICO speaks
- Gemini Live voice conversation
- Faster voice response profile
- Windows/app/file/browser control
- WhatsApp Web messaging
- Image generation
- Website generation from a visual identity visible on screen
- Phone remote dashboard from the upstream project
- Desktop and Start Menu shortcuts
- Per-user first-run setup

## Privacy / API keys

**No Gemini API key is included in the distributed installer.**
Each person who installs NICO must configure their own Gemini API key on first run.

Do not commit `config/api_keys.json` to this repository.

## Build

Open **Actions → Build NICO Windows Installer → Run workflow**.

The workflow produces:
- `NICO-Setup.exe` — installer you can send to another Windows PC
- `NICO-Portable.zip` — portable build

## License notice

NICO's build/customization files in this repository are separate from the upstream Mark-LV source.

The generated application contains/adapts **Mark-LV — JARVIS**, Copyright © 2026 FatihMakes, licensed under **CC BY-NC 4.0**. The generated installer includes the upstream license and attribution.

**Commercial use of the Mark-LV-derived application is not permitted by that license.** If you want to sell NICO commercially, the Mark-LV-derived parts would need to be replaced with code under a license that permits commercial distribution, or you would need separate permission from the copyright holder.
