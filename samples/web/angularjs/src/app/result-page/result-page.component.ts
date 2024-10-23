/* NOTE: This is an EXAMPLE result page for displaying the result of the face liveness session on the client-side. For production, it is recommended that the results of the face liveness session should not be sent to client for security.
To see how to use the SDK in your application please see face-angularjs/src/face/liveness-detector-page.component.ts */

import { NgIf } from '@angular/common';
import {
  CUSTOM_ELEMENTS_SCHEMA,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';

const checkIcon = 'CheckmarkCircle.png';
const dismissIcon = 'DismissCircle.png';

@Component({
  selector: 'result-page',
  standalone: true,
  imports: [NgIf],
  templateUrl: './result-page.component.html',
  styleUrl: './result-page.component.css',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ResultPageComponent implements OnInit {
  @Input('recognitionCondition') recognitionCondition: boolean | undefined;
  @Input('recognitionText') recognitionText: string | undefined;
  @Input('livenessCondition') livenessCondition: boolean = false;
  @Input('livenessText') livenessText: string = '';

  @Output() continueLivenessDetection: EventEmitter<void> = new EventEmitter();

  recognitionIcon: string | undefined;
  livenessIcon: string = '';

  ngOnInit(): void {
    // Set icons based on analyzed result
    this.livenessIcon = this.livenessCondition ? checkIcon : dismissIcon;
    if (this.recognitionCondition !== undefined) {
      this.recognitionIcon = this.recognitionCondition
        ? checkIcon
        : dismissIcon;
    }
  }

  onContinue() {
    this.continueLivenessDetection.emit();
  }
}
