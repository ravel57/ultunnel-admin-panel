$path = Get-Location

Set-Location ../../WebstormProjects/ultunnel-front

npm install
npm run build

if (Test-Path "./dist/") {
    Copy-Item "./dist/*" -Destination "$path/src/main/resources/static" -Recurse -Force
} else {
    Write-Error "Coping error"
    exit 1
}

Set-Location $path
