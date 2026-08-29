param(
    [Parameter(Mandatory)]
    [string]$RepositoryRoot
)

$verifier = Join-Path $RepositoryRoot 'scripts/verify-project-identity.ps1'
& $verifier -RepositoryRoot $RepositoryRoot
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
