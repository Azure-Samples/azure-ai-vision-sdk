//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using Microsoft.Maui.Handlers;
using AzureAIVisionFaceUIWrapper;
using UIKit;

namespace AzureAIVisionFaceUIHandler;

public abstract partial class FaceLivenessDetectorHandler : ViewHandler<FaceLivenessDetectorControl, UIView> {

    public abstract FaceAPIToken GetSessionAuthorizationToken();
    public abstract IFaceLivenessDetectorWrapperDelegate GetDelegate();

    public FaceLivenessDetectorHandler() : base(PropertyMapper) { }

    public static IPropertyMapper<FaceLivenessDetectorControl, FaceLivenessDetectorHandler> PropertyMapper =
        new PropertyMapper<FaceLivenessDetectorControl, FaceLivenessDetectorHandler>(ViewHandler.ViewMapper) { };

    protected override UIView CreatePlatformView() {
        var wrapper = new FaceLivenessDetectorWrapper(GetSessionAuthorizationToken().AuthToken, GetDelegate());
        return wrapper.UiView;
    }
}