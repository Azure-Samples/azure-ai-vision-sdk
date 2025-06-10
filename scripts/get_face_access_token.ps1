param (
    [Parameter(Mandatory = $true)]
    [string]$subscriptionId
)

# do az login if needed
try {
    $account = az account show --output none 2>$null
    if ($?) {
        Write-Host "User is already logged in." -ForegroundColor Green
    } else {
        throw "Not logged in."
    }
} catch {
    Write-Host "User not logged in. Attempting az login..." -ForegroundColor Yellow
    try {
        az login --output none
        if ($?) {
            Write-Host "Azure login successful!" -ForegroundColor Green
        } else {
            throw "Login failed."
        }
    } catch {
        Write-Host "az login failed, install Azure CLI from https://learn.microsoft.com/cli/azure/." -ForegroundColor Red
        exit 1
    }
}

# Set tokenId
$tokenId = 0

# Get the bearer token
try {
    $bearerToken = $(az account get-access-token -s $subscriptionId -o tsv).split()[0]
} catch {
    Write-Host "Failed to retrieve the bearer token."
    exit 1
}

# Generate the token
Write-Host "Generating a new token..."
$response = Invoke-RestMethod -Uri "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}" -Method POST -Headers @{"Authorization"="Bearer ${bearerToken}"}
Write-Host "Token generated successfully."
$response | Format-List