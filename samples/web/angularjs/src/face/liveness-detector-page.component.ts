/* NOTE: This is an example of how to integrate the Face Liveness Detector Web Component with AngularJS. */

// Step 1: Import the web component.
import 'azure-ai-vision-face-ui';
import { FaceLivenessDetector, LivenessDetectionSuccess, LivenessDetectionError, LivenessStatus, RecognitionStatus } from 'azure-ai-vision-face-ui';

import {
  CUSTOM_ELEMENTS_SCHEMA,
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { LivenessOperationMode, fetchTokenFromAPI } from '../app/utils';

@Component({
  selector: 'liveness-detector-page',
  standalone: true,
  imports: [],
  templateUrl: './liveness-detector-page.component.html',
  styleUrl: './liveness-detector-page.component.css',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class FaceLivenessDetectorComponent implements OnInit, OnDestroy {
  @Input('livenessOperationMode') livenessOperationMode: LivenessOperationMode =
    'Passive'; // for more information: https://aka.ms/face-api-reference-livenessoperationmode
  @Input('action') action: string = 'detectLiveness';
  @Input('sendResultsToClient') sendResultsToClient: boolean = true; // By setting the sendResultsToClient to true, you should be able to view the final results in the frontend, else you will get a LivenessStatus of CompletedResultQueryableFromService

  @Input('file') file: File | undefined;

  @Output() retryFaceLivenessDetection: EventEmitter<string> = new EventEmitter();
  @Output() displayResult: EventEmitter<{
    recognitionCondition: boolean | undefined;
    recognitionText: string | undefined;
    livenessCondition: boolean;
    livenessText: string;
  }> = new EventEmitter();

  faceLivenessDetector: FaceLivenessDetector | null = null;
  faceLivenessDetectionResult: LivenessDetectionSuccess | undefined;

  // Step 3: Obtain session-authorization-token.
  // Disclaimer: This is just an illustration of what information is used to create a liveness session using a mocked backend. For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
  // In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend.
  // Note1: More information regarding each request parameter involved in creating a liveness session is here: https://aka.ms/face-api-reference-createlivenesssession
  async ngOnInit(): Promise<void> {
    await fetchTokenFromAPI(
      this.livenessOperationMode,
      this.sendResultsToClient,
      this.file,
      this.startFaceLiveness.bind(this),
      undefined,
      this.retryFaceLivenessDetection.emit.bind(this.retryFaceLivenessDetection)
    );
  }

  ngOnDestroy(): void {
    
  }

  // This method shows how to start face liveness check.
  async startFaceLiveness(authToken: string, action: string) {
    // Step 2: query the azure-ai-vision-face-ui element to process face liveness.
    // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
    // An example would be to retry in case of a failure.
    this.faceLivenessDetector = document.querySelector(
      'azure-ai-vision-face-ui'
    );

    if (this.faceLivenessDetector == null) {
      // Step 3: Create the face liveness detector element and attach it to DOM.
      this.faceLivenessDetector = document.createElement(
        'azure-ai-vision-face-ui'
      ) as FaceLivenessDetector;
      document
        .getElementById('container')
        ?.appendChild(this.faceLivenessDetector);
    }

    // For multi-camera scenarios, you can set desired deviceId by using following APIs:
    // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method.
    // You can then set the desired deviceId as an attribute faceLivenessDetector.mediaInfoDeviceId = <desired-device-id>

    // Step 5: Start the face liveness check session and handle the promise returned appropriately.
    // Note: For added security, you are not required to trust the 'status' property from client.
    // Your backend can and should verify this by querying about the session Face API directly.
    this.faceLivenessDetector.start(authToken)
    .then (resultData => {
        // Once the session is completed and promise fulfilled, the resultData contains the result of the analysis.
        // - livenessStatus: The result of the liveness detection.
        var livenessDetectionResult = resultData as LivenessDetectionSuccess;
        this.faceLivenessDetectionResult = livenessDetectionResult;
        // Set Liveness Status Results
        const livenessStatus = livenessDetectionResult.livenessStatus;
        const livenessCondition = livenessStatus == LivenessStatus.RealFace;

        let livenessText = "";
        if (livenessStatus == LivenessStatus.RealFace) {
          livenessText = 'Real Person';
        } else if (livenessStatus == LivenessStatus.SpoofFace) {
          livenessText = 'Spoof';
        } else if (livenessStatus == LivenessStatus.ResultQueryableFromService) {
          livenessText = 'ResultQueryableFromService';
        }

        // Set Recognition Status Results (if applicable)
        let recognitionCondition = undefined;
        let recognitionText = undefined;
        if (action === "detectLivenessWithVerify") {
          const recognitionStatus = livenessDetectionResult.recognitionResult.status;
          recognitionCondition = recognitionStatus == RecognitionStatus.Recognized;

          if (recognitionStatus == RecognitionStatus.Recognized) {
            recognitionText = 'Same Person';
          } else if (recognitionStatus == RecognitionStatus.NotRecognized) {
            recognitionText = 'Not the same person';
          } else if (recognitionStatus == RecognitionStatus.ResultQueryableFromService) {
            recognitionText = 'ResultQueryableFromService';
          }
        }

        this.displayResult.emit({
          recognitionCondition,
          recognitionText,
          livenessCondition,
          livenessText,
        });
      }
    )
    .catch (error => {
      const errorData = error as LivenessDetectionError;
      let livenessText = errorData.livenessError as string;
      const livenessCondition = false;
      let recognitionText = undefined;
      let recognitionCondition = undefined;
      if (action == "detectLivenessWithVerify") {
        recognitionText = errorData.recognitionError;
        recognitionCondition = false;
      }

      this.displayResult.emit({
        recognitionCondition,
        recognitionText,
        livenessCondition,
        livenessText,
      });
    });
  }
}
