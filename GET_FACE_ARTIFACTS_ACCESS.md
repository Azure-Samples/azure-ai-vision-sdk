# Accessing Azure AI Vision Face Client SDK Release Artifacts

## Overview

This guide provides step-by-step instructions to access Face Client SDK release artifacts from Azure.

## Prerequisites

1. An [Azure Subscription ID](https://learn.microsoft.com/azure/azure-portal/get-subscription-tenant-id) approved for Face Liveness Detection (complete the [Face Recognition intake form](https://aka.ms/facerecognition)).
2. Azure account with [Cognitive Services Contributor role](https://learn.microsoft.com/azure/role-based-access-control/role-assignments-list-portal) with [subscription-level scope](https://learn.microsoft.com/azure/role-based-access-control/scope-overview#:~:text=subscription), or equivalently, the following permissions with [subscription-level scope](https://learn.microsoft.com/azure/role-based-access-control/scope-overview#:~:text=subscription):
   - [`Microsoft.Authorization/permissions/read`](https://learn.microsoft.com/azure/role-based-access-control/permissions/management-and-governance#:~:text=Microsoft.Authorization/permissions/read)
   - [`Microsoft.CognitiveServices/accounts/read`](https://learn.microsoft.com/azure/role-based-access-control/permissions/ai-machine-learning#:~:text=Microsoft.CognitiveServices/accounts/read)
   - [`Microsoft.CognitiveServices/accounts/listKeys/action`](https://learn.microsoft.com/azure/role-based-access-control/permissions/ai-machine-learning#:~:text=Microsoft.CognitiveServices/accounts/listKeys/action) (only required if token id is specified)
   - [`Microsoft.CognitiveServices/accounts/regenerateKey/action`](https://learn.microsoft.com/azure/role-based-access-control/permissions/ai-machine-learning#:~:text=Microsoft.CognitiveServices/accounts/regenerateKey/action) (only required to regenerate non-expired tokens)

## Instructions

### Install The Azure Command-Line Interface (CLI)

Install Azure Command-Line Interface (CLI) as per the documentation [here](https://learn.microsoft.com/cli/azure/).

### Fetching Access Token (Recommended)

1. Open your command-line tool (e.g., Terminal in Linux/MacOS, PowerShell in Windows/Linux/MacOS).
1. Run the following script:

   Parameters:
   - **`#SUBSCRIPTION_ID#`**: Azure Subscription ID that has been registered for Face API Limited Access.

   **For Linux/MacOS or Shell:**

   ```bash
   ./scripts/get_face_access_token.sh #SUBSCRIPTION_ID#
   or
   ## To Generate New Access Token
   ./scripts/get_face_access_token.sh #SUBSCRIPTION_ID# #tokenId#
   ```

   **For PowerShell on Windows/Linux/MacOS :**

   ```powershell
   ./scripts/get_face_access_token.ps1 -subscriptionId #SUBSCRIPTION_ID#
   or
   ## To Generate New Access Token
   ./scripts/get_face_access_token.ps1 -subscriptionId #SUBSCRIPTION_ID# --tokenId #New-Integer-Token-ID#
   ```
> **Note:** The access token remains valid for one week from the date of its creation.

## References

This endpoint is available for token generations and queries:

1. **Token Generation (POST) / Query (GET) Endpoint:**

   ```text
   https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}
   ```

> **Note:** The `GET` endpoint lists all tokens if `tokenId` is not specified. Use `POST` to generate tokens if none exist.

> **Note:** `Authorization` header of `Bearer` scheme is required, so the most straightforward client to use is [`az rest`](https://learn.microsoft.com/cli/azure/reference-index?view=azure-cli-latest#az-rest) command from Azure CLI. Such calls can be made with the following command:
>
>   ```bash
>   az rest --method {method} --uri "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}"
>   ```
>
> where `{method}` is either `GET` or `POST`, and `{tokenId}` is the token ID you want to query or generate. It is recommended to omit the `id` query parameter for `GET` requests to list all tokens.

## Troubleshooting

Run this script on PowerShell to obtain diagnostics:

```powershell
./scripts/diagnose_token.ps1
```
Some users are reporting issues of getting expired tokens with GET method. If you encounter this, please try the POST method by running the following instead of the `get_face_access_token` script:

**For Linux/MacOS or Shell:**

```bash
./scripts/create_face_access_token.sh #SUBSCRIPTION_ID#
```

**For PowerShell on Windows/Linux/MacOS :**

```powershell
./scripts/create_face_access_token.ps1 -subscriptionId #SUBSCRIPTION_ID#
```

## Additional Resources

- Use the token to get started with Android sample [here](samples/kotlin/face/FaceAnalyzerSample/README.md).
- Use the token to get started with iOS sample [here](samples/swift/face/FaceAnalyzerSample/README.md).
- Use the token to get started with Web sample [here](samples/web/README.md).
