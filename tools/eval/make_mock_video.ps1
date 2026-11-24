param(
  [Parameter(Mandatory=$true)][string]$FrameDir,
  [Parameter(Mandatory=$true)][string]$Output,
  [double]$Framerate = 2
)

$pattern = Join-Path $FrameDir '%06d.jpg'
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
  throw 'ffmpeg not found on PATH'
}

# Frames must be numbered 000001.jpg, 000002.jpg, ... in $FrameDir.
ffmpeg -y -framerate $Framerate -i $pattern -c:v libx265 -tag:v hvc1 -pix_fmt yuv420p -an $Output
if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed with exit code $LASTEXITCODE" }
Write-Host "Wrote $Output"
