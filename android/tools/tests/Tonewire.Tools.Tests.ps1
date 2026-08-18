$module = Join-Path $PSScriptRoot "..\Tonewire.Tools.psm1"
Import-Module $module -Force
$path = Join-Path $env:TEMP "tonewire-latency-test.log"
@(
    "07-25 12:00:00.000 I/TonewirePhone: TW_SENT revision=0 type=eq.enable elapsed_ms=100",
    "07-25 12:00:00.020 I/TonewirePhone: TW_ACK revision=1 type=eq.enable elapsed_ms=20",
    "07-25 12:00:01.000 I/TonewirePhone: TW_SENT revision=1 type=eq.band.set elapsed_ms=200",
    "07-25 12:00:01.080 I/TonewirePhone: TW_ACK revision=2 type=eq.band.set elapsed_ms=80",
    "07-25 12:00:02.000 I/TonewirePhone: TW_ACK revision=3 type=eq.band.set elapsed_ms=999"
) | Set-Content $path
$summary = Get-TonewireLatencySummary $path
Remove-Item $path
if ($summary.Samples -ne 2 -or $summary.Unmatched -ne 0 -or $summary.P50Ms -ne 20 -or $summary.P95Ms -ne 80) { throw "Latency parser test failed: $($summary | ConvertTo-Json -Compress)" }
Write-Output "Tonewire tooling parser tests passed."
