# Accessing Face Liveness SDK Release Artifacts

## Overview
This guide provides step-by-step instructions to access Face Liveness SDK release artifacts from Azure.

## Prerequisites
1. An Azure Subscription ID approved for Face Liveness Detection (complete the [Face Recognition intake form](https://aka.ms/facerecognition)).
2. Azure account with Cognitive Services Contributor role for permissions:
   - Microsoft.CognitivesServices/accounts/listKeys/action
   - Microsoft.CognitiveServices/accounts/regenerateKey/action

## Using Endpoints
Two endpoints are available for token queries and regeneration:
1. **Token Query Endpoint (GET):**
   - `https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}`
2. **Token Regeneration Endpoint (POST):**
   - `https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}`

Parameters:
- **subscriptionId**: Azure Subscription ID that has been registered for Face API Limited Access.
- **tokenId**: Optionally needed for the GET endpoint. An integer (0 or 1) for primary or secondary tokens. This allows you to generate primary and secondary keys that can be used while rotating secrets in build-automations.

**Note:** The GET endpoint lists all tokens if `tokenId` isn't specified. Use POST to generate tokens if none exist.

## Instructions

### Install The Azure Command-Line Interface (CLI)
Install Azure Command-Line Interface (CLI) as per the documentation [here](https://learn.microsoft.com/cli/azure/).

### Querying Access Token
1. Open your command-line tool (e.g., Bash in Linux/MacOS, PowerShell in Windows).
2. Run the following commands:

   **For Linux/MacOS:**
   ```bash
   bearerToken=$(az account get-access-token -o tsv | cut -f1)
   curl -X GET --header "Authorization: Bearer $bearerToken" "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}"
   ```

   **For Windows:**
   ```powershell
   $bearerToken = $(az account get-access-token -o tsv).split()[0];
   Invoke-WebRequest -Uri "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}" -Method GET -Headers @{"Authorization"="Bearer $bearerToken"}
   ```

### Regenerating Access Token
1. Use the same command-line tool as above.
2. Execute the following commands:

   **For Linux/MacOS:**
   ```bash
   bearerToken=$(az account get-access-token -o tsv | cut -f1)
   curl -X POST --header "Authorization: Bearer $bearerToken" "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}"
   ```

   **For Windows:**
   ```powershell
   $bearerToken = $(az account get-access-token -o tsv).split()[0];
   Invoke-RestMethod -Uri "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}" -Method POST -Headers @{"Authorization"="Bearer $bearerToken"}
   ```

## Additional Resources
- Learn about listing account keys in Azure Cognitive Service [here](https://learn.microsoft.com/rest/api/cognitiveservices/accountmanagement/accounts/list-keys).
- Information on regenerating account keys can be found [here](https://learn.microsoft.com/rest/api/cognitiveservices/accountmanagement/accounts/regenerate-key).