
#!/bin/bash

# Check if the required parameter is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <subscriptionId>"
  exit 1
fi

# do az login if needed
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

subscriptionId=$1
tokenId=0

# Get the bearer token
bearerToken=$(az account get-access-token -s $subscriptionId -o tsv | cut -f1)

# Check if bearerToken was successfully retrieved
if [ -z "$bearerToken" ]; then
    echo "Error: Failed to retrieve the bearer token."
    exit 1
fi

# Generate the token
echo "Generating a new token..."
curl -s -X POST --header "Authorization: Bearer ${bearerToken}" "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}"
