@echo off
setlocal
title NICO PRO - Atualizar PC
set "ROOT=%USERPROFILE%\NICO-AI\Mark-LV"
if not exist "%ROOT%\main.py" (
  echo NICO nao encontrado em %ROOT%
  pause
  exit /b 1
)
py -3.13 "%~dp0patch_nico.py" "%ROOT%"
echo.
echo Atualizacao aplicada. Abra o NICO novamente.
pause
