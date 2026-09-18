@echo off
REM Bu klasordeki .venv icinde kurulu Python ile ambient_sync.py'yi baslatir.
REM Cift tiklayarak veya "Baslangic" (Startup) klasorune kisayol koyarak
REM Windows acilisinda otomatik calismasini saglayabilirsiniz.
cd /d %~dp0
".venv\Scripts\python.exe" ambient_sync.py --config config.json
pause
