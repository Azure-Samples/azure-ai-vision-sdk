"use client";

import Image from "next/image";
import { ChangeEvent, useState } from "react";
import dynamic from "next/dynamic";

// Result View static assets
const checkmarkCircleIcon = "CheckmarkCircle.png";
const heartPulseIcon = "HeartPulse.png";
const personIcon = "Person.png";

const FaceLivenessDetectorComponent = dynamic(() => import("@/face/face"), {
  ssr: false,
});

type LivenessOperationMode = "Passive" | "PassiveActive";
type LivenessDetectorState = 'Initial' | 'LivenessDetector' | 'Result' | 'Retry';

const buttonStyle =
  "relative text-white bg-[#036ac4] hover:bg-[#0473ce] flex grow-1 px-2.5 py-1.5 rounded-md text-sm md:text-[1.1rem]";

const imageButtonStyle = 
  "relative text-white bg-[#838383] hover:bg-[#9A9A9A] flex grow-1 px-2.5 py-1.5 rounded-md text-sm md:text-[1.1rem] cursor-pointer"

// These components are separated from the original page.tsx because
// Next.js App Directory separately renders static and interactive components as server and client components.
// Learn more: https://nextjs.org/docs/app/building-your-application/rendering
export default function FaceLivenessDetectorSampleClient() {
  const [verifyImage, setVerifyImage] = useState<File>();
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [livenessOperationMode, setLivenessOperationMode] =
    useState<LivenessOperationMode>("PassiveActive");
  const [livenessDetectorState, setLivenessDetectorState] = useState<LivenessDetectorState>("Initial");
  const [isDetectLivenessWithVerify, setIsDetectLivenessWithVerify] =
    useState<Boolean>(false);
  const [livenessIcon, setLivenessIcon] = useState<string>(checkmarkCircleIcon);
  const [livenessText, setLivenessText] = useState<string>("Real Person");
  const [recognitionIcon, setRecognitionIcon] = useState<string>(checkmarkCircleIcon);
  const [recognitionText, setRecognitionText] = useState<string>("Same Person");

  function handleFile(e: ChangeEvent<HTMLInputElement>) {
    if (e.target.files) {
      setVerifyImage(e.target.files[0]);
    } else {
      setVerifyImage(undefined);
    }
  }

  function initFaceLivenessDetector(livenessOperation: LivenessOperationMode) {
    setLivenessOperationMode(livenessOperation);
    setLivenessDetectorState("LivenessDetector"); 
  }

  function continueFaceLivenessDetector() {
    setLivenessDetectorState("Initial");
    setVerifyImage(undefined);
  }

  function displayResult(isDetectLivenessWithVerify: Boolean) {
    setIsDetectLivenessWithVerify(isDetectLivenessWithVerify);
    setLivenessDetectorState("Result");
  }

  function fetchFailureCallback(error: string) {
    setErrorMessage(error);
    setLivenessDetectorState("Retry");
  }

  return (
    <>
      {livenessDetectorState === "Initial" && (
        <InitialView
          verifyImage={verifyImage}
          handleFile={handleFile}
          initFaceLivenessDetector={initFaceLivenessDetector}
        />
      )}
      {livenessDetectorState === "LivenessDetector" && (
        <FaceLivenessDetectorComponent
          livenessOperationMode={livenessOperationMode}
          file={verifyImage}
          setIsDetectLivenessWithVerify={displayResult}
          fetchFailureCallback={fetchFailureCallback}
          setLivenessIcon={setLivenessIcon}
          setLivenessText={setLivenessText}
          setRecognitionIcon={setRecognitionIcon}
          setRecognitionText={setRecognitionText}
        />
      )}
      {livenessDetectorState === "Result" && (
        <ResultView
          livenessIcon={livenessIcon}
          livenessText={livenessText}
          recognitionIcon={recognitionIcon}
          recognitionText={recognitionText}
          continueFunction={continueFaceLivenessDetector}
          isDetectLivenessWithVerify={isDetectLivenessWithVerify}
        />
      )}
      {/* Retry (in cases of failure) for token retrieval */}
      {livenessDetectorState === "Retry" && (
        <RetryView
          errorMessage={errorMessage}
          retryFunction={continueFaceLivenessDetector}
        />
      )}
    </>
  );
}

