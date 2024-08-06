/* NOTE: This is an example of how to integrate the Face Analyzer Web Component with AngularJS.
          You can edit the callback function inside the "analyzed" event listener to change how the event is handled. */


// Step 1: Import the web component.
import 'azure-ai-vision-faceanalyzer';
import * as AzureAIVision from 'azure-ai-vision-faceanalyzer';

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
  selector: 'analyzer-page',
  standalone: true,
  imports: [],
  templateUrl: './analyzer-page.component.html',
  styleUrl: './analyzer-page.component.css',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class AnalyzerPageComponent implements OnInit, OnDestroy {
  @Input('livenessOperationMode') livenessOperationMode: LivenessOperationMode =
    'Passive'; // for more information: https://aka.ms/face-api-reference-livenessoperationmode
  @Input('action') action: string = 'detectLiveness';
  @Input('sendResultsToClient') sendResultsToClient: boolean = true; // By setting the sendResultsToClient to true, you should be able to view the final results in the frontend, else you will get a LivenessStatus of CompletedResultQueryableFromService

  @Input('file') file: File | undefined;

  @Output() retryAnalyzer: EventEmitter<string> = new EventEmitter();
  @Output() displayResult: EventEmitter<{
    faceAnalyzedResult: AzureAIVision.FaceAnalyzedResult;
    recognitionCondition: boolean | undefined;
    recognitionText: string | undefined;
    livenessCondition: boolean;
    livenessText: string;
  }> = new EventEmitter();

  azureAIVisionFaceAnalyzer: AzureAIVision.AzureAIFaceLiveness | null = null;
  faceAnalyzedResult: AzureAIVision.FaceAnalyzedResult | undefined;

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
      this.retryAnalyzer.emit.bind(this.retryAnalyzer)
    );
  }

  ngOnDestroy(): void {
    var container = document.getElementById('container');
    if (
      this.azureAIVisionFaceAnalyzer &&
      container?.contains(this.azureAIVisionFaceAnalyzer)
    ) {
      container.removeChild(this.azureAIVisionFaceAnalyzer);
    }
  }

  // This method shows how to start face liveness check.
  async startFaceLiveness(authToken: string) {
    // Step 2: query the azure-ai-vision-faceanalyzer element to process face liveness.
    // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
    // An example would be to retry in case of a failure.
    this.azureAIVisionFaceAnalyzer = document.querySelector(
      'azure-ai-vision-faceanalyzer'
    );

    if (this.azureAIVisionFaceAnalyzer == null) {
      // Step 4: Create the faceanalyzer element, set the token and upgrade the element.
      this.azureAIVisionFaceAnalyzer = document.createElement(
        'azure-ai-vision-faceanalyzer'
      ) as AzureAIVision.AzureAIFaceLiveness;
      customElements.upgrade(this.azureAIVisionFaceAnalyzer);
      this.azureAIVisionFaceAnalyzer.token = authToken;

      // For multi-camera scenarios, you can set desired deviceId by using following APIs
      // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method
      // Once you have list of camera devices you must call azureAIVisionFaceAnalyzer.filterToSupportedMediaInfoDevices() to get list of devices supported by AzureAIVisionFaceAnalyzer
      // You can then set the desired deviceId as an attribute azureAIVisionFaceAnalyzer.mediaInfoDeviceId = <desired-device-id>

      // Step 5: Handle analysis complete event
      // Note: For added security, you are not required to trust the 'status' property from client.
      // Your backend can and should verify this by querying about the session Face API directly.
      this.azureAIVisionFaceAnalyzer.addEventListener(
        'analyzed',
        (event: any) => {
          // console.log(event);
          // The event.result.livenessResult contains the result of the analysis.
          // The result contains the following properties:
          // - status: The result of the liveness detection.
          // - failureReason: The reason for the failure if the status is LivenessStatus.Failed.
          var faceAnalyzedResult =
            event.detail as AzureAIVision.FaceAnalyzedResult;
          this.faceAnalyzedResult = faceAnalyzedResult;
          if (faceAnalyzedResult) {
            // Set Liveness Status Results
            const livenessStatus =
              this.azureAIVisionFaceAnalyzer!.LivenessStatus[
                faceAnalyzedResult.livenessResult.status
              ];
            const livenessCondition = livenessStatus == 'Live';

            let livenessText = null;
            if (livenessStatus == 'Live') {
              livenessText = 'Live Person';
            } else if (livenessStatus == 'Spoof') {
              livenessText = 'Spoof';
            } else if (
              livenessStatus == 'CompletedResultQueryableFromService'
            ) {
              livenessText = 'CompletedResultQueryableFromService';
            } else {
              livenessText =
                this.azureAIVisionFaceAnalyzer!.LivenessFailureReason[
                  faceAnalyzedResult.livenessResult.failureReason
                ];
            }

            // Set Recognition Status Results (if applicable)
            // For scenario that requires face verification, the event.detail.recognitionResult contains the result of the face verification.
            let recognitionCondition;
            let recognitionText;
            if (faceAnalyzedResult.recognitionResult.status > 0) {
              const recognitionStatus =
                this.azureAIVisionFaceAnalyzer!.RecognitionStatus[
                  faceAnalyzedResult.recognitionResult.status
                ];
              recognitionCondition = recognitionStatus == 'Recognized';

              if (recognitionStatus == 'Recognized') {
                recognitionText = 'Same Person';
              } else if (recognitionStatus == 'NotRecognized') {
                recognitionText = 'Not the same person';
              } else if (
                recognitionStatus == 'CompletedResultQueryableFromService'
              ) {
                recognitionText = 'CompletedResultQueryableFromService';
              } else {
                recognitionText =
                  this.azureAIVisionFaceAnalyzer!.RecognitionFailureReason[
                    faceAnalyzedResult.recognitionResult.failureReason
                  ];
              }
            }

            this.displayResult.emit({
              faceAnalyzedResult,
              recognitionCondition,
              recognitionText,
              livenessCondition,
              livenessText,
            });
          }
        }
      );
      // Step 6: Add the element to the DOM.
      document
        .getElementById('container')
        ?.appendChild(this.azureAIVisionFaceAnalyzer);
    } else {
      // Step 7: In order to retry the session, in case of failure, you can use analyzeOnceAgain().
      // Make sure to set a new token for the next session.
      // For multi-camera scenarios, you need to set the deviceId again by following the aforementioned procedure
      // in Step 4
      this.azureAIVisionFaceAnalyzer.token = authToken;
      await this.azureAIVisionFaceAnalyzer.analyzeOnceAgain();
    }
  }
}
