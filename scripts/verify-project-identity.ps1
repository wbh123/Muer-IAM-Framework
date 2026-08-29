param(
    [Parameter(Mandatory)]
    [string]$RepositoryRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-MetadataValue {
    param(
        [Parameter(Mandatory)][string]$Content,
        [Parameter(Mandatory)][string]$Key
    )

    $match = [regex]::Match($Content, "(?m)^\s*$([regex]::Escape($Key)):\s*(?<value>[^#\r\n]+)\s*$")
    if (-not $match.Success) {
        throw "Missing metadata key: $Key"
    }
    return $match.Groups['value'].Value.Trim().Trim('"').Trim("'")
}

$metadataPath = Join-Path $RepositoryRoot 'metadata/project-metadata.yaml'
$metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding utf8
$groupId = Get-MetadataValue -Content $metadata -Key 'groupId'
$version = Get-MetadataValue -Content $metadata -Key 'version'
$displayName = Get-MetadataValue -Content $metadata -Key 'displayName'
$basePackage = Get-MetadataValue -Content $metadata -Key 'basePackage'

$rootPomPath = Join-Path $RepositoryRoot 'pom.xml'
[xml]$rootPom = Get-Content -LiteralPath $rootPomPath -Raw -Encoding utf8
if ($rootPom.project.groupId -ne $groupId) {
    throw "Root POM groupId '$($rootPom.project.groupId)' does not match '$groupId'."
}
if ($rootPom.project.version -ne $version) {
    throw "Root POM version '$($rootPom.project.version)' does not match '$version'."
}
if ($rootPom.project.name -ne $displayName) {
    throw "Root POM name '$($rootPom.project.name)' does not match '$displayName'."
}

$moduleNames = @($rootPom.project.modules.module)
foreach ($moduleName in $moduleNames) {
    $modulePomPath = Join-Path (Join-Path $RepositoryRoot $moduleName) 'pom.xml'
    [xml]$modulePom = Get-Content -LiteralPath $modulePomPath -Raw -Encoding utf8
    if ($modulePom.project.parent.groupId -ne $groupId) {
        throw "Module '$moduleName' parent groupId does not match '$groupId'."
    }
    if ($modulePom.project.parent.version -ne $version) {
        throw "Module '$moduleName' parent version does not match '$version'."
    }
}

$javaFiles = Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -File -Filter '*.java' |
    Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' }
foreach ($javaFile in $javaFiles) {
    $content = Get-Content -LiteralPath $javaFile.FullName -Raw -Encoding utf8
    if ($content -match '(?m)^package\s+' -and $content -notmatch "(?m)^package\s+$([regex]::Escape($basePackage))(?:\.|;)") {
        throw "Java package in '$($javaFile.FullName)' is outside '$basePackage'."
    }
}

Write-Host "Project identity is consistent for $displayName."
