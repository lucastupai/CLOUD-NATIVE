@echo off
setlocal
set "BASE_DIR=%~dp0"

echo ======================================================================
echo          COMPILANDO TODOS LOS MICROSERVICIOS DE VIDASALUD (EP3)
echo ======================================================================

echo.
echo [1/5] Compilando ms-vidasalud-catalog...
cd /d "%BASE_DIR%ms-vidasalud-catalog"
call mvn clean compile -DskipTests
if %ERRORLEVEL% NEQ 0 ( echo Error en catalog & exit /b %ERRORLEVEL% )

echo.
echo [2/5] Compilando ms-vidasalud-appointments...
cd /d "%BASE_DIR%ms-vidasalud-appointments"
call mvn clean compile -DskipTests
if %ERRORLEVEL% NEQ 0 ( echo Error en appointments & exit /b %ERRORLEVEL% )

echo.
echo [3/5] Compilando ms-vidasalud-bff...
cd /d "%BASE_DIR%ms-vidasalud-bff"
call mvn clean compile -DskipTests
if %ERRORLEVEL% NEQ 0 ( echo Error en bff & exit /b %ERRORLEVEL% )

echo.
echo [4/5] Compilando ms-vidasalud-notifications (RabbitMQ Consumers)...
cd /d "%BASE_DIR%ms-vidasalud-notifications"
call mvn clean compile -DskipTests
if %ERRORLEVEL% NEQ 0 ( echo Error en notifications & exit /b %ERRORLEVEL% )

echo.
echo [5/5] Compilando ms-vidasalud-rabbit-admin...
cd /d "%BASE_DIR%ms-vidasalud-rabbit-admin"
call mvn clean compile -DskipTests
if %ERRORLEVEL% NEQ 0 ( echo Error en rabbit-admin & exit /b %ERRORLEVEL% )

echo.
echo ======================================================================
echo   TODOS LOS MICROSERVICIOS FUERON COMPILADOS CON EXITO!
echo ======================================================================
cd /d "%BASE_DIR%"
