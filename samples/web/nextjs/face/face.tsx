"use client";

// Step 1: Import the web component.
import "azure-ai-vision-face-ui";
import { FaceLivenessDetector, LivenessDetectionSuccess, LivenessDetectionError, LivenessStatus, RecognitionStatus } from 'azure-ai-vision-face-ui';

// Result page static assets
const checkmarkCircleIcon = "CheckmarkCircle.png";
const dismissCircleIcon = "DismissCircle.png";

import React, { useEffect, useRef, useState } from "react";

import { fetchTokenFromAPI } from "./utils";

// Liveness operation mode as a prop based on the button that is pressed, for more information: https://aka.ms/face-api-reference-livenessoperationmode
type FaceLivenessDetectorProps = {
  livenessOperationMode: "Passive" | "PassiveActive";
  file?: File;
  setIsDetectLivenessWithVerify: (res: Boolean) => void;
  fetchFailureCallback?: (error: string) => void;
  setLivenessIcon?: (img: string) => void;
  setRecognitionIcon?: (img: string) => void;
  setLivenessText?: (text: string) => void;
  setRecognitionText?: (text: string) => void;
};

const FaceLivenessDetectorComponent = ({
  livenessOperationMode,
  file,
  setIsDetectLivenessWithVerify,
  fetchFailureCallback,
  setLivenessIcon,
  setRecognitionIcon,
  setLivenessText,
  setRecognitionText,
}: FaceLivenessDetectorProps) => {
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
    let action = (file !== undefined) ? "detectLivenessWithVerify": "detectLiveness";
    // Step 3: query the azure-ai-vision-face-ui element to process face liveness.
    // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
    // An example would be to retry in case of a failure.
    var faceLivenessDetector = document.querySelector(
      "azure-ai-vision-face-ui"
    ) as FaceLivenessDetector;

    // Step 4: Create the FaceLivenessDetector element and attach it to DOM.
    if (faceLivenessDetector == null) {
      faceLivenessDetector = document.createElement(
        "azure-ai-vision-face-ui"
      ) as FaceLivenessDetector;
      containerRef.current.appendChild(faceLivenessDetector);
    }

    // For multi-camera scenarios, you can set desired deviceId by using following APIs
    // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method
    // You can then set the desired deviceId as an attribute faceLivenessDetector.mediaInfoDeviceId = <desired-device-id>

    // Step 5: Start the face liveness check session and handle the promise returned appropriately.
    // Note: For added security, you are not required to trust the 'status' property from client.
    // Your backend can and should verify this by querying about the session Face API directly.
    faceLivenessDetector.start(token as string)
    .then (resultData => {
      // Once the session is completed and promise fulfilled, the resultData contains the result of the analysis.
      // - livenessStatus: The result of the liveness detection.
      var faceLivenessDetectorResult = resultData as LivenessDetectionSuccess;
      setIsDetectLivenessWithVerify(action == "detectLivenessWithVerify");
      const livenessStatus = faceLivenessDetectorResult.livenessStatus;
      const livenessStatusCondition = livenessStatus == LivenessStatus.RealFace;
      const livenessIcon = livenessStatusCondition
        ? checkmarkCircleIcon
        : dismissCircleIcon;

      let livenessText = "";
      if (livenessStatus == LivenessStatus.RealFace) {
        livenessText = "Real Person";
      } else if (livenessStatus == LivenessStatus.SpoofFace) {
        livenessText = "Spoof Person";
      } else if (livenessStatus == LivenessStatus.ResultQueryableFromService) {
        livenessText = "ResultQueryableFromService";
      }
      setLivenessIcon && setLivenessIcon(livenessIcon);
      setLivenessText && setLivenessText(livenessText);

      if (action == "detectLivenessWithVerify") {
        const recognitionStatus = faceLivenessDetectorResult.recognitionResult.status;
        const recognitionStatusCondition = recognitionStatus == RecognitionStatus.Recognized;
        const recognitionIcon = recognitionStatusCondition
          ? checkmarkCircleIcon
          : dismissCircleIcon;

        let recognitionText = "";
        if (recognitionStatus == RecognitionStatus.Recognized) {
          recognitionText = "Same Person";
        } else if (recognitionStatus == RecognitionStatus.NotRecognized) {
          recognitionText = "Not the same person";
        } else if (recognitionStatus == RecognitionStatus.ResultQueryableFromService) {
          recognitionText = "ResultQueryableFromService";
        }

        setRecognitionIcon && setRecognitionIcon(recognitionIcon);
        setRecognitionText && setRecognitionText(recognitionText);
      }
    })
    .catch(error => {
      const errorData = error as LivenessDetectionError;
      let livenessText = errorData.livenessError as string;
      const livenessIcon = dismissCircleIcon;
      setLivenessIcon && setLivenessIcon(livenessIcon);
      setLivenessText && setLivenessText(livenessText);

      if (action == "detectLivenessWithVerify") {
        let recognitionText = errorData.recognitionError;
        const recognitionIcon = dismissCircleIcon;
        setRecognitionIcon && setRecognitionIcon(recognitionIcon);
        setRecognitionText && setRecognitionText(recognitionText);
      }
    });
    
    // Step 8: Cleanup on component unmount
    return () => {
      if (
        faceLivenessDetector &&
        containerRef.current &&
        containerRef.current.contains(faceLivenessDetector)
      ) {
        containerRef.current.removeChild(faceLivenessDetector);
      }
    };
  }, [token]);

  return (
    <div style={{ padding: "0 20px", fontSize: "14px" }}>
      {/* Container in which the FaceLivenessDetector will be injected */}
      <div id="container" ref={containerRef}></div>
    </div>
  );
};

export default FaceLivenessDetectorComponent;
