param(
    [Parameter(Mandatory)]
    [string]$RepositoryRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('iam-rename-fixture-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null

try {
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'metadata') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'module/src/main/java/org/example/identity') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'module/src/main/resources/META-INF/spring') -Force | Out-Null

    Set-Content -LiteralPath (Join-Path $fixtureRoot 'metadata/project-metadata.yaml') -Encoding utf8NoBOM -NoNewline -Value @'
project:
  displayName: IAM Spring Boot Starter
  repositoryName: IAM-Spring-Boot-Starter
  description: Reusable identity and access management starter for Spring Boot.
coordinates:
  groupId: io.github.iamstarter
  version: 2.0.0-SNAPSHOT
java:
  basePackage: io.github.iamstarter
'@
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'pom.xml') -Encoding utf8NoBOM -NoNewline -Value @'
<project><modelVersion>4.0.0</modelVersion><groupId>org.example.identity</groupId><artifactId>identity-parent</artifactId><version>1.0.0-SNAPSHOT</version><name>Legacy Identity</name><modules><module>module</module></modules></project>
'@
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'module/pom.xml') -Encoding utf8NoBOM -NoNewline -Value @'
<project><modelVersion>4.0.0</modelVersion><parent><groupId>org.example.identity</groupId><artifactId>identity-parent</artifactId><version>1.0.0-SNAPSHOT</version></parent><artifactId>module</artifactId></project>
'@
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'module/src/main/java/org/example/identity/IdentityMarker.java') -Encoding utf8NoBOM -NoNewline -Value @'
package org.example.identity;
public final class IdentityMarker { }
'@
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'module/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports') -Encoding utf8NoBOM -NoNewline -Value 'org.example.identity.IdentityMarker'

    git -C $fixtureRoot init -b trunk | Out-Null
    git -C $fixtureRoot config user.email 'test@example.invalid'
    git -C $fixtureRoot config user.name 'IAM test'
    git -C $fixtureRoot add .
    git -C $fixtureRoot commit -m 'test fixture' | Out-Null

    & (Join-Path $RepositoryRoot 'scripts/rename-project.ps1') -RepositoryRoot $fixtureRoot -MetadataPath (Join-Path $fixtureRoot 'metadata/project-metadata.yaml')

    [xml]$rootPom = Get-Content -LiteralPath (Join-Path $fixtureRoot 'pom.xml') -Raw -Encoding utf8
    [xml]$modulePom = Get-Content -LiteralPath (Join-Path $fixtureRoot 'module/pom.xml') -Raw -Encoding utf8
    if ($rootPom.project.groupId -ne 'io.github.iamstarter') { throw 'Root groupId was not migrated.' }
    if ($rootPom.project.version -ne '2.0.0-SNAPSHOT') { throw 'Root version was not migrated.' }
    if ($modulePom.project.parent.version -ne '2.0.0-SNAPSHOT') { throw 'Child parent version was not migrated.' }
    if (-not (Test-Path -LiteralPath (Join-Path $fixtureRoot 'module/src/main/java/io/github/iamstarter/IdentityMarker.java'))) { throw 'Java source path was not migrated.' }
    $imports = Get-Content -LiteralPath (Join-Path $fixtureRoot 'module/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports') -Raw -Encoding utf8
    if ($imports -ne 'io.github.iamstarter.IdentityMarker') { throw 'Auto-configuration registration was not migrated.' }
    Write-Host 'Rename-project fixture test passed.'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
