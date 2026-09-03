param(
    [Parameter(Mandatory)]
    [string]$RepositoryRoot,
    [Parameter(Mandatory)]
    [string[]]$ForbiddenToken
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

foreach ($token in $ForbiddenToken) {
    $hits = git -c "safe.directory=$RepositoryRoot" -C $RepositoryRoot grep -n -i -- $token
    if ($LASTEXITCODE -eq 0) {
        throw "Forbidden identifier found: $token"
    }
    if ($LASTEXITCODE -ne 1) {
        throw "Unable to scan for forbidden identifier: $token"
    }
}

$global:LASTEXITCODE = 0
Write-Host 'Forbidden-identifier scan passed.'
