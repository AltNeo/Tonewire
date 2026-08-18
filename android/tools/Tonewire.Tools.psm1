Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-TonewireAdb {
    $adb = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -eq $adb) { $adb = Get-Command adb -ErrorAction SilentlyContinue }
    if ($null -eq $adb) { throw "adb was not found. Install Android Platform Tools or add adb to PATH." }
    return $adb.Source
}

function Invoke-TonewireAdb {
    param([Parameter(Mandatory)][string] $Serial, [Parameter(ValueFromRemainingArguments)][string[]] $Arguments)
    & (Get-TonewireAdb) -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb command failed for ${Serial}: $($Arguments -join ' ')" }
}

function Get-TonewireDevices {
    $lines = & (Get-TonewireAdb) devices -l
    if ($LASTEXITCODE -ne 0) { throw "adb devices failed." }
    foreach ($line in $lines) {
        if ($line -match "^(?<serial>\S+)\s+(?<state>device|offline|unauthorized)(?<details>.*)$") {
            $serial = $Matches.serial
            $details = $Matches.details.Trim()
            $kind = if ($serial -like "emulator-*") { "emulator" } else { "physical-or-network" }
            [pscustomobject]@{ Serial = $serial; State = $Matches.state; Kind = $kind; Details = $details }
        }
    }
}

function Assert-TonewireDevice {
    param([Parameter(Mandatory)][string] $Serial, [ValidateSet("emulator", "physical-or-network")][string] $ExpectedKind)
    $device = Get-TonewireDevices | Where-Object Serial -eq $Serial
    if ($null -eq $device) { throw "No connected ADB device matches '$Serial'." }
    if ($device.State -ne "device") { throw "ADB device '$Serial' is $($device.State), not ready." }
    if ($ExpectedKind -and $device.Kind -ne $ExpectedKind) {
        throw "Refusing '$Serial': expected $ExpectedKind, identified $($device.Kind)."
    }
    return $device
}

function Get-TonewireProperty {
    param([Parameter(Mandatory)][string] $Serial, [Parameter(Mandatory)][string] $Name)
    return (Invoke-TonewireAdb $Serial shell getprop $Name | Out-String).Trim()
}

function Get-TonewireTargetIdentity {
    param([Parameter(Mandatory)][string] $Serial)
    $device = Assert-TonewireDevice -Serial $Serial
    [pscustomobject]@{
        Serial = $Serial
        Kind = $device.Kind
        Model = Get-TonewireProperty $Serial "ro.product.model"
        Manufacturer = Get-TonewireProperty $Serial "ro.product.manufacturer"
        Sdk = Get-TonewireProperty $Serial "ro.build.version.sdk"
        Product = Get-TonewireProperty $Serial "ro.build.product"
    }
}

function New-TonewireEvidenceDirectory {
    param([string] $Root = (Join-Path $PSScriptRoot "evidence"))
    $path = Join-Path $Root (Get-Date -Format "yyyyMMdd-HHmmss")
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    return $path
}

function Get-TonewireUiDocument {
    param([Parameter(Mandatory)][string] $Serial, [Parameter(Mandatory)][string] $LocalPath)
    Invoke-TonewireAdb $Serial shell uiautomator dump /sdcard/tonewire-window.xml | Out-Null
    Invoke-TonewireAdb $Serial pull /sdcard/tonewire-window.xml $LocalPath | Out-Null
    return [xml](Get-Content -Raw -LiteralPath $LocalPath)
}

function Find-TonewireUiNode {
    param(
        [Parameter(Mandatory)][xml] $Document,
        [string] $Text,
        [string] $Description,
        [string] $ClassName
    )
    $nodes = @(Select-Xml -Xml $Document -XPath "//node" | ForEach-Object { $_.Node })
    $matches = @($nodes | Where-Object {
        (-not $Text -or $_.text -eq $Text) -and
        (-not $Description -or $_.'content-desc' -eq $Description) -and
        (-not $ClassName -or $_.class -eq $ClassName)
    })
    if ($matches.Count -eq 0) { throw "UI node was not found (text='$Text', description='$Description', class='$ClassName')." }
    if ($matches.Count -gt 1) { throw "UI node match is ambiguous (text='$Text', description='$Description', class='$ClassName')." }
    return $matches[0]
}

