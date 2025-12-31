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


; --- INCLUDES ---
!include "nsDialogs.nsh"
!include "LogicLib.nsh"

; --- SERVICE DEFAULTS / VARIABLES ---
!define SERVICE_NAME "edp-monitoring"
!define SERVICE_DISPLAYNAME "EDP Monitoring Service"
Var STARTMENU_FOLDER
Var INSTALL_AS_SERVICE
Var SERVICE_CHK ; checkbox control handle


Function ServicePageCreate
  nsDialogs::Create 1018
  Pop $0
  ${If} $0 == error
    Abort
  ${EndIf}

  ; Checkbox: Install as Windows Service
  ${NSD_CreateCheckbox} 10u 10u 200u 12u "Install as Windows Service"
  Pop $SERVICE_CHK
  ${NSD_SetState} $SERVICE_CHK 1 ; default checked (change to 0 if you prefer unchecked)

  nsDialogs::Show
FunctionEnd

Function ServicePageLeave
  ${NSD_GetState} $SERVICE_CHK $0
  StrCmp $0 1 +2
    StrCpy $INSTALL_AS_SERVICE "0"
    Goto done
  StrCpy $INSTALL_AS_SERVICE "1"
done:
FunctionEnd

Section "Install"
  SetOutPath "$INSTDIR"

  ; Copy main exe
  File "target/windows-client-1.0-SNAPSHOT.exe"

  ; Copy JRE dir recursively (if present)
  SetOutPath "$INSTDIR\\jre"
  File /r "target\\jre\\*"

  ; Copy example config into install dir
  SetOutPath "$INSTDIR"
  File "target\\config.properties.example"

  ; Create Start Menu folder and a shortcut
  CreateDirectory "$SMPROGRAMS\\${APP_NAME}"
  CreateShortCut "$SMPROGRAMS\\${APP_NAME}\\${APP_NAME}.lnk" "$INSTDIR\\${EXE_NAME}"
  CreateShortCut "$DESKTOP\\${APP_NAME}.lnk" "$INSTDIR\\${EXE_NAME}"

  ; Write uninstall information
  WriteUninstaller "$INSTDIR\\Uninstall.exe"

  ; Install and start service if requested
  Call ServiceInstall
SectionEnd

Section "Uninstall"
  ; Stop and remove service (if it was installed)


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


; --- SERVICE HELPER FUNCTIONS ---
Function ServiceInstall
  ; Only install if user opted in
  StrCmp $INSTALL_AS_SERVICE "1" 0 +2
    Return

  ; Build path to executable
  StrCpy $0 "$INSTDIR\\${EXE_NAME}"

  ExecWait 'sc create ${APP_NAME} error= "severe" displayname= "${APP_NAME}" type= "own" start= "auto" binpath= "$0"'
FunctionEnd
