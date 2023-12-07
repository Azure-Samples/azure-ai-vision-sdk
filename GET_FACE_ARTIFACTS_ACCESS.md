# How to access Face Liveness SDK release artifacts

## Prerequisites
An Azure Subscription ID that has been approved to use Face Liveness Detection by completing the Face Recognition intake form.
Your Azure account must have a Cognitive Services Contributor role assigned to get the following permissions:
- Microsoft.CognitiveServices/accounts/listKeys/action
- Microsoft.CognitiveServices/accounts/regenerateKey/action

## Endpoints
We have provided the following two endpoints for token query and regenerate. You can find how to use them in the next section [Instructions](#instructions).
1. Endpoint to query token

    GET https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}
2. Endpoint to regenerate token

    POST https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}

The required parameters in above endpoints are:
- **subscriptionId**: Azure Subscription ID that has been registered for Face API Limited Access
- **tokenId**: integer between 0 and 1, inclusive (0=primary, 1=secondary)

    Note: 
    **tokenId** is optional for GET endpoint. It will list all available tokens if tokenId is not set explicitly. If no tokens have been generated, GET will return empty, and you can run POST to generate one or both tokens. **tokenId** can be 0 or 1 so that we have primary and secondary keys that can be used while rotating secrets in automations.


## Instructions

### 1. Install Azure Command-Line Interface (CLI)

Please refer to the documentation [here](https://learn.microsoft.com/cli/azure/) and install the CLI in your computer.

### 2. How to query access token for release artifacts

Open your command line tool, like (Command Prompt App in Windows or Terminal App in MacOS). Run the following commands to get accessToken:

```
bearerToken=$(az account get-access-token -o tsv | cut -f1)

curl -X GET --header "Authorization: Bearer $bearerToken" "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}"
```

If you want to learn more about "how to list account keys" in Azure Cognitive Service, you can find more information here:
    - [Accounts - List Keys - REST API (Azure Cognitive Services) | Microsoft Learn](https://learn.microsoft.com/rest/api/cognitiveservices/accountmanagement/accounts/list-keys?view=rest-cognitiveservices-accountmanagement-2023-05-01&tabs=HTTP)

### 3. How to regenerate access token for release artifacts

Open your command line tool, like (Command Prompt App in Windows or Terminal App in MacOS). Run the following commands to get accessToken:

```
bearerToken=$(az account get-access-token -o tsv | cut -f1)

curl -X POST --header "Authorization: Bearer $bearerToken" "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}"
```

If you want to learn more about "how to regenerate account keys" in Azure Cognitive Service, you can find more information here:
- [Accounts - Regenerate Key - REST API (Azure Cognitive Services) | Microsoft Learn](https://learn.microsoft.com/rest/api/cognitiveservices/accountmanagement/accounts/regenerate-key?view=rest-cognitiveservices-accountmanagement-2023-05-01&tabs=HTTP)
