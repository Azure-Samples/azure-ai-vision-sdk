param (
    [string]$subscriptionId,
    [string]$tokenId
)

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

if (-not $subscriptionId) {
    Write-Host "Info: Subscription ID is not provided."
    Write-Host "Info: If you want to provide the subscription ID manually, please run the script as below."
    Write-Host "Usage: .\create_face_access_token.ps1 -subscriptionId <subscriptionId> [-tokenId <tokenId>]"
    Write-Host "Info: Valid token IDs are 0 or 1"
    try {
        $subscriptionId = az account show --query id -o tsv
        if (-not $subscriptionId) {
            throw "Failed to retrieve the subscription ID."
        }
        Write-Host "Info: Now using the default subscription ID $subscriptionId"
    } catch {
        Write-Host "Error: Failed to retrieve the subscription ID."
        exit 1
    }
}

if (-not $tokenId) {
    Write-Host "Info: Token ID is not provided."
    Write-Host "Info: If you want to provide the token ID manually, please run the script as below."
    Write-Host "Usage: .\create_face_access_token.ps1 -subscriptionId <subscriptionId> [-tokenId <tokenId>]"
    Write-Host "Info: Valid token IDs are 0 or 1"
    $tokenId = "0"
    Write-Host "Info: Now using the default token ID $tokenId"
}
$tokenArg = "id=$tokenId"


Write-Host "Fetching a token..."
az rest --method post --resource "https://management.core.windows.net/" --uri "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?$tokenArg"
