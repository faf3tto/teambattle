@echo off
title Compilazione TeamBattle
if not exist gradlew.bat (
  echo ==========================================================
  echo  Manca il Gradle wrapper.
  echo  Scarica il Forge MDK 1.20.1 da files.minecraftforge.net,
  echo  estrailo, e copia QUI dentro questi tre elementi:
  echo    - gradlew.bat
  echo    - gradlew
  echo    - la cartella "gradle"
  echo  Poi riavvia questo file.
  echo ==========================================================
  pause
  exit /b 1
)
echo Compilazione in corso (la prima volta scarica le dipendenze, serve internet)...
call gradlew.bat build
echo.
echo Se non ci sono errori qui sopra, la mod e' in build\libs\teambattle-1.4.0.jar
echo Copiala nella cartella mods/ del server Forge 1.20.1.
pause
