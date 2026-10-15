$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'D:\develop\Java\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location -LiteralPath $projectRoot
mvn -o -gs .mvn/settings.xml -s .mvn/settings.xml spring-boot:run
