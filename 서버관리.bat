@echo off
rem CafeOn servers on/off
start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0server-control.ps1"
