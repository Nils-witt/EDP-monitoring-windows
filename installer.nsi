; NSIS installer script for Windows client
; Produces an installer that copies the exe, bundled JRE and config example to Program Files
; and creates a desktop shortcut.

!define APP_NAME "windows-client"
!define APP_VERSION "1.0-SNAPSHOT"
!define EXE_NAME "${APP_NAME}-${APP_VERSION}.exe"
!define OUT_INSTALLER "${APP_NAME}-${APP_VERSION}-installer.exe"

OutFile "target\${OUT_INSTALLER}"
InstallDir "$PROGRAMFILES\\${APP_NAME}"
RequestExecutionLevel admin

Var STARTMENU_FOLDER

Section "Install"
  SetOutPath "$INSTDIR"

  ; Copy main exe
  File "target/windows-client-1.0-SNAPSHOT.exe"

  ; Copy JRE dir recursively (if present)
  IfFileExists ".\\target\\jre\\*" 0 +3
    SetOutPath "$INSTDIR\\jre"
    File /r ".\\target\\jre\\*"

  ; Copy example config into install dir
  SetOutPath "$INSTDIR"
  IfFileExists ".\\target\\config.properties.example" 0 +2
    File ".\\target\\config.properties.example"

  ; Create Start Menu folder and a shortcut
  CreateDirectory "$SMPROGRAMS\\${APP_NAME}"
  CreateShortCut "$SMPROGRAMS\\${APP_NAME}\\${APP_NAME}.lnk" "$INSTDIR\\${EXE_NAME}"
  CreateShortCut "$DESKTOP\\${APP_NAME}.lnk" "$INSTDIR\\${EXE_NAME}"

  ; Write uninstall information
  WriteUninstaller "$INSTDIR\\Uninstall.exe"
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\\${APP_NAME}\\${APP_NAME}.lnk"
  RMDir "$SMPROGRAMS\\${APP_NAME}"

  Delete "$DESKTOP\\${APP_NAME}.lnk"

  ; Remove installed files
  Delete "$INSTDIR\\${EXE_NAME}"
  IfFileExists "$INSTDIR\\config.properties.example" 0 +2
    Delete "$INSTDIR\\config.properties.example"

  ; Remove JRE directory recursively
  RMDir /r "$INSTDIR\\jre"

  Delete "$INSTDIR\\Uninstall.exe"
  RMDir "$INSTDIR"
SectionEnd

