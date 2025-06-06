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

### Fetching Access Token

1. Open your command-line tool (e.g., Terminal in Linux/MacOS, PowerShell in Windows/Linux/MacOS).
1. Run the following script:

   Parameters:
   - **subscriptionId**: Azure Subscription ID that has been registered for Face API Limited Access.

   **For Linux/MacOS or Shell:**

   ```bash
   ./scripts/get_face_access_token.sh #SUBSCRIPTION_ID#
   ```

   **For PowerShell on Windows/Linux/MacOS :**

   ```powershell
   ./scripts/get_face_access_token.ps1 -subscriptionId #SUBSCRIPTION_ID#
   ```

## References

This endpoint is available for token generations and queries:

1. **Token Generation (POST) / Query (GET) Endpoint (`POST`):**

   ```text
   https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}
   ```

> **Note:** The `GET` endpoint lists all tokens if `tokenId` is not specified. Use `POST` to generate tokens if none exist.

## Troubleshooting

Run this script on PowerShell to obtain diagnostics:

```powershell
./scripts/diagnose_token.ps1
```

## Additional Resources

- Use the token to get started with Android sample [here](samples/kotlin/face/FaceAnalyzerSample/README.md).
- Use the token to get started with iOS sample [here](samples/swift/face/FaceAnalyzerSample/README.md).
- Use the token to get started with Web sample [here](samples/web/README.md).
