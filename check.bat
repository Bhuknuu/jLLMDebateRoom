@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -d out Courtroom.java
if errorlevel 1 (
    echo [FAILED] Compilation error.
    pause
    exit /b 1
)
java -cp out Courtroom --check
pause
