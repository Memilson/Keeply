# setup-test-data.ps1
$TestDir = Join-Path $PSScriptRoot "../testdata"

if (!(Test-Path $TestDir)) {
    New-Item -ItemType Directory -Path $TestDir | Out-Null
}

$a = Join-Path $TestDir "a.txt"
$b = Join-Path $TestDir "b.txt"
$large = Join-Path $TestDir "large_file.bin"

Set-Content -Path $a -Value "Hello Keeply C++ Agent!`nThis is a simple text file to test snapshots."
Set-Content -Path $b -Value ("This is a repetitive line to test compression and chunk deduplication.`n" * 50)

# Create a 5MB binary file to trigger chunk splitting since default chunk size is 4MB
Write-Host "Creating a 5MB test file..." -ForegroundColor Cyan
$Bytes = New-Object Byte[] (5 * 1024 * 1024)
for ($i = 0; $i -lt $Bytes.Length; $i++) {
    $Bytes[$i] = ($i % 256) -bxor 0xAA
}
[System.IO.File]::WriteAllBytes($large, $Bytes)

Write-Host "Test data setup complete in ./testdata" -ForegroundColor Green
