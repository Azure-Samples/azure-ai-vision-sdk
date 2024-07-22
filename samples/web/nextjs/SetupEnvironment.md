# Set up the environment 

The SDK is conveniently available as an NPM package. It leverages the power of [Web Components](https://www.webcomponents.org/introduction) APIs, ensuring effortless integration and usage. It is fully compatible with bundlers such as Webpack. 

1. If you have a valid Azure subscription that has been provisioned for Face API Liveness Detection, you can get the access token to access the release artifacts. More details can be found in [GET_FACE_ARTIFACTS_ACCESS](../../../GET_FACE_ARTIFACTS_ACCESS.md).

2. Prepare NPM authentication
Create .npmrc file with following content in the root of the app folder.
```
registry=https://pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/registry/
always-auth=true
; begin auth token
//pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/registry/:username=msface
//pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/registry/:_password=[base64-access-token]
//pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/registry/:email=[email]
//pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/:username=msface
//pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/:_password=[base64-access-token]
//pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/:email=[email]
; end auth token
```
Please replace the password to the Base64 encoded access-token and email to your email.
To obtain a Base64 token from access-token, use following command (you just need to run the command as is, and it will prompt you to enter the token on the command line):
```sh
node -e "require('readline') .createInterface({input:process.stdin,output:process.stdout,historySize:0}) .question('access-token> ',p => { b64=Buffer.from(p.trim()).toString('base64');console.log(b64);process.exit(); })"
```

3. NPM Package

To install the SDK via NPM, run the following command in the root of the app folder:

```sh
npm install azure-ai-vision-faceanalyzer
```