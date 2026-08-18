[CmdletBinding(SupportsShouldProcess, ConfirmImpact = "High")]
param(
    [Parameter(Mandatory)][string] $PhoneSerial,
    [Parameter(Mandatory)][string] $TvSerial,
    [Parameter(Mandatory)][string] $ControlConfig,
    [securestring] $PairingCode,
    [switch] $AllowAudioMutation,
    [int] $UiTimeoutSeconds = 30
)

Import-Module (Join-Path $PSScriptRoot "Tonewire.Tools.psm1") -Force

function Get-Ui {
    $path = Join-Path $runDirectory "phone-window.xml"
    return Get-TonewireUiDocument -Serial $PhoneSerial -LocalPath $path
}

function Wait-UiNode {
    param([string] $Text, [string] $Description, [string] $ClassName)
    $deadline = (Get-Date).AddSeconds($UiTimeoutSeconds)
    do {
        try { return Find-TonewireUiNode -Document (Get-Ui) -Text $Text -Description $Description -ClassName $ClassName }
        catch { Start-Sleep -Milliseconds 500 }
    } until ((Get-Date) -gt $deadline)
    throw "Timed out waiting for UI node (text='$Text', description='$Description', class='$ClassName')."
}

function Get-PhoneEqSnapshot {
    $document = Get-Ui
    $nodes = @(Select-Xml -Xml $document -XPath "//node" | ForEach-Object { $_.Node })
    $enabled = $nodes | Where-Object { $_.text -in @("EQ ON", "EQ BYPASSED") } | Select-Object -First 1
    if ($null -eq $enabled) { throw "The phone does not expose the EQ enabled state." }
    $bands = @($nodes | Where-Object { $_.'content-desc' -match "^(?<frequency>.+) equalizer level, (?<db>[+-]\d+\.\d) dB$" } | ForEach-Object {
        $description = $_.'content-desc'
        if ($description -notmatch "^(?<frequency>.+) equalizer level, (?<db>[+-]\d+\.\d) dB$") { return }
        [pscustomobject]@{ Frequency = $Matches.frequency; Millibels = [int]([decimal]$Matches.db * 100); Bounds = $_.bounds }
    })
    if ($bands.Count -eq 0) { throw "The phone has no enabled hardware EQ sliders. Pairing/state sync may not have completed." }
    return [pscustomobject]@{ Enabled = $enabled.text -eq "EQ ON"; Bands = $bands }
}

function Set-EqSlider {
    param([Parameter(Mandatory)]$Band, [Parameter(Mandatory)][int] $LevelMillibels)
    if ($LevelMillibels -lt $config.minimumMillibels -or $LevelMillibels -gt $config.maximumMillibels) {
        throw "Requested $LevelMillibels mB is outside configured hardware range."
    }
    $bounds = ConvertFrom-TonewireBounds $Band.Bounds
    $ratio = ($LevelMillibels - $config.minimumMillibels) / [double]($config.maximumMillibels - $config.minimumMillibels)
    $x = [math]::Round($bounds.Left + (($bounds.Right - $bounds.Left) * $ratio))
    $y = [math]::Floor(($bounds.Top + $bounds.Bottom) / 2)
    Invoke-TonewireAdb $PhoneSerial shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 250
}

Assert-TonewireDevice $PhoneSerial "emulator" | Out-Null
Assert-TonewireDevice $TvSerial "physical-or-network" | Out-Null
if (-not (Test-Path $ControlConfig)) { throw "Control config not found: $ControlConfig" }
$config = Get-Content -Raw $ControlConfig | ConvertFrom-Json
if ($config.manualTvAddress -notmatch "^[0-9.]+:\d{1,5}$") { throw "manualTvAddress must be an IPv4 address and TV protocol port, for example 192.0.2.42:49321." }
if ($config.minimumMillibels -ge $config.maximumMillibels) { throw "Invalid configured EQ range." }
if (@($config.testCurveMb).Count -eq 0 -or @($config.testCurveMb | Where-Object { $_ -gt 0 }).Count -gt 0) {
    throw "Only a non-empty cut-only test curve (millibels <= 0) is allowed."
}
if (-not $AllowAudioMutation) { throw "This test can change audible EQ. Re-run with -AllowAudioMutation only while attended, with PCM playback running." }
if (-not $PSCmdlet.ShouldProcess("$PhoneSerial -> $TvSerial", "pair through production LAN protocol and apply a cut-only curve")) { return }

