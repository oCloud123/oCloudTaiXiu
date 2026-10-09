param([string[]]$Versions = @(
    '1.21-R0.1-SNAPSHOT', '1.21.1-R0.1-SNAPSHOT', '1.21.3-R0.1-SNAPSHOT',
    '1.21.4-R0.1-SNAPSHOT', '1.21.5-R0.1-SNAPSHOT', '1.21.6-R0.1-SNAPSHOT',
    '1.21.7-R0.1-SNAPSHOT', '1.21.8-R0.1-SNAPSHOT', '1.21.9-R0.1-SNAPSHOT',
    '1.21.10-R0.1-SNAPSHOT', '1.21.11-R0.1-SNAPSHOT',
    '26.1.2.build.74-stable', '26.2.build.129-stable', '26.3.build.156-beta'
))

$ErrorActionPreference = 'Stop'
function Invoke-GradleCheck([string[]]$GradleArguments, [string]$LogFile) {
    $ErrorActionPreference = 'Continue'
    & ./gradlew.bat @GradleArguments *> $LogFile
    return $LASTEXITCODE
}
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    $verificationDirectory = Join-Path $projectDirectory 'build/verification'
    New-Item -ItemType Directory -Force -Path $verificationDirectory | Out-Null
    $baselineTasks = @('sharePlugin', '--console=plain')
    $exitCode = Invoke-GradleCheck $baselineTasks (Join-Path $verificationDirectory 'baseline.log')
    if ($exitCode -ne 0) { throw 'Baseline tests failed; see build/verification/baseline.log' }
    Copy-Item -LiteralPath 'build/test-results/test' -Destination (Join-Path $verificationDirectory 'baseline-results') -Recurse -Force
    $results = @()
    foreach ($version in $Versions) {
        Write-Output "Checking compiled plugin against Paper API $version"
        $logFile = Join-Path $verificationDirectory "$version.log"
        $exitCode = Invoke-GradleCheck @('test', "-PruntimePaperApiVersion=$version", '--tests', '*ApiCompatibilityTest', '--console=plain') $logFile
        $passed = $exitCode -eq 0
        $results += [pscustomobject]@{ Version = $version; Passed = $passed }
        Write-Output "$version : $passed"
    }
    $results | Export-Csv -LiteralPath (Join-Path $verificationDirectory 'compatibility.csv') -NoTypeInformation -Encoding UTF8
    $exitCode = Invoke-GradleCheck $baselineTasks (Join-Path $verificationDirectory 'final-build.log')
    if ($exitCode -ne 0) { throw 'Final build failed' }
    if ($results.Where({ -not $_.Passed }).Count -gt 0) { throw 'One or more API checks failed; see build/verification/compatibility.csv' }
    Write-Output 'All API checks and baseline tests passed.'
} finally { Pop-Location }
