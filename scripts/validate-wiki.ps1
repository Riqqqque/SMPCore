[CmdletBinding()]
param(
    [string]$WikiPath
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($WikiPath)) {
    $WikiPath = Join-Path $PSScriptRoot '..\docs\wiki'
}
$root = (Resolve-Path -LiteralPath $WikiPath).Path
$files = @(Get-ChildItem -LiteralPath $root -Filter '*.md' -File | Sort-Object Name)
$failures = [System.Collections.Generic.List[string]]::new()
$pages = @{}

foreach ($file in $files) {
    $pages[$file.BaseName.ToLowerInvariant()] = $file
}

$requiredPages = @(
    'Home',
    'Getting-Started',
    'Progression-Guide',
    'Essence-Economy-And-Rewards',
    'Gear-Upgrades-And-Stations',
    'Familiars-And-Gathering',
    'Inventory-Safety-And-Recovery',
    'NPC-Directory',
    'Admin-Setup-Checklist'
)

foreach ($page in $requiredPages) {
    if (-not $pages.ContainsKey($page.ToLowerInvariant())) {
        $failures.Add("Missing required page: $page.md")
    }
}

$linkedFromSidebar = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$linkPattern = [regex]'\[[^\]]+\]\(([^)]+)\)'

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    if ([string]::IsNullOrWhiteSpace($content)) {
        $failures.Add("Empty page: $($file.Name)")
        continue
    }

    if ($file.BaseName -ne '_Sidebar') {
        $h1Count = ([regex]::Matches($content, '(?m)^# [^#]')).Count
        if ($h1Count -ne 1) {
            $failures.Add("$($file.Name) must contain exactly one H1; found $h1Count")
        }
    }

    if ([regex]::IsMatch($content, '[\u00C2\u00C3\u00E2\uFFFD]')) {
        $failures.Add("Possible mojibake in $($file.Name)")
    }

    foreach ($match in $linkPattern.Matches($content)) {
        $target = $match.Groups[1].Value.Trim()
        if ($target -match '^(?:https?://|mailto:|#)' -or [string]::IsNullOrWhiteSpace($target)) {
            continue
        }

        $pageTarget = ($target -split '#', 2)[0]
        if ($pageTarget.EndsWith('.md', [System.StringComparison]::OrdinalIgnoreCase)) {
            $pageTarget = $pageTarget.Substring(0, $pageTarget.Length - 3)
        }
        $pageTarget = [System.Uri]::UnescapeDataString($pageTarget).TrimStart('.', '/', '\')
        if ($pageTarget.Contains('/') -or $pageTarget.Contains('\')) {
            $pageTarget = [System.IO.Path]::GetFileNameWithoutExtension($pageTarget)
        }

        if (-not $pages.ContainsKey($pageTarget.ToLowerInvariant())) {
            $failures.Add("Broken link in $($file.Name): $target")
        }
        if ($file.BaseName -eq '_Sidebar') {
            [void]$linkedFromSidebar.Add($pageTarget)
        }
    }
}

foreach ($file in $files) {
    if ($file.BaseName -ne '_Sidebar' -and -not $linkedFromSidebar.Contains($file.BaseName)) {
        $failures.Add("Page is missing from _Sidebar.md: $($file.Name)")
    }
}

$staleText = @(
    '30 seconds to accept',
    '65.5% loss',
    '34.5% total hit',
    'Player-placed ores never qualify',
    'Covenant-Armory'
)
$allContent = ($files | ForEach-Object {
    [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
}) -join "`n"

foreach ($stale in $staleText) {
    if ($allContent.IndexOf($stale, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
        $failures.Add("Stale wiki text remains: $stale")
    }
}

if ($failures.Count -gt 0) {
    $failures | Sort-Object -Unique | ForEach-Object { Write-Error $_ }
    throw "Wiki validation failed with $($failures.Count) issue(s)."
}

$linkCount = ($files | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
    $linkPattern.Matches($content).Count
} | Measure-Object -Sum).Sum

Write-Host "Wiki validation passed: $($files.Count) pages, $linkCount links, complete sidebar."
