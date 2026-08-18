[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $PhoneSerial,
    [Parameter(Mandatory)][string] $TvSerial,
    [string] $EvidenceRoot = (Join-Path $PSScriptRoot "evidence"),
    [int] $CpuSamples = 3,
    [int] $CpuIntervalSeconds = 2
)
Import-Module (Join-Path $PSScriptRoot "Tonewire.Tools.psm1") -Force
Assert-TonewireDevice $PhoneSerial "emulator" | Out-Null
Assert-TonewireDevice $TvSerial "physical-or-network" | Out-Null
$out = New-TonewireEvidenceDirectory $EvidenceRoot
@{ collected_at = (Get-Date).ToString("o"); phone = Get-TonewireTargetIdentity $PhoneSerial; tv = Get-TonewireTargetIdentity $TvSerial } | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $out "identity.json")
foreach ($target in @(@{ n = "phone"; s = $PhoneSerial; p = "com.tonewire.phone" }, @{ n = "tv"; s = $TvSerial; p = "com.tonewire.tv" })) {
    Invoke-TonewireAdb $target.s shell uiautomator dump /sdcard/tonewire-window.xml | Out-Null
    Invoke-TonewireAdb $target.s pull /sdcard/tonewire-window.xml (Join-Path $out "$($target.n)-window.xml") | Out-Null
    # Use native redirection rather than the PowerShell pipeline so PNG bytes are never decoded as text.
    & (Get-TonewireAdb) -s $target.s exec-out screencap -p > (Join-Path $out "$($target.n).png")
    if ($LASTEXITCODE -ne 0) { throw "Screenshot capture failed for $($target.s)." }
    Invoke-TonewireAdb $target.s shell dumpsys meminfo $target.p | Set-Content (Join-Path $out "$($target.n)-meminfo.txt")
    Invoke-TonewireAdb $target.s shell dumpsys package $target.p | Set-Content (Join-Path $out "$($target.n)-package.txt")
    Invoke-TonewireAdb $target.s shell dumpsys netstats | Set-Content (Join-Path $out "$($target.n)-network.txt")
}
Invoke-TonewireAdb $TvSerial shell dumpsys media.audio_flinger | Set-Content (Join-Path $out "tv-audioflinger.txt")
Invoke-TonewireAdb $TvSerial shell dumpsys audio | Set-Content (Join-Path $out "tv-audio.txt")
for ($i = 1; $i -le $CpuSamples; $i++) { Invoke-TonewireAdb $TvSerial shell top -b -n 1 | Add-Content (Join-Path $out "tv-cpu.txt"); if ($i -lt $CpuSamples) { Start-Sleep $CpuIntervalSeconds } }
Invoke-TonewireAdb $PhoneSerial logcat -d -v threadtime | Set-Content (Join-Path $out "phone-logcat.txt")
Invoke-TonewireAdb $TvSerial logcat -d -v threadtime | Set-Content (Join-Path $out "tv-logcat.txt")
Get-TonewireLatencySummary (Join-Path $out "phone-logcat.txt") | ConvertTo-Json | Set-Content (Join-Path $out "latency.json")
Write-Output "Evidence captured in $out. No UI or audio state was changed."
