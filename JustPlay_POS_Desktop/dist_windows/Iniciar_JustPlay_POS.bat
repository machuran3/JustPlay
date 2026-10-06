@echo off
title JustPlay POS Scanner
echo Iniciando JustPlay POS Scanner...
java -jar JustPlayPOS.jar
if %errorlevel% neq 0 (
    echo.
    echo =======================================================
    echo Error al iniciar. Asegurate de tener Java instalado:
    echo Descarga Java 21 o superior desde: https://adoptium.net
    echo =======================================================
    pause
)
