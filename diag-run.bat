@echo off
cd /d "%~dp0"
echo === STARTUP at %DATE% %TIME% === > run.log
java -Djava.awt.headless=true -cp out Courtroom >> run.log 2>&1
echo === EXIT at %TIME% code=%ERRORLEVEL% >> run.log
