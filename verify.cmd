@echo off
rem Windows (cmd, PowerShell) : .\verify lance ce fichier, comme .\mvnw lance mvnw.cmd. Sans lui, Windows
rem ne sait pas ouvrir le script bash ./verify, qui n'a pas d'extension, et demande avec quelle application.
rem On passe par le bash de Git for Windows, pas celui de WSL (C:\Windows\System32\bash.exe), qui ne voit
rem ni le Maven ni le JDK de Windows.
setlocal
set "GITBASH="
for /f "delims=" %%g in ('where git 2^>nul') do (
  if not defined GITBASH if exist "%%~dpg..\bin\bash.exe" set "GITBASH=%%~dpg..\bin\bash.exe"
)
if not defined GITBASH if exist "%ProgramFiles%\Git\bin\bash.exe" set "GITBASH=%ProgramFiles%\Git\bin\bash.exe"
if not defined GITBASH (
  echo verify: Git Bash introuvable. Installez Git for Windows, ou lancez ./verify depuis Git Bash. 1>&2
  exit /b 2
)
pushd "%~dp0"
"%GITBASH%" ./verify %*
set "rc=%ERRORLEVEL%"
popd
exit /b %rc%
