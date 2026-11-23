@echo off
title Fake MG server
REM Starts the fake MG server. In the app pick region "Test server (PC)", password: test
cd /d "%~dp0"

set PY=
where py >nul 2>nul && set PY=py -3
if not defined PY (
    python -c "import sys" >nul 2>nul && set PY=python
)
if not defined PY (
    if exist "%LOCALAPPDATA%\Programs\Python\Launcher\py.exe" set PY="%LOCALAPPDATA%\Programs\Python\Launcher\py.exe" -3
)
if not defined PY (
    for /d %%D in ("%LOCALAPPDATA%\Programs\Python\Python3*") do if exist "%%D\python.exe" set PY="%%D\python.exe"
)
if not defined PY (
    echo.
    echo  Python was not found.
    echo  Install it from https://www.python.org/downloads/ and tick "Add python.exe to PATH".
    echo.
    pause
    exit /b 1
)

echo Using: %PY%
%PY% -m pip install --quiet --disable-pip-version-check pycryptodome
%PY% mock_saic_server.py %*
echo.
echo Server stopped.
pause
