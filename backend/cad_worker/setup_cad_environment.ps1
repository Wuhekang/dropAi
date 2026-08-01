param(
    [string]$Python = "python",
    [string]$VenvPath = "$PSScriptRoot\.venv"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================"
Write-Host " DropAI CAD Worker Environment Setup"
Write-Host "========================================"
Write-Host "CAD worker: $PSScriptRoot"
Write-Host "Venv:       $VenvPath"
Write-Host ""

if (-not (Test-Path $VenvPath)) {
    Write-Host "[1/4] Creating virtual environment..."
    & $Python -m venv $VenvPath
} else {
    Write-Host "[1/4] Virtual environment already exists."
}

$VenvPython = Join-Path $VenvPath "Scripts\python.exe"
if (-not (Test-Path $VenvPython)) {
    throw "Virtual environment python not found: $VenvPython"
}

Write-Host "[2/4] Upgrading pip..."
& $VenvPython -m pip install --upgrade pip

Write-Host "[3/4] Installing CAD worker dependencies..."
& $VenvPython -m pip install -r (Join-Path $PSScriptRoot "requirements.txt")

Write-Host "[4/4] Checking cadquery and pyparsing..."
$check = @'
import json
import sys
result = {"python": sys.executable}
try:
    import pyparsing
    result["pyparsingVersion"] = getattr(pyparsing, "__version__", "")
    result["hasDelimitedList"] = hasattr(pyparsing, "DelimitedList")
    import cadquery
    result["cadqueryVersion"] = getattr(cadquery, "__version__", "")
    result["status"] = "UP" if result["hasDelimitedList"] else "DOWN"
except Exception as exc:
    result["status"] = "DOWN"
    result["error"] = str(exc)
print(json.dumps(result, ensure_ascii=False, indent=2))
'@
& $VenvPython -c $check

Write-Host ""
Write-Host "Set this environment variable for DropAI:"
Write-Host "CAD_WORKER_PYTHON=$VenvPython"
Write-Host ""
Write-Host "PowerShell example:"
Write-Host "[Environment]::SetEnvironmentVariable('CAD_WORKER_PYTHON', '$VenvPython', 'User')"
