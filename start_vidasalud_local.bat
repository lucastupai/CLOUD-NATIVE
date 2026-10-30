@echo off
setlocal
set "BASE_DIR=%~dp0"

echo ======================================================================
echo             LEVANTANDO PLATAFORMA VIDASALUD (EP3)
echo ======================================================================

echo [1/6] Levantando RabbitMQ mediante Docker...
cd /d "%BASE_DIR%infra\apps"
docker compose -f compose.local.yml up -d rabbitmq
timeout /t 5 /nobreak >nul

echo [2/6] Iniciando ms-vidasalud-catalog en puerto 8082...
start "Catalog (8082)" cmd /k "cd /d %BASE_DIR%ms-vidasalud-catalog && mvn spring-boot:run"
timeout /t 4 /nobreak >nul

echo [3/6] Iniciando ms-vidasalud-appointments en puerto 8081...
start "Appointments (8081)" cmd /k "cd /d %BASE_DIR%ms-vidasalud-appointments && mvn spring-boot:run"
timeout /t 4 /nobreak >nul

echo [4/6] Iniciando ms-vidasalud-notifications en puerto 8083...
start "Notifications (8083)" cmd /k "cd /d %BASE_DIR%ms-vidasalud-notifications && mvn spring-boot:run"
timeout /t 4 /nobreak >nul

echo [5/6] Iniciando ms-vidasalud-rabbit-admin en puerto 8084...
start "Rabbit-Admin (8084)" cmd /k "cd /d %BASE_DIR%ms-vidasalud-rabbit-admin && mvn spring-boot:run"
timeout /t 4 /nobreak >nul

echo [6/6] Iniciando ms-vidasalud-bff en puerto 8080...
start "BFF (8080)" cmd /k "cd /d %BASE_DIR%ms-vidasalud-bff && mvn spring-boot:run"
timeout /t 4 /nobreak >nul

echo Iniciando Frontend Angular en puerto 4200...
start "Frontend Angular (4200)" cmd /k "cd /d %BASE_DIR%frontend-vidasalud && npm start"

echo.
echo Todos los servicios estan levantandose en sus respectivas ventanas.
echo Consola RabbitMQ: http://localhost:15672 (vidasalud / vidasalud-local)
echo Frontend Angular: http://localhost:4200
echo BFF Gateway:      http://localhost:8080
echo.
pause
