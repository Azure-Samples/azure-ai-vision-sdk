/* NOTE: This is an EXAMPLE app page for controlling the state of the app. This is not required to use the web component.
          For integration example, please see face-angularjs/src/face_analyzer/analyzer-page.component.ts */

import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LivenessOperationMode } from './utils';
import { NgIf } from '@angular/common';

// Components for different views based on state of the face analyzer
import { InitialComponent } from './initial-page/initial-page.component';
import { AnalyzerPageComponent } from '../face_analyzer/analyzer-page.component';
import { ResultPageComponent } from './result-page/result-page.component';
import { RetryPageComponent } from './retry-page/retry-page.component';

// Only import type because "azure-ai-vision-faceanalyzer" is already initialized in face_analyzer/face.tsx
import type { FaceAnalyzedResult } from 'azure-ai-vision-faceanalyzer';

type AnalyzerState = 'Initial' | 'Analyzer' | 'Result' | 'Retry';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    InitialComponent,
    AnalyzerPageComponent,
    ResultPageComponent,
    RetryPageComponent,
    NgIf,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  title = 'face-angular';

  // State of face analyzer, similar to different pages
  analyzerState: AnalyzerState = 'Initial';

  // Face analyzer parameters
  livenessOperationMode: LivenessOperationMode = 'PassiveActive';
  verifyImage: File | undefined;
  action: string = 'detectLiveness';

  // Retry display variables
  errorMessage: string | undefined;

  // Result display variables
  faceAnalyzedResult: FaceAnalyzedResult | undefined;
  recognitionCondition: boolean | undefined;
  recognitionText: string | undefined;
  livenessCondition: boolean = false;
  livenessText: string = '';

  // View toggle
  pageHidden: { [key: string]: boolean } = {
    Initial: true,
    Analyzer: false,
    Result: false,
    Retry: false,
  };

  setAnalyzerState(s: AnalyzerState) {
    if (this.analyzerState === s) {
      return;
    }
    this.pageHidden[this.analyzerState] = false;
    this.analyzerState = s;
    this.pageHidden[s] = true;
  }

  initFaceAnalyzer(event: {
    livenessOperationMode: LivenessOperationMode;
    file: File | undefined;
  }) {
    this.verifyImage = event.file;
    this.action = event.file ? 'detectLivenessWithVerify' : 'detectLiveness';
    this.livenessOperationMode = event.livenessOperationMode;
    this.setAnalyzerState('Analyzer');
  }

  continueFaceAnalyzer() {
    this.setAnalyzerState('Initial');
  }

  displayResult(event: {
    faceAnalyzedResult: FaceAnalyzedResult;
    recognitionCondition: boolean | undefined;
    recognitionText: string | undefined;
    livenessCondition: boolean;
    livenessText: string;
  }) {
    this.faceAnalyzedResult = event.faceAnalyzedResult;
    this.recognitionCondition = event.recognitionCondition;
    this.recognitionText = event.recognitionText;
    this.livenessCondition = event.livenessCondition;
    this.livenessText = event.livenessText;
    this.setAnalyzerState('Result');
  }

  fetchFailureCallback(error: string) {
    this.errorMessage = `Failed to fetch the token. ${error}`;
    this.setAnalyzerState('Retry');
  }
}
