/* NOTE: This is an EXAMPLE splash page for starting the face liveness session. To see how to use the SDK in your application please see face-angularjs/src/face_analyzer/analyzer-page.component.ts */

import {
  CUSTOM_ELEMENTS_SCHEMA,
  Component,
  EventEmitter,
  HostBinding,
  Output,
} from '@angular/core';
import { LivenessOperationMode } from '../utils';
import { NgIf } from '@angular/common';

type InitResponse = {
  livenessOperationMode: LivenessOperationMode;
  file: File | undefined;
};

@Component({
  selector: 'initial-page',
  standalone: true,
  imports: [NgIf],
  templateUrl: './initial-page.component.html',
  styleUrl: './initial-page.component.css',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class InitialComponent {
  @Output() initFaceAnalyzer: EventEmitter<InitResponse> = new EventEmitter();

  @HostBinding('className') componentClass: string = 'page ms-Fabric';

  verifyImage: File | undefined;
  imageObjectUrl: string = '';

  initAnalyzer(mode: LivenessOperationMode) {
    this.initFaceAnalyzer.emit({
      livenessOperationMode: mode,
      file: this.verifyImage,
    });
  }

  setImage(event: Event) {
    const elem = event.target as HTMLInputElement;
    if (elem.files) {
      this.imageObjectUrl = URL.createObjectURL(elem.files[0]);
      this.verifyImage = elem.files[0];
    } else {
      this.verifyImage = undefined;
    }
  }
}
