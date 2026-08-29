param(
    [Parameter(Mandatory)]
    [string]$RepositoryRoot,
    [Parameter(Mandatory)]
    [string]$MetadataPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-MetadataValue {
    param([string]$Content, [string]$Key)
    $match = [regex]::Match($Content, "(?m)^\s*$([regex]::Escape($Key)):\s*(?<value>[^#\r\n]+)\s*$")
    if (-not $match.Success) { throw "Missing metadata key: $Key" }
    return $match.Groups['value'].Value.Trim().Trim('"').Trim("'")
}

$dirty = git -c "safe.directory=$RepositoryRoot" -C $RepositoryRoot status --porcelain
if ($dirty) { throw 'Working tree must be clean before rename-project.ps1 runs.' }

$metadata = Get-Content -LiteralPath $MetadataPath -Raw -Encoding utf8
$newGroupId = Get-MetadataValue $metadata 'groupId'
$newVersion = Get-MetadataValue $metadata 'version'
$newDisplayName = Get-MetadataValue $metadata 'displayName'
$newBasePackage = Get-MetadataValue $metadata 'basePackage'
[xml]$rootPom = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'pom.xml') -Raw -Encoding utf8
$oldGroupId = [string]$rootPom.project.groupId
$oldBasePackage = $oldGroupId
$oldVersion = [string]$rootPom.project.version
$oldDisplayName = [string]$rootPom.project.name

$trackedFiles = git -c "safe.directory=$RepositoryRoot" -C $RepositoryRoot ls-files -- '*.java' '*.xml' '*.yaml' '*.yml' '*.md' '*.txt' '*.imports'
foreach ($relativePath in $trackedFiles) {
    $path = Join-Path $RepositoryRoot $relativePath
    $content = Get-Content -LiteralPath $path -Raw -Encoding utf8
    $updated = $content.Replace($oldBasePackage, $newBasePackage).Replace($oldGroupId, $newGroupId).Replace($oldVersion, $newVersion).Replace($oldDisplayName, $newDisplayName)
    if ($updated -ne $content) {
        Set-Content -LiteralPath $path -Value $updated -Encoding utf8NoBOM -NoNewline
    }
}

$oldPath = $oldBasePackage.Replace('.', [IO.Path]::DirectorySeparatorChar)
$newPath = $newBasePackage.Replace('.', [IO.Path]::DirectorySeparatorChar)
$packageDirectories = Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -Directory |
    Where-Object { $_.FullName.EndsWith($oldPath) }
foreach ($packageDirectory in $packageDirectories) {
    $sourceRoot = $packageDirectory.FullName
    foreach ($segment in $oldBasePackage.Split('.')) {
        $sourceRoot = Split-Path -Path $sourceRoot -Parent
    }
    $newDirectory = Join-Path $sourceRoot $newPath
    $newParent = Split-Path -Path $newDirectory -Parent
    New-Item -ItemType Directory -Path $newParent -Force | Out-Null
    Move-Item -LiteralPath $packageDirectory.FullName -Destination $newDirectory
    $oldParent = Split-Path -Path $packageDirectory.FullName -Parent
    while ((Test-Path -LiteralPath $oldParent) -and -not (Get-ChildItem -LiteralPath $oldParent -Force | Select-Object -First 1)) {
        Remove-Item -LiteralPath $oldParent
        $oldParent = Split-Path -Path $oldParent -Parent
    }
}

Write-Host "Renamed project identity to $newDisplayName."