$runDirectory = New-TonewireEvidenceDirectory
$snapshot = $null
try {
    Invoke-TonewireAdb $PhoneSerial shell am start -n "$($config.phonePackage)/$($config.phoneActivity)" | Out-Null
    $connect = Wait-UiNode -Description "Connection status. Tap to find or enter TV address."
    Invoke-TonewireUiTap $PhoneSerial $connect
    $address = Wait-UiNode -ClassName "android.widget.EditText"
    Invoke-TonewireUiTap $PhoneSerial $address
    Invoke-TonewireUiText $PhoneSerial $config.manualTvAddress
    Invoke-TonewireUiTap $PhoneSerial (Wait-UiNode -Text "Connect")
    $codeInput = Wait-UiNode -ClassName "android.widget.EditText"
    if ($null -eq $PairingCode) { $PairingCode = Read-Host "Enter the current six-digit code shown on the TV" -AsSecureString }
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($PairingCode)
    try { $code = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    if ($code -notmatch "^\d{6}$") { throw "Pairing code must be exactly six digits." }
    Invoke-TonewireUiTap $PhoneSerial $codeInput
    Invoke-TonewireUiText $PhoneSerial $code
    Remove-Variable code -ErrorAction SilentlyContinue
    Invoke-TonewireUiTap $PhoneSerial (Wait-UiNode -Text "Pair")
    Wait-UiNode -Text "LIVE" | Out-Null
    $snapshot = Get-PhoneEqSnapshot
    $snapshot | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $runDirectory "original-phone-state.json")
    if ($snapshot.Bands.Count -ne @($config.testCurveMb).Count) { throw "Test curve band count does not match the paired TV state." }
    if (-not $snapshot.Enabled) { Invoke-TonewireUiTap $PhoneSerial (Wait-UiNode -Text "EQ BYPASSED"); Start-Sleep -Milliseconds 500 }
    $liveBands = (Get-PhoneEqSnapshot).Bands
    for ($index = 0; $index -lt $liveBands.Count; $index++) { Set-EqSlider $liveBands[$index] ([int]$config.testCurveMb[$index]) }
    Get-Date -Format o | Set-Content (Join-Path $runDirectory "cut-curve-applied-at.txt")
    Write-Output "Cut-only curve applied. Inspect playback and collect evidence before ending this command; restoration runs in finally."
    Read-Host "Press Enter after attended playback verification to restore the exact original state"
}
finally {
    if ($null -ne $snapshot) {
        try {
            $current = Get-PhoneEqSnapshot
            if (-not $current.Enabled) { Invoke-TonewireUiTap $PhoneSerial (Wait-UiNode -Text "EQ BYPASSED"); Start-Sleep -Milliseconds 300 }
            $bands = (Get-PhoneEqSnapshot).Bands
            for ($index = 0; $index -lt $bands.Count; $index++) { Set-EqSlider $bands[$index] ([int]$snapshot.Bands[$index].Millibels) }
            if (-not $snapshot.Enabled) { Invoke-TonewireUiTap $PhoneSerial (Wait-UiNode -Text "EQ ON") }
            Get-Date -Format o | Set-Content (Join-Path $runDirectory "restore-completed-at.txt")
        } catch {
            Set-Content -LiteralPath (Join-Path $runDirectory "RESTORE_FAILED.txt") -Value $_.Exception.ToString()
            throw "CRITICAL: automatic restore failed. Restore the original values in $runDirectory manually before continuing. $($_.Exception.Message)"
        }
    }
}
Write-Output "Original phone-visible EQ state restored. Evidence directory: $runDirectory"
