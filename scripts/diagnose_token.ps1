$subscriptions = az account list | ConvertFrom-Json | Sort-Object -Property name
$anyWorkingSubscription = $false

foreach ($subscription in $subscriptions) {
    Write-Progress -Id 1 -Activity "Checking subscriptions" -Status "Processing subscription: $($subscription.name)" -PercentComplete (($subscriptions.IndexOf($subscription) + 1) / $subscriptions.Count * 100)
    $hasFlag = $false
    $hasListKeys = $false
    $keys = $null
    $resources = $(az cognitiveservices account list --subscription $subscription.id 2>$null) | ConvertFrom-Json
    if ($resources -isnot [array]) {
        Write-Host "[$($subscription.name)]($($subscription.id)) -- Cannot list resources" -ForegroundColor DarkGray
        continue
    }
    foreach ($resource in $resources) {
        Write-Progress -ParentId 1 -Activity "Checking resources" -Status "Processing resource: $($resource.name)" -PercentComplete (($resources.IndexOf($resource) + 1) / $resources.Count * 100)
        foreach ($capability in $resource.properties.capabilities) {
            if ($capability.name -eq "Scenario") {
                if ($capability.value -split "," -contains "Face.LivenessDetection") {
                    $hasFlag = $true
                    $keys = $(az cognitiveservices account keys list --name $resource.name --resource-group $resource.resourceGroup --subscription $subscription.id 2>$null) | ConvertFrom-Json
                    if ($($keys.PSObject.Properties).Count -gt 0) {
                        $hasListKeys = $true
                        $keys = $null
                        $anyWorkingSubscription = $true
                        Write-Host "[$($subscription.name)]($($subscription.id)) -- " -NoNewline
                        Write-Host "OK" -ForegroundColor Green
                        break
                    }
                    $keys = $null
                }
            }
        }
        if ($hasFlag -and $hasListKeys) {
            break
        }
    }
    if (-not $hasListKeys) {
       Write-Host "[$($subscription.name)]($($subscription.id)) -- Cannot list API keys" -ForegroundColor DarkGray
    }
}

if (-not $anyWorkingSubscription) {
    Write-Host "No subscription with Face.LivenessDetection capability found." -ForegroundColor Red
    exit 1
}