"use client";

// Step 1: Import the web component.
import "azure-ai-vision-faceanalyzer";
import type * as AzureAIVision from "azure-ai-vision-faceanalyzer";

// Result page static assets
const checkmarkCircleIcon = "CheckmarkCircle.png";
const dismissCircleIcon = "DismissCircle.png";

import React, { useEffect, useRef, useState } from "react";

import { fetchTokenFromAPI } from "./utils";

// Liveness operation mode as a prop based on the button that is pressed, for more information: https://aka.ms/face-api-reference-livenessoperationmode
type FaceAnalyzerProps = {
  livenessOperationMode: "Passive" | "PassiveActive";
  file?: File;
  setFaceAnalyzerResult: (res: AzureAIVision.FaceAnalyzedResult | null) => void;
  fetchFailureCallback?: (error: string) => void;
  setLivenessIcon?: (img: string) => void;
  setRecognitionIcon?: (img: string) => void;
  setLivenessText?: (text: string) => void;
  setRecognitionText?: (text: string) => void;
};

const FaceAnalyzerComponent = ({
  livenessOperationMode,
  file,
  setFaceAnalyzerResult,
  fetchFailureCallback,
  setLivenessIcon,
  setRecognitionIcon,
  setLivenessText,
  setRecognitionText,
}: FaceAnalyzerProps) => {
  // React Hooks
  const [token, setToken] = useState<string | null>(null);
  const [loadingToken, setLoadingToken] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const containerRef = useRef<HTMLDivElement>(null);

  // Step 2: Obtain session-authorization-token.
  // Disclaimer: This is just an illustration of what information is used to create a liveness session using a mocked backend. For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
  // In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend.

  // By setting the sendResultsToClient to true, you should be able to view the final results in the frontend, else you will get a LivenessStatus of CompletedResultQueryableFromService
  const sendResultsToClient = true;

  useEffect(() => {
    // Asynchronously fetch access token from server
    fetchTokenFromAPI(
      livenessOperationMode,
      sendResultsToClient,
      file,
      setToken,
      setLoadingToken,
      setErrorMessage
    );
  }, []);

  useEffect(() => {
    if (!token && !loadingToken && fetchFailureCallback) {
      fetchFailureCallback(errorMessage);
    }
  }, [token, loadingToken]);

  useEffect(() => {
    // Step 3: query the azure-ai-vision-faceanalyzer element to process face liveness.
    // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
    // An example would be to retry in case of a failure.
    var azureAIVisionFaceAnalyzer = document.querySelector(
      "azure-ai-vision-faceanalyzer"
    ) as AzureAIVision.AzureAIFaceLiveness;

    if (token != null) {
      // Step 4: Create the faceanalyzer element, set the token and upgrade the element.
      if (azureAIVisionFaceAnalyzer == null) {
        azureAIVisionFaceAnalyzer = document.createElement(
          "azure-ai-vision-faceanalyzer"
        ) as AzureAIVision.AzureAIFaceLiveness;
        customElements.upgrade(azureAIVisionFaceAnalyzer);
        azureAIVisionFaceAnalyzer.token = token;

        // For multi-camera scenarios, you can set desired deviceId by using following APIs
        // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method
        // Once you have list of camera devices you must call azureAIVisionFaceAnalyzer.filterToSupportedMediaInfoDevices() to get list of devices supported by AzureAIVisionFaceAnalyzer
        // You can then set the desired deviceId as an attribute azureAIVisionFaceAnalyzer.mediaInfoDeviceId = <desired-device-id>

        // Step 5: Handle analysis complete event
        // Note: For added security, you are not required to trust the 'status' property from client.
        // Your backend can and should verify this by querying about the session Face API directly.
        azureAIVisionFaceAnalyzer.addEventListener("analyzed", (event: any) => {
          // The event.result.livenessResult contains the result of the analysis.
          // The result contains the following properties:
          // - status: The result of the liveness detection.
          // - failureReason: The reason for the failure if the status is LivenessStatus.Failed.
          var faceAnalyzerResult =
            event.result as AzureAIVision.FaceAnalyzedResult;
          setFaceAnalyzerResult(faceAnalyzerResult);
          if (faceAnalyzerResult) {
            // Set Liveness Status Results
            const livenessStatus =
              azureAIVisionFaceAnalyzer.LivenessStatus[
                faceAnalyzerResult.livenessResult.status
              ];
            const livenessStatusCondition = livenessStatus == "Live";
            const livenessIcon = livenessStatusCondition
              ? checkmarkCircleIcon
              : dismissCircleIcon;

            let livenessText = null;
            if (livenessStatus == "Live") {
              livenessText = "Live Person";
            } else if (livenessStatus == "Spoof") {
              livenessText = "Spoof";
            } else if (
              livenessStatus == "CompletedResultQueryableFromService"
            ) {
              livenessText = "CompletedResultQueryableFromService";
            } else {
              livenessText =
                azureAIVisionFaceAnalyzer.LivenessFailureReason[
                  faceAnalyzerResult.livenessResult.failureReason
                ];
            }
            setLivenessIcon && setLivenessIcon(livenessIcon);
            setLivenessText && setLivenessText(livenessText);

            // Set Recognition Status Results (if applicable)
            if (faceAnalyzerResult.recognitionResult.status > 0) {
              const recognitionStatus =
                azureAIVisionFaceAnalyzer.RecognitionStatus[
                  faceAnalyzerResult.recognitionResult.status
                ];
              const recognitionStatusCondition = recognitionStatus == "Recognized";
              const recognitionIcon = recognitionStatusCondition
                ? checkmarkCircleIcon
                : dismissCircleIcon;

              let recognitionText = null;
              if (recognitionStatus == "Recognized") {
                recognitionText = "Same Person";
              } else if (recognitionStatus == "NotRecognized") {
                recognitionText = "Not the same person";
              } else if (
                recognitionStatus == "CompletedResultQueryableFromService"
              ) {
                recognitionText = "CompletedResultQueryableFromService";
              } else {
                recognitionText =
                  azureAIVisionFaceAnalyzer.RecognitionFailureReason[
                    faceAnalyzerResult.recognitionResult.failureReason
                  ];
              }

              setRecognitionIcon && setRecognitionIcon(recognitionIcon);
              setRecognitionText && setRecognitionText(recognitionText);
            }
          }
          // For scenario that requires face verification, the event.result.recognitionResult contains the result of the face verification.
        });

        // Step 6: Add the element to the DOM.
        if (containerRef.current) {
          containerRef.current.appendChild(azureAIVisionFaceAnalyzer);
        }
      } else {
        // Step 7: In order to retry the session, in case of failure, you can use analyzeOnceAgain().
        // Make sure to set a new token for the next session.
        // For multi-camera scenarios, you need to set the deviceId again by following the aforementioned procedure
        // in Step 4
        azureAIVisionFaceAnalyzer.token = token;
        azureAIVisionFaceAnalyzer.analyzeOnceAgain();
      }
    }

    // Step 8: Cleanup on component unmount
    return () => {
      if (
        azureAIVisionFaceAnalyzer &&
        containerRef.current &&
        containerRef.current.contains(azureAIVisionFaceAnalyzer)
      ) {
        containerRef.current.removeChild(azureAIVisionFaceAnalyzer);
      }
    };
  }, [token]);

  return (
    <div style={{ padding: "0 20px", fontSize: "14px" }}>
      {/* Container in which the faceanalyzer will be injected */}
      <div id="container" ref={containerRef}></div>
    </div>
  );
};

export default FaceAnalyzerComponent;
