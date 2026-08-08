@echo off
REM Same as build.ps1, for cmd.exe and for double-clicking. See that file for details.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
exit /b %ERRORLEVEL%
