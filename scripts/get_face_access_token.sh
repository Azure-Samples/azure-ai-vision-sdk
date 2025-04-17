#!/bin/bash

if az account show --output none 2>/dev/null; then
    echo "User is already logged in."
else
    if az login --output none ; then
        echo "Azure login successful!"
    else
        echo "az login failed, install Azure CLI from https://learn.microsoft.com/cli/azure/."
        exit 1
    fi
fi

if [ -z "$1" ]; then
    echo "Info: Subscription ID is not provided."
    echo "Info: If you want to provide the subscription ID manually, please run the script as below."
    echo "Usage: $0 <subscriptionId> [tokenId]"
    echo "Info: Valid token IDs are 0 or 1"
    subscriptionId=$(az account show --query id -o tsv)
    if [ -z "$subscriptionId" ]; then
        echo "Error: Failed to retrieve the subscription ID."
        exit 1
    fi
    echo "Info: Now using the default subscription ID $subscriptionId"
else
    subscriptionId=$1
fi

if [ -z "$2" ]; then
    echo "Info: Token ID is not provided."
    echo "Info: If you want to provide the token ID manually, please run the script as below."
    echo "Usage: $0 <subscriptionId> [tokenId]"
    echo "Info: Valid token IDs are 0 or 1"
    tokenArg=""
else
    tokenId=$2
    tokenArg="id=${tokenId}"
fi

echo "Fetching a token..."
az rest --method get --resource "https://management.core.windows.net/" --uri "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?${tokenArg}"
