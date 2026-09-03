param(
    [Parameter(Mandatory = $true)]
    [string]$SourceTemplatePath,

    [string]$OutputDirectory = (
        [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot `
            '..\..\backend\src\main\resources\ppt\templates\v1\small-bear-watercolor-blue-v1'))
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ExpectedSourceSha256 = '9ef26c051c77b97de54a762b1b0528c2956d2bbeaa771b72e0c2f0b99c8d6d37'
$ExpectedWatercolorSha256 = 'dffbde6ec4b28854ce960ae2ee3a7a2f8f571972b5f6052903a8bcaecc4db3f4'
$ExpectedSlideCount = 20
$WatercolorEntryName = 'ppt/media/image1.png'
$CanvasWidthPx = 1600
$CanvasHeightPx = 900

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)

    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-BytesSha256 {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.Convert]::ToHexString($algorithm.ComputeHash($Bytes)).ToLowerInvariant()
    }
    finally {
        $algorithm.Dispose()
    }
}

function Read-ZipEntryBytes {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchiveEntry]$Entry
    )

    $entryStream = $Entry.Open()
    try {
        $buffer = [System.IO.MemoryStream]::new()
        try {
            $entryStream.CopyTo($buffer)
            return $buffer.ToArray()
        }
        finally {
            $buffer.Dispose()
        }
    }
    finally {
        $entryStream.Dispose()
    }
}

function Save-PngAtomically {
    param(
        [Parameter(Mandatory = $true)]
        [System.Drawing.Bitmap]$Bitmap,

        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    $destinationPath = [System.IO.Path]::GetFullPath($Destination)
    $destinationDirectory = [System.IO.Path]::GetDirectoryName($destinationPath)
    [System.IO.Directory]::CreateDirectory($destinationDirectory) | Out-Null
    $temporaryPath = Join-Path $destinationDirectory ('.' + [System.IO.Path]::GetFileName($destinationPath) + ".tmp-$PID")
    try {
        $Bitmap.Save($temporaryPath, [System.Drawing.Imaging.ImageFormat]::Png)
        [System.IO.File]::Move($temporaryPath, $destinationPath, $true)
    }
    finally {
        if ([System.IO.File]::Exists($temporaryPath)) {
            [System.IO.File]::Delete($temporaryPath)
        }
    }
}

function New-WhiteCanvas {
    $bitmap = [System.Drawing.Bitmap]::new(
        $CanvasWidthPx,
        $CanvasHeightPx,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::White)
    }
    finally {
        $graphics.Dispose()
    }
    return $bitmap
}

function Convert-ProportionToPixel {
    param(
        [Parameter(Mandatory = $true)][long]$Value,
        [Parameter(Mandatory = $true)][long]$Whole,
        [Parameter(Mandatory = $true)][int]$Pixels
    )

    return [int][System.Math]::Round(
        ($Value / [double]$Whole) * $Pixels,
        [System.MidpointRounding]::AwayFromZero)
}

$sourcePath = (Resolve-Path -LiteralPath $SourceTemplatePath).Path
$sourceHash = Get-FileSha256 -Path $sourcePath
if ($sourceHash -ne $ExpectedSourceSha256) {
    throw "Unexpected source template SHA-256. Expected $ExpectedSourceSha256, got $sourceHash"
}

$sourceStream = [System.IO.File]::OpenRead($sourcePath)
try {
    $archive = [System.IO.Compression.ZipArchive]::new(
        $sourceStream,
        [System.IO.Compression.ZipArchiveMode]::Read,
        $false)
    try {
        $forbiddenParts = @(
            $archive.Entries |
                Where-Object {
                    $name = $_.FullName.ToLowerInvariant()
                    $name.StartsWith('ppt/embeddings/') -or
                    $name.StartsWith('ppt/activex/') -or
                    $name.StartsWith('ppt/comments/') -or
                    $name.StartsWith('ppt/fonts/') -or
                    $name.Contains('vbaproject.bin') -or
                    $name -match '\.(mp3|wav|m4a|wma|mp4|avi|mov|wmv)$'
                } |
                ForEach-Object FullName
        )
        if ($forbiddenParts.Count -gt 0) {
            throw "Source template contains forbidden package parts: $($forbiddenParts -join ', ')"
        }

        $externalRelationships = [System.Collections.Generic.List[string]]::new()
        foreach ($relationshipEntry in $archive.Entries | Where-Object FullName -Like '*.rels') {
            $relationshipBytes = Read-ZipEntryBytes -Entry $relationshipEntry
            [xml]$relationshipXml = [System.Text.Encoding]::UTF8.GetString($relationshipBytes)
            $externalNodes = $relationshipXml.SelectNodes(
                "//*[local-name()='Relationship' and @TargetMode='External']")
            foreach ($externalNode in $externalNodes) {
                $externalRelationships.Add(
                    "$($relationshipEntry.FullName):$($externalNode.Target)")
            }
        }
        if ($externalRelationships.Count -gt 0) {
            throw "Source template contains external relationships: $($externalRelationships -join ', ')"
        }

        $presentationEntry = $archive.GetEntry('ppt/presentation.xml')
        if ($null -eq $presentationEntry) {
            throw 'Source template is missing ppt/presentation.xml'
        }
        [xml]$presentationXml = [System.Text.Encoding]::UTF8.GetString(
            (Read-ZipEntryBytes -Entry $presentationEntry))
        $slideCount = $presentationXml.SelectNodes("//*[local-name()='sldId']").Count
        if ($slideCount -ne $ExpectedSlideCount) {
            throw "Unexpected source slide count. Expected $ExpectedSlideCount, got $slideCount"
        }

        $watercolorEntry = $archive.GetEntry($WatercolorEntryName)
        if ($null -eq $watercolorEntry) {
            throw "Source template is missing allowlisted asset $WatercolorEntryName"
        }
        $watercolorBytes = Read-ZipEntryBytes -Entry $watercolorEntry
        $watercolorHash = Get-BytesSha256 -Bytes $watercolorBytes
        if ($watercolorHash -ne $ExpectedWatercolorSha256) {
            throw "Unexpected watercolor asset SHA-256. Expected $ExpectedWatercolorSha256, got $watercolorHash"
        }

        $watercolorStream = [System.IO.MemoryStream]::new([byte[]]$watercolorBytes)
        try {
            $watercolor = [System.Drawing.Bitmap]::FromStream($watercolorStream)
            try {
                if ($watercolor.Width -ne 1261 -or $watercolor.Height -ne 1650) {
                    throw "Unexpected watercolor dimensions: $($watercolor.Width)x$($watercolor.Height)"
                }
                if (-not [System.Drawing.Image]::IsAlphaPixelFormat($watercolor.PixelFormat)) {
                    throw 'Allowlisted watercolor asset must have an alpha channel'
                }

                $cover = New-WhiteCanvas
                try {
                    $graphics = [System.Drawing.Graphics]::FromImage($cover)
                    try {
                        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
                        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality

                        # Reproduce the source deck's two allowlisted watercolor placements only.
                        # The source title, circle, metadata, icons, photographs and all other
                        # slide-local content are deliberately absent.
                        $destinationWidth = Convert-ProportionToPixel `
                            -Value 5317067 -Whole 12192000 -Pixels $CanvasWidthPx
                        $rightDestinationX = Convert-ProportionToPixel `
                            -Value 6877473 -Whole 12192000 -Pixels $CanvasWidthPx

                        $sourceLeft = [int][System.Math]::Round(
                            34612 / 100000.0 * $watercolor.Width,
                            [System.MidpointRounding]::AwayFromZero)
                        $sourceTopLeft = [int][System.Math]::Round(
                            16289 / 100000.0 * $watercolor.Height,
                            [System.MidpointRounding]::AwayFromZero)
                        $sourceTopRight = [int][System.Math]::Round(
                            16249 / 100000.0 * $watercolor.Height,
                            [System.MidpointRounding]::AwayFromZero)
                        $sourceRight = [int][System.Math]::Round(
                            9341 / 100000.0 * $watercolor.Width,
                            [System.MidpointRounding]::AwayFromZero)
                        $sourceBottom = [int][System.Math]::Round(
                            28535 / 100000.0 * $watercolor.Height,
                            [System.MidpointRounding]::AwayFromZero)
                        $sourceWidth = $watercolor.Width - $sourceLeft - $sourceRight
                        $sourceHeightLeft = $watercolor.Height - $sourceTopLeft - $sourceBottom
                        $sourceHeightRight = $watercolor.Height - $sourceTopRight - $sourceBottom

                        $graphics.DrawImage(
                            $watercolor,
                            [System.Drawing.Rectangle]::new(0, 0, $destinationWidth, $CanvasHeightPx),
                            [System.Drawing.Rectangle]::new(
                                $sourceLeft,
                                $sourceTopLeft,
                                $sourceWidth,
                                $sourceHeightLeft),
                            [System.Drawing.GraphicsUnit]::Pixel)

                        $rightDecoration = [System.Drawing.Bitmap]::new(
                            $destinationWidth,
                            $CanvasHeightPx,
                            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
                        try {
                            $rightGraphics = [System.Drawing.Graphics]::FromImage($rightDecoration)
                            try {
                                $rightGraphics.Clear([System.Drawing.Color]::Transparent)
                                $rightGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
                                $rightGraphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                                $rightGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                                $rightGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                                $rightGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                                $rightGraphics.DrawImage(
                                    $watercolor,
                                    [System.Drawing.Rectangle]::new(
                                        0,
                                        0,
                                        $destinationWidth,
                                        $CanvasHeightPx),
                                    [System.Drawing.Rectangle]::new(
                                        $sourceLeft,
                                        $sourceTopRight,
                                        $sourceWidth,
                                        $sourceHeightRight),
                                    [System.Drawing.GraphicsUnit]::Pixel)
                            }
                            finally {
                                $rightGraphics.Dispose()
                            }
                            $rightDecoration.RotateFlip([System.Drawing.RotateFlipType]::Rotate180FlipNone)
                            $graphics.DrawImageUnscaled($rightDecoration, $rightDestinationX, 0)
                        }
                        finally {
                            $rightDecoration.Dispose()
                        }
                    }
                    finally {
                        $graphics.Dispose()
                    }
                    Save-PngAtomically `
                        -Bitmap $cover `
                        -Destination (Join-Path $OutputDirectory 'watercolor-background.png')
                }
                finally {
                    $cover.Dispose()
                }

                $body = New-WhiteCanvas
                try {
                    Save-PngAtomically `
                        -Bitmap $body `
                        -Destination (Join-Path $OutputDirectory 'white-background.png')
                }
                finally {
                    $body.Dispose()
                }
            }
            finally {
                $watercolor.Dispose()
            }
        }
        finally {
            $watercolorStream.Dispose()
        }

        $tagPartCount = @($archive.Entries | Where-Object FullName -Like 'ppt/tags/*').Count
        $animationPartCount = 0
        foreach ($slideEntry in $archive.Entries | Where-Object FullName -Match '^ppt/slides/slide[0-9]+\.xml$') {
            [xml]$slideXml = [System.Text.Encoding]::UTF8.GetString(
                (Read-ZipEntryBytes -Entry $slideEntry))
            if ($slideXml.SelectNodes("//*[local-name()='transition' or local-name()='timing']").Count -gt 0) {
                $animationPartCount++
            }
        }
    }
    finally {
        $archive.Dispose()
    }
}
finally {
    $sourceStream.Dispose()
}

$assetRows = @(
    Get-ChildItem -LiteralPath $OutputDirectory -Filter '*.png' |
        Sort-Object Name |
        ForEach-Object {
            $image = [System.Drawing.Image]::FromFile($_.FullName)
            try {
                [ordered]@{
                    file = $_.Name
                    bytes = $_.Length
                    widthPx = $image.Width
                    heightPx = $image.Height
                    sha256 = 'sha256:' + (Get-FileSha256 -Path $_.FullName)
                }
            }
            finally {
                $image.Dispose()
            }
        }
)

[ordered]@{
    templatePackId = 'small-bear-watercolor-blue-v1'
    templatePackVersion = '1.0.0'
    sourceTemplateSha256 = 'sha256:' + $sourceHash
    sourceSlideCount = $slideCount
    allowlistedSourceAsset = [ordered]@{
        entry = $WatercolorEntryName
        sha256 = 'sha256:' + $watercolorHash
    }
    excluded = [ordered]@{
        stockPhotos = 6
        sampleText = $true
        sourceShapesAndIcons = $true
        transitionsAndTimings = $animationPartCount
        wpsTagParts = $tagPartCount
        externalRelationships = $externalRelationships.Count
        sourcePresentationPackage = $true
    }
    assets = $assetRows
} | ConvertTo-Json -Depth 6
