//
//  AzureFaceLivenessBridge.m
//  RNAzureLiveness
//
//  Created by Harshil J on 17/04/25.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(AzureLivenessManager, RCTEventEmitter)

RCT_EXTERN_METHOD(startLivenessDetection:(NSString *)sessionToken
                  isLiveness:(NSString *)isLiveness)

@end
