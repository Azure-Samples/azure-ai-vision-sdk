# Multi-stage build for the .NET 10 Face liveness attestation backend.
# Build from the repository ROOT so the referenced package project is in context:
#   docker build -f public_samples/vision/backend_samples/backend/dotnet/Dockerfile -t face-liveness-attestation .
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# The web app ProjectReferences the attestation package, so both trees are needed.
COPY public_samples/vision/backend_samples/library/dotnet/AzureAIVisionFaceDeviceAttestation public_samples/vision/backend_samples/library/dotnet/AzureAIVisionFaceDeviceAttestation
COPY public_samples/vision/backend_samples/backend/dotnet public_samples/vision/backend_samples/backend/dotnet

RUN dotnet publish public_samples/vision/backend_samples/backend/dotnet/FaceLivenessAttestationBackendSample.csproj -c Release -o /app

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app
COPY --from=build /app .
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080
ENTRYPOINT ["dotnet", "FaceLivenessAttestationBackendSample.dll"]
