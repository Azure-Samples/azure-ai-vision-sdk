/* NOTE: This is an EXAMPLE retry page for displaying errors from generating face liveness session. To see how to use the SDK in your application please see face-angularjs/src/face_analyzer/analyzer-page.component.ts */

import {
  CUSTOM_ELEMENTS_SCHEMA,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';

@Component({
  selector: 'retry-page',
  standalone: true,
  imports: [],
  templateUrl: './retry-page.component.html',
  styleUrl: './retry-page.component.css',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class RetryPageComponent {
  @Input('errorMessage') errorMessage: string | undefined;

  @Output() retryAnalyzer: EventEmitter<void> = new EventEmitter();

  onRetry() {
    this.retryAnalyzer.emit();
  }
}