function ConvertFrom-TonewireBounds {
    param([Parameter(Mandatory)][string] $Bounds)
    if ($Bounds -notmatch "^\[(?<left>-?\d+),(?<top>-?\d+)\]\[(?<right>-?\d+),(?<bottom>-?\d+)\]$") {
        throw "Invalid UI bounds: $Bounds"
    }
    return [pscustomobject]@{ Left = [int]$Matches.left; Top = [int]$Matches.top; Right = [int]$Matches.right; Bottom = [int]$Matches.bottom }
}

function Invoke-TonewireUiTap {
    param([Parameter(Mandatory)][string] $Serial, [Parameter(Mandatory)]$Node)
    $bounds = ConvertFrom-TonewireBounds $Node.bounds
    $x = [math]::Floor(($bounds.Left + $bounds.Right) / 2)
    $y = [math]::Floor(($bounds.Top + $bounds.Bottom) / 2)
    Invoke-TonewireAdb $Serial shell input tap $x $y | Out-Null
}

function Invoke-TonewireUiText {
    param([Parameter(Mandatory)][string] $Serial, [Parameter(Mandatory)][string] $Text)
    if ($Text -notmatch "^[0-9A-Za-z.:_-]+$") { throw "UI text contains unsupported characters for safe adb input." }
    Invoke-TonewireAdb $Serial shell input text $Text | Out-Null
}

function ConvertFrom-TonewireLogTimestamp {
    param([Parameter(Mandatory)][string] $Line)
    if ($Line -match "(?<stamp>\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})") {
        return [datetime]::ParseExact($Matches.stamp, "MM-dd HH:mm:ss.fff", $null)
    }
    return $null
}

function Get-TonewireLatencySummary {
    param([Parameter(Mandatory)][string] $LogPath)
    $pending = @{}
    $samples = [System.Collections.Generic.List[double]]::new()
    foreach ($line in Get-Content -LiteralPath $LogPath) {
        if ($line -match "TW_SENT revision=(?<revision>\d+) type=(?<type>[a-z.]+) elapsed_ms=(?<elapsed>\d+)") {
            $key = "$(1 + [long]$Matches.revision)|$($Matches.type)"
            if ($pending.ContainsKey($key)) { throw "Duplicate unacknowledged command: $key" }
            $pending[$key] = [long]$Matches.elapsed
            continue
        }
        if ($line -match "TW_ACK revision=(?<revision>\d+) type=(?<type>[a-z.]+) elapsed_ms=(?<elapsed>\d+)") {
            $key = "$($Matches.revision)|$($Matches.type)"
            if ($pending.ContainsKey($key)) {
                $samples.Add([double][long]$Matches.elapsed)
                $pending.Remove($key)
            }
        }
    }
    $ordered = @($samples | Sort-Object)
    if ($ordered.Count -eq 0) {
        return [pscustomobject]@{ Metric = "phone_rtt_upper_bound"; Samples = 0; Unmatched = $pending.Count; P50Ms = $null; P95Ms = $null; MaxMs = $null }
    }
    $percentile = { param([double] $p) $ordered[[math]::Ceiling($p * $ordered.Count) - 1] }
    return [pscustomobject]@{ Metric = "phone_rtt_upper_bound"; Samples = $ordered.Count; Unmatched = $pending.Count; P50Ms = & $percentile 0.50; P95Ms = & $percentile 0.95; MaxMs = $ordered[-1] }
}

Export-ModuleMember -Function *-Tonewire*
