[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$PackPath,
    [string]$SshTarget = $env:RESOURCE_PACK_SSH_TARGET,
    [string]$RemotePath = $env:RESOURCE_PACK_REMOTE_PATH,
    [string]$PublicUrl = $env:RESOURCE_PACK_PUBLIC_URL
)

$ErrorActionPreference = 'Stop'

function Assert-LastExitCode {
    param([string]$Action)
    if ($LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE."
    }
}

function Test-Archive {
    param([string]$Path, [string]$Label)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label does not exist: $Path"
    }
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -le 0) {
        throw "$Label is empty: $Path"
    }

    $entries = @(& tar -tf $Path 2>&1)
    Assert-LastExitCode "Reading $Label"
    if ($entries.Count -eq 0) {
        throw "$Label contains no entries."
    }
    if (@($entries | Where-Object { $_ -eq 'pack.mcmeta' }).Count -ne 1) {
        throw "$Label must contain exactly one pack.mcmeta at the archive root."
    }
    if (-not ($entries | Where-Object { $_ -like 'assets/*' })) {
        throw "$Label does not contain an assets directory."
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $packEntry = $archive.Entries | Where-Object { $_.FullName -eq 'pack.mcmeta' }
        if (@($packEntry).Count -ne 1 -or $packEntry.Length -le 0) {
            throw "$Label has an empty or invalid root pack.mcmeta."
        }
    }
    finally {
        $archive.Dispose()
    }
}

if ($env:PUBLISH_RESOURCE_PACK -ne '1') {
    throw 'Publishing is disabled. Set PUBLISH_RESOURCE_PACK=1 for an intentional deployment.'
}
if ([string]::IsNullOrWhiteSpace($SshTarget)) {
    throw 'RESOURCE_PACK_SSH_TARGET is required (for example, an SSH config alias).'
}
if ([string]::IsNullOrWhiteSpace($RemotePath)) {
    throw 'RESOURCE_PACK_REMOTE_PATH is required.'
}
if ([string]::IsNullOrWhiteSpace($PublicUrl)) {
    throw 'RESOURCE_PACK_PUBLIC_URL is required.'
}
if ($SshTarget -notmatch '^[A-Za-z0-9_.@:-]+$') {
    throw 'RESOURCE_PACK_SSH_TARGET contains unsupported characters.'
}
if ($RemotePath -notmatch '^/[A-Za-z0-9._/-]+$') {
    throw 'RESOURCE_PACK_REMOTE_PATH must be an absolute Linux path without spaces or shell characters.'
}
$parsedPublicUrl = $null
if (-not [Uri]::TryCreate($PublicUrl, [UriKind]::Absolute, [ref]$parsedPublicUrl) -or $parsedPublicUrl.Scheme -ne 'https') {
    throw 'RESOURCE_PACK_PUBLIC_URL must be an absolute HTTPS URL.'
}

$resolvedPack = (Resolve-Path -LiteralPath $PackPath).Path
Test-Archive $resolvedPack 'Built resource pack'
$localFile = Get-Item -LiteralPath $resolvedPack
$localSha1 = (Get-FileHash -LiteralPath $resolvedPack -Algorithm SHA1).Hash.ToLowerInvariant()
$remoteTemporary = "$RemotePath.next"
$remoteRollback = "$RemotePath.rollback"
$allowInteractive = $env:RESOURCE_PACK_ALLOW_INTERACTIVE_AUTH -eq '1'
$sshOptions = @('-o', 'ConnectTimeout=10')
if (-not $allowInteractive) {
    $sshOptions += @('-o', 'BatchMode=yes')
}

& ssh @sshOptions $SshTarget true
if ($LASTEXITCODE -ne 0) {
    throw 'SSH authentication failed. Configure a key, or explicitly set RESOURCE_PACK_ALLOW_INTERACTIVE_AUTH=1 for a runtime prompt.'
}

$downloadPath = Join-Path ([System.IO.Path]::GetTempPath()) ("smpcore-pack-public-{0}.zip" -f ([Guid]::NewGuid().ToString('N')))
$remoteReplaced = $false
$rollbackPresent = $false

