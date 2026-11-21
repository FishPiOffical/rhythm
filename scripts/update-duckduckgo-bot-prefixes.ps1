param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "..\src\main\resources\search-engines")
)

$ErrorActionPreference = "Stop"

# 保留 DuckDuckGo 两来源合并的旧规则；其他搜索角色独立存储，不混入用户抓取或训练列表。
$feeds = @(
    @{
        File = "duckduckgo-bot-prefixes.json"
        Sources = @(
            @{ Name = "DuckDuckBot"; Url = "https://duckduckgo.com/duckduckbot.json" },
            @{ Name = "DuckAssistBot"; Url = "https://duckduckgo.com/duckassistbot.json" }
        )
    },
    @{ File = "oai-search-bot-prefixes.json"; Sources = @(@{ Name = "OAI-SearchBot"; Url = "https://openai.com/searchbot.json" }) },
    @{ File = "claude-search-bot-prefixes.json"; Sources = @(@{ Name = "Claude-SearchBot"; Url = "https://claude.com/crawling/bots.json" }) },
    @{ File = "perplexity-bot-prefixes.json"; Sources = @(@{ Name = "PerplexityBot"; Url = "https://www.perplexity.ai/perplexitybot.json" }) },
    @{ File = "mistral-index-bot-prefixes.json"; Sources = @(@{ Name = "MistralAI-Index"; Url = "https://mistral.ai/mistralai-index-ips.json" }) }
)

$snapshots = @()
foreach ($feed in $feeds) {
    $prefixes = @{}
    $sourceResults = @()
    foreach ($source in $feed.Sources) {
        Write-Host "==> Download $($source.Name) IP list: $($source.Url)"
        $response = Invoke-WebRequest -Uri $source.Url -UseBasicParsing -TimeoutSec 30
        if ($response.StatusCode -ne 200) {
            throw "$($source.Name): HTTP $($response.StatusCode)"
        }
        $json = $response.Content | ConvertFrom-Json
        if ($null -eq $json.prefixes -or @($json.prefixes).Count -eq 0) {
            throw "$($source.Name): missing or empty prefixes"
        }
        $count = 0
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
            # 拒绝全网通配，避免源格式异常时把任意来源识别为可信蜘蛛。
            if (($field -eq "ipv4Prefix") -ne $isV4 -or $bits -le 0 -or $bits -gt $maxBits) {
                throw "$($source.Name): invalid address family or unsafe CIDR [$cidr]"
            }
            $prefixes["$field|$cidr"] = @{ $field = $cidr }
            $count++
        }
        $sourceResults += [ordered]@{
            name = $source.Name
            url = $source.Url
            creationTime = $json.creationTime
            prefixCount = $count
        }
        Write-Host "==> Validated $($source.Name): $count prefixes"
    }
    $result = [ordered]@{}
    if ($feed.Sources.Count -gt 1) {
        $result.sources = @($sourceResults)
    } else {
        # 保持上一版单角色快照的元数据格式。
        $result.source = [ordered]@{
            name = $sourceResults[0].name
            url = $sourceResults[0].url
            creationTime = $sourceResults[0].creationTime
        }
    }
    $result.prefixes = @($prefixes.GetEnumerator() | Sort-Object Name | ForEach-Object { $_.Value })
    $snapshots += @{ File = $feed.File; Content = ($result | ConvertTo-Json -Depth 6) }
}

# 所有来源都下载并校验成功后才写入，下载失败不覆盖任何现有快照。
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
    Write-Host "==> Updated $($snapshot.File)"
}

# DNS 核验的引擎不需要列表；360无官方JSON接口，保持人工复核，不抓网页扩大信任。
Write-Host "==> DNS verification (no IP feed): Google, Baidu, Bing, Sogou, Yandex, Applebot."
Write-Host "==> 360: manual maintenance only; 360-bot-prefixes.json was NOT changed. Official source: https://www.so.com/help/spider_ip.html"
