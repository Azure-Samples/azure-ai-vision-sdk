//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using Android.App;
using Android.Content;
using Microsoft.Maui.Handlers;
using View = Android.Views.View;

namespace AzureAIVisionFaceUIHandler;

public abstract partial class FaceLivenessDetectorHandler: ViewHandler<FaceLivenessDetectorControl, View> {
    public abstract FaceAPIToken GetSessionAuthorizationToken();

    public FaceLivenessDetectorHandler() : base(PropertyMapper) { }

    public static IPropertyMapper<FaceLivenessDetectorControl, FaceLivenessDetectorHandler> PropertyMapper =
        new PropertyMapper<FaceLivenessDetectorControl, FaceLivenessDetectorHandler>(ViewHandler.ViewMapper) { };
    
    protected override Android.Views.View CreatePlatformView() {
        //it is just a mock view
        // Return an empty view
        
        return new Android.Views.View(Android.App.Application.Context);
    }

    protected override void ConnectHandler(View platformView)
    {
        base.ConnectHandler(platformView);
        if (Platform.CurrentActivity is Activity currentActivity)
        {
            var intent = new Intent(
                currentActivity,    
                typeof(Com.Example.Azureaivisionfaceuiwrapper.FaceLivenessDetectorWrapper));
            var token = GetSessionAuthorizationToken();
            intent.PutExtra("com.example.azureaivisionfaceuiwrapper.sessionAuthorizationToken", token.AuthToken);
            intent.PutExtra("com.example.azureaivisionfaceuiwrapper.sessionId", token.SessionId);
            currentActivity.StartActivity(intent);
        }
    }
}
