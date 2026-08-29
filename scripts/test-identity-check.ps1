param(
    [Parameter(Mandatory)]
    [string]$RepositoryRoot
)

$verifier = Join-Path $RepositoryRoot 'scripts/verify-project-identity.ps1'
& $verifier -RepositoryRoot $RepositoryRoot

$javaFiles = Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -File -Filter '*.java' |
    Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' }
foreach ($javaFile in $javaFiles) {
    $content = Get-Content -LiteralPath $javaFile.FullName -Raw -Encoding utf8
    $packageMatch = [regex]::Match($content, '(?m)^package\s+(?<package>[A-Za-z0-9_.]+);')
    if (-not $packageMatch.Success) { continue }
    $pathMatch = [regex]::Match($javaFile.FullName, '[\\/]src[\\/](?:main|test)[\\/]java[\\/](?<relative>.+)$')
    if (-not $pathMatch.Success) { throw "Cannot identify Java source root for $($javaFile.FullName)." }
    $expectedRelative = Join-Path ($packageMatch.Groups['package'].Value.Replace('.', [IO.Path]::DirectorySeparatorChar)) $javaFile.Name
    if (-not [string]::Equals($pathMatch.Groups['relative'].Value, $expectedRelative, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Java source path does not match package declaration: $($javaFile.FullName)."
    }
}
