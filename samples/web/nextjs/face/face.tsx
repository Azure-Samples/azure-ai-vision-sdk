"use client";

// Step 1: Import the web component.
import "@azure/ai-vision-face-ui";
import { FaceLivenessDetector, LivenessDetectionError } from '@azure/ai-vision-face-ui';

// Result page static assets
const checkmarkCircleIcon = "CheckmarkCircle.png";
const dismissCircleIcon = "DismissCircle.png";

import React, { useEffect, useRef, useState } from "react";

import { SessionAuthorizationData, fetchTokenFromAPI, fetchSessionResultFromAPI } from "./utils";

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
  const [sessionData, setSessionData] = useState<SessionAuthorizationData | null>(null);
  const [loadingToken, setLoadingToken] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const containerRef = useRef<HTMLDivElement>(null);

  // Step 2: Obtain session-authorization-token.
  // Disclaimer: This is just an illustration of what information is used to create a liveness session using a mocked backend. For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
  // In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend.

  useEffect(() => {
    // Asynchronously fetch access token from server
    fetchTokenFromAPI(
      livenessOperationMode,
      file,
      setSessionData,
      setLoadingToken,
      setErrorMessage
    );
  }, []);

  useEffect(() => {
    if (!sessionData && !loadingToken && fetchFailureCallback) {
      fetchFailureCallback(errorMessage);
    }
  }, [sessionData, loadingToken]);

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
    faceLivenessDetector.start(sessionData?.authToken as string)
    .then (async resultData => {

      console.log(resultData);
      setIsDetectLivenessWithVerify(action == "detectLivenessWithVerify");
      // Once the session is completed and promise fulfilled, the client does not receive the outcome whether face is live or spoof.
      // You can query the result from your backend service by calling the sessions results API
      // https://aka.ms/face/liveness-session/get-liveness-session-result
      var sessionResult = await fetchSessionResultFromAPI(action, sessionData?.sessionId as string);
    
      // Set Liveness Status Results
      const livenessStatus = sessionResult.results.attempts[0].result.livenessDecision;
      const livenessStatusCondition = livenessStatus == "realface";
      const livenessIcon = livenessStatusCondition
        ? checkmarkCircleIcon
        : dismissCircleIcon;

      let livenessText = "";
      if (livenessStatusCondition) {
        livenessText = 'Real Person';
      } else {
        livenessText = 'Spoof';
      }

      setLivenessIcon && setLivenessIcon(livenessIcon);
      setLivenessText && setLivenessText(livenessText);
  
      // Set Recognition Status Results (if applicable)
      if (action === "detectLivenessWithVerify") {
        const recognitionStatusCondition = sessionResult.results.attempts[0].result.verifyResult.isIdentical;
        const recognitionIcon = recognitionStatusCondition
          ? checkmarkCircleIcon
          : dismissCircleIcon;
        let recognitionText = "";
        if (recognitionStatusCondition) {
          recognitionText = 'Same Person';
        } else {
          recognitionText = 'Not the same person';
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
  }, [sessionData]);

  return (
    <div style={{ padding: "0 20px", fontSize: "14px" }}>
      {/* Container in which the FaceLivenessDetector will be injected */}
      <div id="container" ref={containerRef}></div>
    </div>
  );
};

export default FaceLivenessDetectorComponent;
