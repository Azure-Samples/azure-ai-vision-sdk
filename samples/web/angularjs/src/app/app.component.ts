/* NOTE: This is an EXAMPLE app page for controlling the state of the app. This is not required to use the web component.
          For integration example, please see face-angularjs/src/face/liveness-detector-page.component.ts */

import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LivenessOperationMode } from './utils';
import { NgIf } from '@angular/common';

// Components for different views based on state of the face liveness detector
import { InitialComponent } from './initial-page/initial-page.component';
import { FaceLivenessDetectorComponent } from '../face/liveness-detector-page.component';
import { ResultPageComponent } from './result-page/result-page.component';
import { RetryPageComponent } from './retry-page/retry-page.component';

type LivenessDetectorState = 'Initial' | 'LivenessDetector' | 'Result' | 'Retry';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    InitialComponent,
    FaceLivenessDetectorComponent,
    ResultPageComponent,
    RetryPageComponent,
    NgIf,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  title = 'face-angular';

  livenessDetectorState: LivenessDetectorState = 'Initial';

  // Face liveness detector parameters
  livenessOperationMode: LivenessOperationMode = 'PassiveActive';
  verifyImage: File | undefined;
  action: string = 'detectLiveness';

  // Retry display variables
  errorMessage: string | undefined;

  // Result display variables
  recognitionCondition: boolean | undefined;
  recognitionText: string | undefined;
  livenessCondition: boolean = false;
  livenessText: string = '';

  // View toggle
  pageHidden: { [key: string]: boolean } = {
    Initial: true,
    LivenessDetector: false,
    Result: false,
    Retry: false,
  };

  setLivenessDetectorState(s: LivenessDetectorState) {
    if (this.livenessDetectorState === s) {
      return;
    }
    this.pageHidden[this.livenessDetectorState] = false;
    this.livenessDetectorState = s;
    this.pageHidden[s] = true;
  }

  initFaceLivenessDetector(event: {
    livenessOperationMode: LivenessOperationMode;
    file: File | undefined;
  }) {
    this.verifyImage = event.file;
    this.action = event.file ? 'detectLivenessWithVerify' : 'detectLiveness';
    this.livenessOperationMode = event.livenessOperationMode;
    this.setLivenessDetectorState('LivenessDetector');
  }

  continueLivenessDetection() {
    this.setLivenessDetectorState('Initial');
  }

  displayResult(event: {
    recognitionCondition: boolean | undefined;
    recognitionText: string | undefined;
    livenessCondition: boolean;
    livenessText: string;
  }) {
    this.recognitionCondition = event.recognitionCondition;
    this.recognitionText = event.recognitionText;
    this.livenessCondition = event.livenessCondition;
    this.livenessText = event.livenessText;
    this.setLivenessDetectorState('Result');
  }

  fetchFailureCallback(error: string) {
    this.errorMessage = `Failed to fetch the token. ${error}`;
    this.setLivenessDetectorState('Retry');
  }
}
