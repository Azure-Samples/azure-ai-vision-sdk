param (
    [Parameter(Mandatory = $true)]
    [string]$subscriptionId
)

# Set tokenId
$tokenId = 0

# Get the bearer token
try {
    $bearerToken = $(az account get-access-token -s $subscriptionId -o tsv).split()[0]
} catch {
    Write-Host "Failed to retrieve the bearer token."
    exit 1
}

# Query the token
try {
    $response = Invoke-WebRequest -Uri "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}" -Method GET -Headers @{"Authorization"="Bearer ${bearerToken}"}
    $statusCode = $response.StatusCode
    if ($statusCode -eq 200) {
        Write-Host "Token exists."
        $response.Content | ConvertFrom-Json | Format-List
    } else {
        Write-Host "Token does not exist. Status code: $statusCode"
        Write-Host "Generating a new token..."
        $response = Invoke-RestMethod -Uri "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}" -Method POST -Headers @{"Authorization"="Bearer ${bearerToken}"}
        Write-Host "Token generated successfully."
        $response | Format-List
    }
} catch {
    Write-Host "Error: $_.Exception.Message"
    Write-Host "Generating a new token..."
    $response = Invoke-RestMethod -Uri "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}" -Method POST -Headers @{"Authorization"="Bearer ${bearerToken}"}
    Write-Host "Token generated successfully."
    $response | Format-List
}