// This component contains the starting view of the web app
type InitialViewProps = {
  verifyImage: File | undefined;
  handleFile: (e: ChangeEvent<HTMLInputElement>) => void;
  initFaceLivenessDetector: (s: LivenessOperationMode) => void;
};
const InitialView = ({
  verifyImage,
  handleFile,
  initFaceLivenessDetector,
}: InitialViewProps) => {
  return (
    <>
      <iframe
        id="splash"
        title="splash"
        src="splash.html"
        role="status"
        className="flex-[1_1_80vh] min-h-[50vh] max-h-[70vh] max-h-[70dvh] w-full border-none z-50 bg-white relative"
      ></iframe>
      <div className="flex-[0_1_20vh] flex gap-y-4 md:flex-row flex-col justify-center mb-[2vh] pb-[2vh] items-center min-h-fit text-2xl md:gap-y-0 md:gap-x-4 max-sm:text-base">
        {/* Upload component for verifying person with detectLivenessWithVerify */}
        <label
          className={imageButtonStyle}
        >
          <input
          onChange={handleFile}
          type="file"
          accept="image/*"
          id="useVerifyImageFileInput"
          className="hidden"
        />
          Select Verify Image
        </label>        
      </div>
      {verifyImage && (
        <Image
          className="mx-auto mb-[2vh] pb-[2vh]"
          src={URL.createObjectURL(verifyImage)}
          alt="uploaded image"
          width={100}
          height={100}
        />
      )}
      <div className="flex-[0_1_20vh] flex items-center flex-row gap-x-4 mb-[2vh] pb-[2vh] min-h-fit justify-center">
      <button
          type="button"
          onClick={() => initFaceLivenessDetector("Passive")}
          className={buttonStyle}
        >
          Start Passive
        </button>
        <button
          type="button"
          onClick={() => initFaceLivenessDetector("PassiveActive")}
          className={buttonStyle}
        >
          Start PassiveActive
        </button>
      </div>
    </>
  );
};

// This component displays the results of the detection
type ResultViewProps = {
  isDetectLivenessWithVerify: Boolean;
  livenessIcon: string;
  livenessText: string;
  recognitionIcon: string;
  recognitionText: string;
  continueFunction?: () => void;
};
const ResultView = ({
  isDetectLivenessWithVerify,
  livenessIcon,
  livenessText,
  recognitionIcon,
  recognitionText,
  continueFunction,
}: ResultViewProps) => {
  return (
    <div className="flex flex-col h-screen justify-start items-center py-24 gap-y-24 text-xl md:text-3xl">
      <div className="flex flex-col justify-start items-center gap-y-4">
        <div className="flex flex-row items-center gap-x-2">
          <img src={heartPulseIcon} alt="Liveness Icon" />
          <span>Liveness</span>
        </div>
        <div className="flex flex-row items-center gap-x-2">
          <img src={livenessIcon} alt="Liveness Status Icon" />
          <span>{livenessText}</span>
        </div>

        {isDetectLivenessWithVerify && (
            <>
              <div className="w-40 h-0 border border-transparent border-t-gray-500" />
              <div className="flex flex-row items-center gap-x-2">
                <img src={personIcon} alt="Verification Icon" />
                <span>Verification</span>
              </div>
              <div className="flex flex-row items-center gap-x-2">
                <img src={recognitionIcon} alt="Recognition Status Icon" />
                <span>{recognitionText}</span>
              </div>
            </>
          )}
      </div>
      {continueFunction !== undefined && (
        <div>
          <button onClick={continueFunction} className={buttonStyle}>
            Continue
          </button>
        </div>
      )}
    </div>
  );
};

// This component displays an error if the access token generation process fails
type RetryViewProps = {
  errorMessage: string;
  retryFunction?: () => void;
};
const RetryView = ({ errorMessage, retryFunction }: RetryViewProps) => {
  return (
    <div className="flex flex-col h-screen justify-start items-center py-24 gap-y-24 text-lg md:text-2xl">
      <p className="text-center w-[80%] text-wrap">{errorMessage}</p>
      {retryFunction !== undefined && (
        <div>
          <button onClick={retryFunction} className={buttonStyle}>
            Retry
          </button>
        </div>
      )}
    </div>
  );
};
