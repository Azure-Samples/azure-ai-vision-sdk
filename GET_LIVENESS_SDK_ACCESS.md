# How to access Face Liveness SDK release

## Prerequisites
An Azure Subscription ID that has been approved to use Face.LivenessDetection by completing the Face Recognition intake form.
Your Azure account must have a Cognitive Services Contributor role assigned to get the following permissions:
- Microsoft.CognitiveServices/accounts/listKeys/action
- Microsoft.CognitiveServices/accounts/regenerateKey/action

## Token Endpoint
1. Query token

    GET https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}
2. Regenerate token
    
    POST https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}

## Instructions
In your command line, run with following codes:
```
token=$(az account get-access-token -o tsv | cut -f1)

curl -X GET --header "Authorization: Bearer $token" "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}"
```

- **subscriptionId**: Azure Subscription ID that has been registered for Face API Limited Access
- **tokenId**: integer between 0 and 1, inclusive (0=primary, 1=secondary)

Note: 
**tokenId** is optional for GET endpoint. It will list all available tokens if tokenId is not set explicitly. If no tokens have been generated, GET will return empty, and you can run POST to generate one or both tokens. **tokenId** can be 0 or 1 so that we have primary and secondary keys that can be used while rotating secrets in automations.

## Related references for verbiage
- [Accounts - List Keys - REST API (Azure Cognitive Services) | Microsoft Learn](https://learn.microsoft.com/en-us/rest/api/cognitiveservices/accountmanagement/accounts/list-keys?view=rest-cognitiveservices-accountmanagement-2023-05-01&tabs=HTTP)
- [Accounts - Regenerate Key - REST API (Azure Cognitive Services) | Microsoft Learn](https://learn.microsoft.com/en-us/rest/api/cognitiveservices/accountmanagement/accounts/regenerate-key?view=rest-cognitiveservices-accountmanagement-2023-05-01&tabs=HTTP)
- [Get an access token to use the FHIR service or the DICOM service | Microsoft Learn](https://learn.microsoft.com/en-us/azure/healthcare-apis/get-access-token?tabs=azure-cli)
- [Manage account access keys - Azure Storage | Microsoft Learn](https://learn.microsoft.com/en-us/azure/storage/common/storage-account-keys-manage?tabs=azure-portal)
- [Rotation tutorial for resources with two sets of credentials | Microsoft Learn](https://learn.microsoft.com/en-us/azure/key-vault/secrets/tutorial-rotation-dual?tabs=azure-cli)
- [Learn how to secure access to data in Azure Cosmos DB | Microsoft Learn](https://learn.microsoft.com/en-us/azure/cosmos-db/secure-access-to-data?tabs=using-primary-key#primary-keys)
