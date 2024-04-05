# Accessing Azure AI Vision Face Client SDK Release Artifacts

## Overview
This guide provides step-by-step instructions to access Face Client SDK release artifacts from Azure.

## Prerequisites
1. An [Azure Subscription ID](https://learn.microsoft.com/azure/azure-portal/get-subscription-tenant-id) approved for Face Liveness Detection (complete the [Face Recognition intake form](https://aka.ms/facerecognition)).
2. Azure account with [Cognitive Services Contributor role](https://learn.microsoft.com/azure/role-based-access-control/role-assignments-list-portal) for permissions:
   - [`Microsoft.CognitiveServices/accounts/listKeys/action`](https://learn.microsoft.com/rest/api/cognitiveservices/accountmanagement/accounts/list-keys)
   - [`Microsoft.CognitiveServices/accounts/regenerateKey/action`](https://learn.microsoft.com/rest/api/cognitiveservices/accountmanagement/accounts/regenerate-key)

## Using Endpoints
Two endpoints are available for token generations and queries:
   
1. **Token Generation Endpoint (`POST`):**
   
   ```
   https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}
   ```
   
1. **Token Query Endpoint (`GET`):**

   ```
   https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}
   ```

Parameters:
- **subscriptionId**: Azure Subscription ID that has been registered for Face API Limited Access.
- **tokenId**: An integer (0 or 1) for primary or secondary tokens. This allows you to generate primary and secondary keys that can be used while rotating secrets in build-automations. This parameter is required for `POST` and optional for `GET`.

> **Note:** The `GET` endpoint lists all tokens if `tokenId` is not specified. Use `POST` to generate tokens if none exist.

## Instructions

### Install The Azure Command-Line Interface (CLI)
Install Azure Command-Line Interface (CLI) as per the documentation [here](https://learn.microsoft.com/cli/azure/).

### Generating Access Token

> **Note:** This will overwrite the previously generated token of `tokenId` if any. If you would like to preserve and retrieve the token value previously generated, go to [querying access token](#querying-access-token) section.

1. Open your command-line tool (e.g., Bash in Linux/MacOS, PowerShell in Windows).
1. Run the following commands:

   **For Linux/MacOS or Shell:**
   ```bash
   subscriptionId = #SUBSCRIPTION_ID#
   tokenId = #TOKEN_ID#
   bearerToken=$(az account get-access-token -s $subscriptionId -o tsv | cut -f1)
   curl -X POST --header "Authorization: Bearer ${bearerToken}" "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}"
   ```

   **For Windows or PowerShell:**
   ```powershell
   $subscriptionId = #SUBSCRIPTION_ID#
   $tokenId = #TOKEN_ID#
   $bearerToken = $(az account get-access-token -s $subscriptionId -o tsv).split()[0];
   Invoke-RestMethod -Uri "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}" -Method POST -Headers @{"Authorization"="Bearer ${bearerToken}"}
   ```

### Querying Access Token
1. Use the same command-line tool as above.
1. Run the following commands:

   **For Linux/MacOS:**
   ```bash
   subscriptionId = #SUBSCRIPTION_ID#
   tokenId = #TOKEN_ID#
   bearerToken=$(az account get-access-token -s $subscriptionId -o tsv | cut -f1)
   curl -X GET --header "Authorization: Bearer ${bearerToken}" "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}"
   ```

   **For Windows or PowerShell:**
   ```powershell
   $subscriptionId = #SUBSCRIPTION_ID#
   $tokenId = #TOKEN_ID#
   $bearerToken = $(az account get-access-token -s $subscriptionId -o tsv).split()[0];
   Invoke-WebRequest -Uri "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}" -Method GET -Headers @{"Authorization"="Bearer ${bearerToken}"}
   ```

## Additional Resources
- Use the token to get started with Android sample [here](samples/kotlin/face/FaceAnalyzerSample/README.md).
- Use the token to get started with iOS sample [here](samples/swift/face/FaceAnalyzerSample/README.md).
