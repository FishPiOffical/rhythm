param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "..\src\main\resources\search-engines")
)

$ErrorActionPreference = "Stop"

# 仅取搜索角色的官方来源；Anthropic 共用官方来源，但 Java 只识别 Claude-SearchBot。
$sources = @(
    @{ Name = "OAI-SearchBot"; Url = "https://openai.com/searchbot.json"; File = "oai-search-bot-prefixes.json" },
    @{ Name = "Claude-SearchBot"; Url = "https://claude.com/crawling/bots.json"; File = "claude-search-bot-prefixes.json" },
    @{ Name = "PerplexityBot"; Url = "https://www.perplexity.ai/perplexitybot.json"; File = "perplexity-bot-prefixes.json" },
    @{ Name = "MistralAI-Index"; Url = "https://mistral.ai/mistralai-index-ips.json"; File = "mistral-index-bot-prefixes.json" }
)

$snapshots = @()
foreach ($source in $sources) {
    Write-Host "==> Download $($source.Name) IP list: $($source.Url)"
    $response = Invoke-WebRequest -Uri $source.Url -UseBasicParsing -TimeoutSec 30
    if ($response.StatusCode -ne 200) {
        throw "$($source.Name): HTTP $($response.StatusCode)"
    }
    $json = $response.Content | ConvertFrom-Json
    if ($null -eq $json.prefixes -or @($json.prefixes).Count -eq 0) {
        throw "$($source.Name): missing or empty prefixes"
    }

    $prefixes = @{}
    foreach ($prefix in $json.prefixes) {
        $fields = @($prefix.PSObject.Properties.Name | Where-Object { $_ -in @("ipv4Prefix", "ipv6Prefix") })
        if ($fields.Count -ne 1) {
            throw "$($source.Name): each entry must contain exactly one IP prefix"
        }
        $field = $fields[0]
        $cidr = [string]$prefix.$field
        $parts = $cidr.Split('/')
        $address = $null
        $bits = 0
        if ($parts.Count -ne 2 -or $parts[0] -notmatch '^[0-9a-fA-F:.]+$' -or
                -not [System.Net.IPAddress]::TryParse($parts[0], [ref]$address) -or
                $parts[1] -notmatch '^\d+$' -or -not [int]::TryParse($parts[1], [ref]$bits)) {
            throw "$($source.Name): invalid CIDR [$cidr]"
        }
        $isV4 = $address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork
        $maxBits = if ($isV4) { 32 } else { 128 }
        # 禁止全网通配，避免官方源格式异常时把任意来源当成搜索蜘蛛。
        if (($field -eq "ipv4Prefix") -ne $isV4 -or $bits -le 0 -or $bits -gt $maxBits) {
            throw "$($source.Name): invalid address family or unsafe CIDR [$cidr]"
        }
        $prefixes["$field|$cidr"] = @{ $field = $cidr }
    }
    $result = [ordered]@{
        source = [ordered]@{ name = $source.Name; url = $source.Url; creationTime = $json.creationTime }
        prefixes = @($prefixes.GetEnumerator() | Sort-Object Name | ForEach-Object { $_.Value })
    }
    $snapshots += @{ File = $source.File; Content = ($result | ConvertTo-Json -Depth 6) }
    Write-Host "==> Validated $($source.Name): $($prefixes.Count) prefixes"
}

# 全部下载并校验成功后才写入；失败不能用空列表覆盖已有快照。
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
foreach ($snapshot in $snapshots) {
    $target = Join-Path (Resolve-Path $OutputDirectory).Path $snapshot.File
    $temporary = "$target.tmp"
    try {
        [System.IO.File]::WriteAllText($temporary, $snapshot.Content + "`n", [System.Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temporary -Destination $target -Force
    } finally {
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary
        }
    }
}
