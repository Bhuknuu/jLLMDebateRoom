@echo off
setlocal
cd /d "%~dp0"

echo.
echo ============================================================
echo  jLLMDebateRoom
echo  Swing GUI + OpenRouter / Ollama + per-persona web search
echo ============================================================
echo.

REM --- Show API-key guidance ---
if not exist .env (
    echo [NOTE] No .env file found. Creating a template...
    echo OPENROUTER_API_KEY=> .env
    echo    Created .env - open it and paste your OpenRouter key,
    echo    or leave blank to use local Ollama at localhost:11434.
    echo.
) else (
    findstr /b "OPENROUTER_API_KEY=" .env >nul
    if errorlevel 1 (
        echo [NOTE] .env found but OPENROUTER_API_KEY line is missing.
        echo    Add a line:  OPENROUTER_API_KEY=your_key_here
        echo.
    )
)

REM --- Compile ---
if not exist out mkdir out
javac -d out Courtroom.java
if errorlevel 1 (
    echo [FAILED] Check compilation errors above.
    pause
    exit /b 1
)
echo Compiled successfully.
echo.

REM --- Subcommands ---
if /I "%1"=="--check" (
    java -cp out Courtroom --check
    pause
    exit /b 0
)

REM --- Default: launch GUI ---
java -cp out Courtroom
echo.
echo [Courtroom] Process exited.
pause
exit /b 0