try {
    & scp @sshOptions $resolvedPack "${SshTarget}:$remoteTemporary"
    Assert-LastExitCode 'Uploading the temporary resource pack'

    $remoteDeploy = @'
set -euo pipefail
live="$1"
next="$2"
rollback="$3"
expected="$4"
replaced=0
rollback_present=0
restore_on_error() {
  if test "$replaced" -eq 1; then
    if test "$rollback_present" -eq 1 && test -s "$rollback"; then
      cp -p -- "$rollback" "$next"
      chmod 0644 "$next"
      mv -f -- "$next" "$live"
    else
      rm -f -- "$live"
    fi
  fi
}
trap restore_on_error ERR
test -s "$next"
unzip -tq "$next" >/dev/null
test "$(unzip -Z1 "$next" | grep -Fxc 'pack.mcmeta')" -eq 1
actual="$(sha1sum "$next" | awk '{print $1}')"
test "$actual" = "$expected"
if test -f "$live"; then
  rollback_next="${rollback}.next"
  cp -p -- "$live" "$rollback_next"
  chmod 0644 "$rollback_next"
  mv -f -- "$rollback_next" "$rollback"
  rollback_present=1
fi
chmod 0644 "$next"
mv -f -- "$next" "$live"
replaced=1
live_hash="$(sha1sum "$live" | awk '{print $1}')"
test "$live_hash" = "$expected"
trap - ERR
printf 'REMOTE_SHA1=%s\nROLLBACK_PRESENT=%s\n' "$live_hash" "$rollback_present"
'@
    $remoteResult = @($remoteDeploy | & ssh @sshOptions $SshTarget bash -s -- $RemotePath $remoteTemporary $remoteRollback $localSha1)
    Assert-LastExitCode 'Validating and atomically installing the resource pack'
    $remoteReplaced = $true
    $remoteSha1Line = $remoteResult | Where-Object { $_ -like 'REMOTE_SHA1=*' } | Select-Object -Last 1
    $rollbackLine = $remoteResult | Where-Object { $_ -like 'ROLLBACK_PRESENT=*' } | Select-Object -Last 1
    if (-not $remoteSha1Line) {
        throw 'The remote deployment did not return a SHA-1 result.'
    }
    $remoteSha1 = $remoteSha1Line.Substring('REMOTE_SHA1='.Length).Trim().ToLowerInvariant()
    if ($remoteSha1 -ne $localSha1) {
        throw "Remote SHA-1 mismatch: expected $localSha1 but received $remoteSha1."
    }
    $rollbackPresent = $rollbackLine -eq 'ROLLBACK_PRESENT=1'
    $separator = if ($PublicUrl.Contains('?')) { '&' } else { '?' }
    $cacheBustedUrl = "${PublicUrl}${separator}v=$localSha1"
    $publicSha1 = $null
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        Remove-Item -LiteralPath $downloadPath -Force -ErrorAction SilentlyContinue
        try {
            Invoke-WebRequest -Uri $cacheBustedUrl -OutFile $downloadPath -UseBasicParsing
            if ((Test-Path -LiteralPath $downloadPath) -and (Get-Item -LiteralPath $downloadPath).Length -gt 0) {
                $publicSha1 = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA1).Hash.ToLowerInvariant()
                if ($publicSha1 -eq $localSha1) {
                    Test-Archive $downloadPath 'Publicly downloaded resource pack'
                    break
                }
            }
        }
        catch {
            if ($attempt -eq 5) {
                throw
            }
        }
        if ($attempt -lt 5) {
            Start-Sleep -Seconds 2
        }
    }
    if ($publicSha1 -ne $localSha1) {
        throw "Public SHA-1 mismatch: expected $localSha1 but received $publicSha1."
    }

    Write-Output "PACK_PATH=$resolvedPack"
    Write-Output "PACK_SIZE=$($localFile.Length)"
    Write-Output "LOCAL_SHA1=$localSha1"
    Write-Output "REMOTE_SHA1=$remoteSha1"
    Write-Output "PUBLIC_SHA1=$publicSha1"
    Write-Output "PUBLIC_URL=$cacheBustedUrl"
    Write-Output "ROLLBACK_PRESENT=$([int]$rollbackPresent)"
}
catch {
    if ($remoteReplaced) {
        $remoteRestore = @'
set -euo pipefail
live="$1"
next="$2"
rollback="$3"
if test -s "$rollback"; then
  cp -p -- "$rollback" "$next"
  chmod 0644 "$next"
  mv -f -- "$next" "$live"
else
  rm -f -- "$live"
fi
'@
        $remoteRestore | & ssh @sshOptions $SshTarget bash -s -- $RemotePath $remoteTemporary $remoteRollback
        if ($LASTEXITCODE -ne 0) {
            Write-Error 'Public verification failed and the previous hosted pack could not be restored automatically.'
        }
    }
    throw
}
finally {
    Remove-Item -LiteralPath $downloadPath -Force -ErrorAction SilentlyContinue
    & ssh @sshOptions $SshTarget rm -f -- $remoteTemporary 2>$null
}
