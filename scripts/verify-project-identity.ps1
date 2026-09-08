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
$website = Get-MetadataValue -Content $metadata -Key 'website'
$repositoryName = Get-MetadataValue -Content $metadata -Key 'repositoryName'
$rootArtifact = Get-MetadataValue -Content $metadata -Key 'rootArtifact'
$starterArtifact = Get-MetadataValue -Content $metadata -Key 'starterArtifact'

$rootPomPath = Join-Path $RepositoryRoot 'pom.xml'
[xml]$rootPom = Get-Content -LiteralPath $rootPomPath -Raw -Encoding utf8
if ($rootPom.project.groupId -ne $groupId) {
    throw "Root POM groupId '$($rootPom.project.groupId)' does not match '$groupId'."
}
if ($rootPom.project.artifactId -ne $rootArtifact) {
    throw "Root POM artifactId '$($rootPom.project.artifactId)' does not match '$rootArtifact'."
}
if ($rootPom.project.version -ne $version) {
    throw "Root POM version '$($rootPom.project.version)' does not match '$version'."
}
if ($rootPom.project.name -ne $displayName) {
    throw "Root POM name '$($rootPom.project.name)' does not match '$displayName'."
}
if ($rootPom.project.url -ne $website) {
    throw "Root POM website '$($rootPom.project.url)' does not match '$website'."
}
$scmUrl = [string]$rootPom.project.scm.url
if ($scmUrl -ne "https://github.com/$repositoryName") {
    throw "Root POM SCM URL '$scmUrl' does not match repository '$repositoryName'."
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
    if ($modulePom.project.artifactId -ne $moduleName) {
        throw "Module POM artifactId '$($modulePom.project.artifactId)' does not match its directory '$moduleName'."
    }
}

# Muer is the single canonical brand identity: the consumable starter
# artifact must exist under the Muer group and the canonical package root.
$starterPomPath = Join-Path (Join-Path $RepositoryRoot $starterArtifact) 'pom.xml'
if (-not (Test-Path -LiteralPath $starterPomPath)) {
    throw "Missing canonical starter artifact POM: $starterPomPath"
}
[xml]$starterPom = Get-Content -LiteralPath $starterPomPath -Raw -Encoding utf8
if ($starterPom.project.artifactId -ne $starterArtifact) {
    throw "Starter artifactId '$($starterPom.project.artifactId)' does not match '$starterArtifact'."
}

# examples/quickstart is a standalone third-party consumer (package
# com.example.muerquickstart) that intentionally lives outside the cloud.muer
# package. It is not framework source, so it is excluded from the package-root
# scan just like target output is.
$javaFiles = Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -File -Filter '*.java' |
    Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' -and $_.FullName -notmatch '[\\/]examples[\\/]quickstart[\\/]' }
foreach ($javaFile in $javaFiles) {
    $content = Get-Content -LiteralPath $javaFile.FullName -Raw -Encoding utf8
    if ($content -match '(?m)^package\s+' -and $content -notmatch "(?m)^package\s+$([regex]::Escape($basePackage))(?:\.|;)") {
        throw "Java package in '$($javaFile.FullName)' is outside '$basePackage'."
    }
    $packageMatch = [regex]::Match($content, '(?m)^package\s+(?<package>[A-Za-z0-9_.]+);')
    if ($packageMatch.Success) {
        $pathMatch = [regex]::Match($javaFile.FullName, '[\\/]src[\\/](?:main|test)[\\/]java[\\/](?<relative>.+)$')
        if (-not $pathMatch.Success) { throw "Cannot identify Java source root for '$($javaFile.FullName)'." }
        $expectedRelative = Join-Path ($packageMatch.Groups['package'].Value.Replace('.', [IO.Path]::DirectorySeparatorChar)) $javaFile.Name
        if (-not [string]::Equals($pathMatch.Groups['relative'].Value, $expectedRelative, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Java source path does not match package declaration: '$($javaFile.FullName)'."
        }
    }
}

Write-Host "Project identity is consistent for $displayName."
