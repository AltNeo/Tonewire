[CmdletBinding()]
param([string] $AvdName = "Medium_Phone_API_36.1", [int] $TimeoutSeconds = 180)

Import-Module (Join-Path $PSScriptRoot "Tonewire.Tools.psm1") -Force
$existing = @(Get-TonewireDevices | Where-Object { $_.Kind -eq "emulator" -and $_.State -eq "device" })
if ($existing.Count -gt 1) { throw "Multiple running emulators: $($existing.Serial -join ', '). Pass a single running emulator or stop extras manually." }
if ($existing.Count -eq 1) { Get-TonewireTargetIdentity $existing[0].Serial; exit 0 }

$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"
if (-not (Test-Path $emulator)) { throw "Android emulator was not found at $emulator. Set ANDROID_SDK_ROOT." }
$available = & $emulator -list-avds
if ($available -notcontains $AvdName) { throw "AVD '$AvdName' does not exist. Available: $($available -join ', ')" }
# Never use -wipe-data; preserve the named development AVD and its network configuration.
Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName, "-no-snapshot-save") -WindowStyle Hidden | Out-Null
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds 2
    $ready = @(Get-TonewireDevices | Where-Object { $_.Kind -eq "emulator" -and $_.State -eq "device" })
} until ($ready -or (Get-Date) -gt $deadline)
if (-not $ready) { throw "Timed out waiting for $AvdName." }
if ($ready.Count -ne 1) { throw "Expected one emulator after start; found $($ready.Serial -join ', ')." }
$serial = $ready[0].Serial
do { Start-Sleep -Seconds 2; $boot = Get-TonewireProperty $serial "sys.boot_completed" } until ($boot -eq "1" -or (Get-Date) -gt $deadline)
if ($boot -ne "1") { throw "Emulator '$serial' connected but did not finish booting." }
Get-TonewireTargetIdentity $serial
