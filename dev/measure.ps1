# 부하 측정 한 번: lag 0까지 대기 → 시뮬레이터 → 측정 구간 Prometheus 쿼리 → 다 처리될 때까지 대기
# 서버는 load 프로필로, Prometheus는 compose로 떠 있어야 한다
# ★ 이 파일은 BOM 있는 UTF-8로 둔다. PowerShell 5.1은 BOM 없으면 한글 주석이 깨져 다음 줄을 삼킨다
param([Parameter(Mandatory)][string]$SimArgs, [int]$Window = 60)
Set-Location (Join-Path $PSScriptRoot '..')

function pq($q, $at) {
  $r = Invoke-RestMethod "http://localhost:9090/api/v1/query?query=$([uri]::EscapeDataString($q))&time=$at"
  ($r.data.result | ForEach-Object { [math]::Round([double]$_.value[1], 3) }) -join ','
}

# ★ kafka_consumer_fetch_manager_records_lag_max 는 쓰지 않는다. 컨슈머가 가져올 때 본 값이라
#   처리에 묶이면 낡거나 0이 된다(실제 69,500건일 때 0). 커밋된 오프셋 기준으로 센다
function groupLag {
  $rows = docker exec heavy-eq-tracker-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group tracker-judge 2>$null
  ($rows | Where-Object { $_ -match '^tracker-judge\s' } | ForEach-Object { [long](($_ -split '\s+')[5]) } | Measure-Object -Sum).Sum
}

function waitDrained {
  $t = Get-Date
  while ((groupLag) -gt 0) { Start-Sleep 15 }
  [int]((Get-Date) - $t).TotalSeconds
}

"drained before run: $(waitDrained) s"
$out = & .\gradlew.bat -q :simulator:run "--args=$SimArgs"
"lag right after run: $(groupLag)"
$out
$end = [long](($out | Select-String 'end=(\d+)').Matches[0].Groups[1].Value)
Start-Sleep 12   # 마지막 구간이 수집되도록(scrape 5초)

$w = "${Window}s"
$recv = 'tracker_location_judged_since_received_seconds'
$lis = 'spring_kafka_listener_seconds'
"processed/s: " + (pq "sum(increase(${recv}_count[$w])) / $Window" $end)
"judged p50/p99 s: " + (pq "histogram_quantile(0.5, sum by (le) (increase(${recv}_bucket[$w])))" $end) + " / " + (pq "histogram_quantile(0.99, sum by (le) (increase(${recv}_bucket[$w])))" $end)
"listener p50/p99 s: " + (pq "histogram_quantile(0.5, sum by (le) (increase(${lis}_bucket[$w])))" $end) + " / " + (pq "histogram_quantile(0.99, sum by (le) (increase(${lis}_bucket[$w])))" $end)
"drained after run: $(waitDrained) s"
