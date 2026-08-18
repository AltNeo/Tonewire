[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $PhoneSerial,
    [Parameter(Mandatory)][string] $TvSerial,
    [string] $PhoneApk = (Join-Path $PSScriptRoot "..\phone\build\outputs\apk\debug\phone-debug.apk"),
    [string] $TvApk = (Join-Path $PSScriptRoot "..\tv\build\outputs\apk\debug\tv-debug.apk")
)
Import-Module (Join-Path $PSScriptRoot "Tonewire.Tools.psm1") -Force
Assert-TonewireDevice $PhoneSerial "emulator" | Out-Null
Assert-TonewireDevice $TvSerial "physical-or-network" | Out-Null
foreach ($apk in @($PhoneApk, $TvApk)) { if (-not (Test-Path $apk)) { throw "APK not found: $apk. Build it from android/ first." } }
Invoke-TonewireAdb $PhoneSerial install -r $PhoneApk
Invoke-TonewireAdb $TvSerial install -r $TvApk
Get-TonewireTargetIdentity $PhoneSerial
Get-TonewireTargetIdentity $TvSerial
