#!/bin/bash

# Check if the required parameter is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <subscriptionId>"
  exit 1
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

# Query the token
response=$(curl -s -o /dev/null -w "%{http_code}" -X GET --header "Authorization: Bearer ${bearerToken}" "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}")

if [ "$response" -eq 200 ]; then
  echo "Token exists."
  curl -s -X GET --header "Authorization: Bearer ${bearerToken}" "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}"
else
  echo "Token does not exist. Status code: $response"
  echo "Generating a new token..."
  curl -s -X POST --header "Authorization: Bearer ${bearerToken}" "https://face-sdk-gating-helper-2.azurewebsites.net/sdk/subscriptions/${subscriptionId}/tokens?id=${tokenId}"
fi