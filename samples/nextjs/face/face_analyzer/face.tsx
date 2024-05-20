"use client";

// Step 1: Import the web component.
import "azure-ai-vision-faceanalyzer";

// Result page static assets
import checkmarkCircleIcon from "./CheckmarkCircle.png";
import dismissCircleIcon from "./DismissCircle.png";
import heartPulseIcon from "./HeartPulse.png";

import React, { useEffect, useRef, useState, useMemo } from "react";
import Image from "next/image";
import styles from "./styles.module.css";

import { fetchToken } from "./utils";

const FaceAnalyzerComponent = () => {
  // React Hooks
  const [token, setToken] = useState<string | null>(null);
  const [detectionResult, setDetectionResult] = useState<any>(undefined);
  const [showDetection, setShowDetection] = useState(false);
  const containerRef = useRef(null) as any;
  const [livenessIcon, setLivenessIcon] = useState(checkmarkCircleIcon);
  const [livenessText, setLivenessText] = useState<string>("Live Person");

  // Step 2: Obtain session-authorization-token.
  // Disclaimer: This is just an illustration of what information is used to create a liveness session using a mocked backend. For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
  // In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend.

  // The liveness-operation-mode is retrieved from 'data-mode' attribute of the start buttons, for more information: https://aka.ms/face-api-reference-livenessoperationmode
  const livenessOperationMode = "PassiveActive";
  // By setting the sendResultsToClient to true, you should be able to view the final results in the frontend, else you will get a LivenessStatus of CompletedResultQueryableFromService
  const sendResultsToClient = true;

  // Note1: More information regarding each request parameter involved in creating a liveness session is here: https://aka.ms/face-api-reference-createlivenesssession
  // Note2: You can also create a liveness session with verification by attaching a verify image during session-create, reference: https://aka.ms/face-api-reference-createlivenesswithverifysession
  useEffect(() => {
    fetchToken(
      "<end-point>",
      "<key>",
      livenessOperationMode,
      sendResultsToClient,
      setToken
    );
  }, []);

  useEffect(() => {
    // Step 3: query the azure-ai-vision-faceanalyzer element to process face liveness.
    // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
    // An example would be to retry in case of a failure.
    var azureAIVisionFaceAnalyzer = document.querySelector(
      "azure-ai-vision-faceanalyzer"
    );

    if (token != null) {
      // Step 4: Create the faceanalyzer element, set the token and upgrade the element.
      if (azureAIVisionFaceAnalyzer == null) {
        azureAIVisionFaceAnalyzer = document.createElement(
          "azure-ai-vision-faceanalyzer"
        );
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
          setDetectionResult(event.result);
          setShowDetection(false);

          const livenessStatus =
            azureAIVisionFaceAnalyzer.LivenessStatus[
              event.result.livenessResult.status
            ];
          const livenessCondition = livenessStatus == "Live";
          const livenessIcon = livenessCondition
            ? checkmarkCircleIcon
            : dismissCircleIcon;

          let livenessText = null;
          if (livenessStatus == "Live") {
            livenessText = "Live Person";
          } else if (livenessStatus == "Spoof") {
            livenessText = "Spoof";
          } else if (livenessStatus == "CompletedResultQueryableFromService") {
            livenessText = "CompletedResultQueryableFromService";
          } else {
            livenessText =
              azureAIVisionFaceAnalyzer.LivenessFailureReason[
                event.result.livenessResult.failureReason
              ];
          }

          setLivenessIcon(livenessIcon);
          setLivenessText(livenessText);

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

  useEffect(() => {
    if (detectionResult) {
    }
  }, [detectionResult]);

  const ResultView = () => {
    return (
      <>
        <div className={styles.feedback}>
          <div className={styles.item}>
            <Image src={heartPulseIcon} alt="Liveness Icon" />
            <span>Liveness</span>
          </div>
          <div className={styles.item}>
            <Image src={livenessIcon} alt="Liveness Status Icon" />
            <span>{livenessText}</span>
          </div>
        </div>
      </>
    );
  };

  return (
    <div style={{ padding: "0 20px", fontSize: "14px" }}>
      <div
        style={{
          display: "flex",
          placeContent: "center",
          flexDirection: "column",
          alignItems: "center",
        }}
      >
        {!showDetection && <>{detectionResult && <ResultView />}</>}
      </div>
      <div id="container" ref={containerRef}></div>
    </div>
  );
};

export default FaceAnalyzerComponent;
