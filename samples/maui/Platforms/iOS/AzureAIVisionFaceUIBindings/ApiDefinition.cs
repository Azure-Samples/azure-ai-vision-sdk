//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using AzureAIVisionFaceUI;
using AzureAIVisionFaceUIWrapper;
using Foundation;
using ObjCRuntime;
using UIKit;

namespace AzureAIVisionFaceUIWrapper
{
    // @interface FaceLivenessDetectorWrapper : NSObject
    [BaseType (typeof(NSObject), Name = "_TtC26AzureAIVisionFaceUIWrapper27FaceLivenessDetectorWrapper")]
    [DisableDefaultCtor]
    interface FaceLivenessDetectorWrapper
    {
        // @property (readonly, nonatomic, strong) UIView * _Nullable uiView;
        [NullAllowed, Export ("uiView", ArgumentSemantic.Strong)]
        UIView UiView { get; }

        // -(instancetype _Nonnull)initWithSessionAuthorizationToken:(NSString * _Nonnull)sessionAuthorizationToken delegate:(id<FaceLivenessDetectorWrapperDelegate> _Nonnull)delegate __attribute__((objc_designated_initializer));
        [Export ("initWithSessionAuthorizationToken:delegate:")]
        [DesignatedInitializer]
        NativeHandle Constructor (string sessionAuthorizationToken, IFaceLivenessDetectorWrapperDelegate @delegate);
    }

    // @protocol FaceLivenessDetectorWrapperDelegate
    [Protocol (Name = "_TtP26AzureAIVisionFaceUIWrapper35FaceLivenessDetectorWrapperDelegate_"), Model]
    interface FaceLivenessDetectorWrapperDelegate
    {
        // @required -(void)resultHasChangedWithResult:(LivenessDetectionResultWrapper * _Nonnull)result;
        [Abstract]
        [Export ("resultHasChangedWithResult:")]
        void ResultHasChangedWithResult (LivenessDetectionResultWrapper result);
    }

    // @interface LivenessDetectionErrorWrapper : NSObject
    [BaseType (typeof(NSObject), Name = "_TtC26AzureAIVisionFaceUIWrapper29LivenessDetectionErrorWrapper")]
    [DisableDefaultCtor]
    interface LivenessDetectionErrorWrapper
    {
        // @property (readonly, copy, nonatomic) NSString * _Nullable resultId;
        [NullAllowed, Export ("resultId")]
        string ResultId { get; }

        // @property (readonly, nonatomic) enum AZFaceLivenessFailureReason livenessError;
        [Export ("livenessError")]
        AZFaceLivenessFailureReason LivenessError { get; }

        // @property (readonly, nonatomic) enum AZFaceRecognitionFailureReason recognitionError;
        [Export ("recognitionError")]
        AZFaceRecognitionFailureReason RecognitionError { get; }
    }

    // @interface LivenessDetectionResultWrapper : NSObject
    [BaseType (typeof(NSObject), Name = "_TtC26AzureAIVisionFaceUIWrapper30LivenessDetectionResultWrapper")]
    [DisableDefaultCtor]
    interface LivenessDetectionResultWrapper
    {
        // @property (readonly, nonatomic, strong) LivenessDetectionSuccessWrapper * _Nullable success;
        [NullAllowed, Export ("success", ArgumentSemantic.Strong)]
        LivenessDetectionSuccessWrapper Success { get; }

        // @property (readonly, nonatomic, strong) LivenessDetectionErrorWrapper * _Nullable error;
        [NullAllowed, Export ("error", ArgumentSemantic.Strong)]
        LivenessDetectionErrorWrapper Error { get; }
    }

    // @interface LivenessDetectionSuccessWrapper : NSObject
    [BaseType (typeof(NSObject), Name = "_TtC26AzureAIVisionFaceUIWrapper31LivenessDetectionSuccessWrapper")]
    [DisableDefaultCtor]
    interface LivenessDetectionSuccessWrapper
    {
        // @property (readonly, copy, nonatomic) NSString * _Nonnull resultId;
        [Export ("resultId")]
        string ResultId { get; }

        // @property (readonly, copy, nonatomic) NSString * _Nonnull digest;
        [Export ("digest")]
        string Digest { get; }
    }
}
