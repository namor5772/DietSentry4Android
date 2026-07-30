@echo off
rem Build DietSentry for Windows (single static exe, no runtime dependencies).
setlocal
call "C:\Program Files\Microsoft Visual Studio\18\Community\Common7\Tools\VsDevCmd.bat" -arch=x64 -no_logo
cd /d "%~dp0"
if not exist build mkdir build

rem --- SQLite amalgamation: compiled once, reused ---
if not exist build\sqlite3.obj (
    echo Compiling sqlite3.c ...
    cl /nologo /c /O2 /MT /DSQLITE_THREADSAFE=1 /DSQLITE_OMIT_LOAD_EXTENSION vendor\sqlite3.c /Fobuild\sqlite3.obj
    if errorlevel 1 exit /b 1
)

rem --- ImGui: compiled once, reused ---
if not exist build\imgui.obj (
    echo Compiling Dear ImGui ...
    cl /nologo /c /std:c++17 /utf-8 /EHsc /O2 /MT /DUNICODE /D_UNICODE /Ivendor ^
       vendor\imgui.cpp vendor\imgui_draw.cpp vendor\imgui_tables.cpp vendor\imgui_widgets.cpp ^
       vendor\imgui_impl_win32.cpp vendor\imgui_impl_dx11.cpp /Fobuild\
    if errorlevel 1 exit /b 1
)

rem --- App icon resource ---
rc /nologo /fo build\app.res app.rc
if errorlevel 1 exit /b 1

echo Compiling DietSentry ...
cl /nologo /std:c++17 /utf-8 /EHsc /O2 /MT /W3 /DUNICODE /D_UNICODE /Ivendor /Isrc ^
   src\*.cpp ^
   build\sqlite3.obj build\imgui.obj build\imgui_draw.obj build\imgui_tables.obj build\imgui_widgets.obj ^
   build\imgui_impl_win32.obj build\imgui_impl_dx11.obj ^
   build\app.res ^
   /Febuild\DietSentry.exe /Fobuild\ ^
   /link user32.lib gdi32.lib shell32.lib ole32.lib oleaut32.lib shlwapi.lib comdlg32.lib ^
         d3d11.lib dxgi.lib d3dcompiler.lib dwmapi.lib winhttp.lib windowscodecs.lib ^
         /SUBSYSTEM:WINDOWS
if errorlevel 1 exit /b 1

rem --- Assets next to the exe ---
if not exist build\assets mkdir build\assets
copy /y assets\*.* build\assets\ >nul

echo.
echo Build OK: %~dp0build\DietSentry.exe
endlocal

