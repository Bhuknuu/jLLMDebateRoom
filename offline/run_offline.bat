@echo off
setlocal
cd /d "%~dp0"

if not exist out mkdir out
javac -d out CourtroomCLI.java
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)

java -cp out CourtroomCLI %*
pause
exit /b 0
