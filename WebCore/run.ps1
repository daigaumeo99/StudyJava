param([ValidateRange(1, 65535)][int]$Port = 8080)

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdkRoot = $null
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/javac.exe")) {
        $jdkRoot = $env:JAVA_HOME
    } else {
        $compiler = Get-Command javac.exe -ErrorAction SilentlyContinue
        if ($compiler) {
            $jdkRoot = Split-Path (Split-Path $compiler.Source)
        } elseif (Test-Path "$env:USERPROFILE/.jdks") {
            $jdkRoot = Get-ChildItem "$env:USERPROFILE/.jdks" -Directory |
                Where-Object { $_.Name -match '(17|18|19|2[0-9])' -and (Test-Path "$($_.FullName)/bin/javac.exe") } |
                Sort-Object Name | Select-Object -First 1 -ExpandProperty FullName
        }
    }
    if (!$jdkRoot) { throw 'JDK 17+ is required. Set JAVA_HOME to your JDK directory.' }
    $classes = Join-Path $PSScriptRoot 'build/manual'
    New-Item -ItemType Directory -Force $classes | Out-Null
    $sources = @(Get-ChildItem 'src/main/java' -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
    & "$jdkRoot/bin/javac.exe" -source 17 -target 17 -encoding UTF-8 -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Copy-Item -Recurse -Force 'src/main/resources/web' "$classes/web"
    & "$jdkRoot/bin/java.exe" -cp $classes org.example.Main $Port
    if ($LASTEXITCODE -ne 0) { throw 'Server exited with an error.' }
} finally {
    Pop-Location
}
