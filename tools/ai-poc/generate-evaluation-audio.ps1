param([string]$OutputDirectory = 'build/ai-poc-evaluation/audio', [ValidateSet('development','holdout','unseen')][string]$Corpus = 'development')
$ErrorActionPreference = 'Stop'
$probeRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$probeOutput = [IO.Path]::GetFullPath((Join-Path $probeRoot $OutputDirectory))
if (-not $probeOutput.StartsWith($probeRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'O diretório de saída deve estar dentro do workspace.'
}
$probeCorpusName = switch ($Corpus) { 'holdout' { 'evaluation-holdout.json' } 'unseen' { 'evaluation-unseen.json' } default { 'evaluation-cases.json' } }
$probeCorpusPath = Join-Path $probeRoot ('src/test/resources/ai-poc/' + $probeCorpusName)
$probeCorpus = Get-Content -Raw -Encoding UTF8 -LiteralPath $probeCorpusPath | ConvertFrom-Json
$probeExpectedCount = switch ($Corpus) { 'holdout' { 20 } 'unseen' { 10 } default { 50 } }
if (-not $probeCorpus.syntheticOnly -or $probeCorpus.cases.Count -ne $probeExpectedCount) { throw 'Corpus sintético inválido.' }
if (Test-Path -LiteralPath $probeOutput) { throw 'Diretório já existe. Use uma nova saída para preservar resultados anteriores.' }
New-Item -ItemType Directory -Path $probeOutput | Out-Null
Add-Type -AssemblyName System.Speech
$probeVoice = New-Object System.Speech.Synthesis.SpeechSynthesizer
$probeHashes = [ordered]@{}
try {
    $probeVoice.SelectVoice('Microsoft Maria Desktop')
    $probeFormat = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo -ArgumentList 16000,([System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen),([System.Speech.AudioFormat.AudioChannel]::Mono)
    foreach ($probeCase in $probeCorpus.cases) {
        if ($probeCase.id -notmatch '^[chu][0-9]{2}$') { throw 'Identificador inválido.' }
        $probeWavePath = Join-Path $probeOutput ($probeCase.id + '.wav')
        if ([string]::IsNullOrWhiteSpace($probeCase.text)) {
            $probeWriter = New-Object IO.BinaryWriter([IO.File]::Create($probeWavePath))
            try {
                $probeWriter.Write([Text.Encoding]::ASCII.GetBytes('RIFF')); $probeWriter.Write([int]64036)
                $probeWriter.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt ')); $probeWriter.Write([int]16)
                $probeWriter.Write([int16]1); $probeWriter.Write([int16]1); $probeWriter.Write([int]16000)
                $probeWriter.Write([int]32000); $probeWriter.Write([int16]2); $probeWriter.Write([int16]16)
                $probeWriter.Write([Text.Encoding]::ASCII.GetBytes('data')); $probeWriter.Write([int]64000)
                $probeWriter.Write((New-Object byte[] 64000))
            } finally { $probeWriter.Dispose() }
        } else {
            $probeVoice.SetOutputToWaveFile($probeWavePath, $probeFormat)
            $probeVoice.Speak($probeCase.text)
            $probeVoice.SetOutputToNull()
        }
        $probeHashes[$probeCase.id] = (Get-FileHash -LiteralPath $probeWavePath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
} finally { $probeVoice.Dispose() }
$probeManifest = [ordered]@{ syntheticOnly=$true; voice='Microsoft Maria Desktop'; corpusSha256=(Get-FileHash -LiteralPath $probeCorpusPath -Algorithm SHA256).Hash.ToLowerInvariant(); files=$probeHashes }
$probeJson = $probeManifest | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $probeOutput 'synthetic-manifest.json'), $probeJson, (New-Object Text.UTF8Encoding($false)))
Write-Output "$probeExpectedCount WAVs sintéticos gerados em $probeOutput; manifesto com hashes pronto. Nenhuma chamada externa."
