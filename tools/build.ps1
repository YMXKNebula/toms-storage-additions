param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleTasks=@('build'))
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$sharedProject=Join-Path (Split-Path -Parent $projectRoot) 'toms-persistent-cables'
$oldLocation=Get-Location
$oldJava=$env:JAVA_HOME
$oldGradle=$env:GRADLE_USER_HOME
$oldOptions=$env:JAVA_TOOL_OPTIONS
try {
    Set-Location -LiteralPath $projectRoot
    $dependencies=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'dependencies.json') -Raw | ConvertFrom-Json
    $jdk=Join-Path $PSScriptRoot $dependencies.jdkDirectory
    $sharedJdk=Join-Path $sharedProject ('tools\'+$dependencies.jdkDirectory)
    if(-not(Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe')) -and (Test-Path -LiteralPath (Join-Path $sharedJdk 'bin\java.exe'))){$jdk=$sharedJdk}
    if(-not(Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe'))){
        $archive=Join-Path $PSScriptRoot 'jdk25.zip'
        if(-not(Test-Path -LiteralPath $archive)){Invoke-WebRequest -Uri $dependencies.jdkUrl -OutFile $archive}
        if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $dependencies.jdkSha256){throw 'JDK checksum mismatch'}
        Expand-Archive -LiteralPath $archive -DestinationPath $PSScriptRoot -Force
    }
    New-Item -ItemType Directory -Path vendor -Force | Out-Null
    $tom=Join-Path $projectRoot ('vendor\'+$dependencies.tomJar)
    if(-not(Test-Path -LiteralPath $tom)){Invoke-WebRequest -Uri $dependencies.tomUrl -OutFile $tom}
    if((Get-FileHash -LiteralPath $tom -Algorithm SHA512).Hash -ne $dependencies.tomSha512){throw 'Tom dependency checksum mismatch'}
    $cloth=Join-Path $projectRoot 'vendor\cloth-config-fabric-26.3.159.jar'
    if(-not(Test-Path -LiteralPath $cloth)){Invoke-WebRequest -Uri 'https://maven.shedaniel.me/me/shedaniel/cloth/cloth-config-fabric/26.3.159/cloth-config-fabric-26.3.159.jar' -OutFile $cloth}
    if((Get-FileHash -LiteralPath $cloth -Algorithm SHA256).Hash -ne '1716b9964f90d2b885f9bd3887f8824b71b55c32a4123a5853486e722f7c2e4b'){throw 'Cloth compile API checksum mismatch'}
    $env:JAVA_HOME=$jdk
    $sharedCache=Join-Path $sharedProject '.gradle-home'
    $env:GRADLE_USER_HOME=if(Test-Path -LiteralPath $sharedCache){$sharedCache}else{Join-Path $projectRoot '.gradle-home'}
    $env:JAVA_TOOL_OPTIONS=($oldOptions+' -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE').Trim()
    & (Join-Path $projectRoot 'gradlew.bat') --console=plain @GradleTasks
    if($LASTEXITCODE -ne 0){throw "Gradle exited with code $LASTEXITCODE"}
} finally {
    Set-Location -LiteralPath $oldLocation.Path
    $env:JAVA_HOME=$oldJava
    $env:GRADLE_USER_HOME=$oldGradle
    $env:JAVA_TOOL_OPTIONS=$oldOptions
}
