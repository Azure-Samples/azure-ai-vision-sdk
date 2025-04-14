//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using AzureAIVisionFaceUIHandler;

namespace FaceAnalyzerSample;

[Activity(Exported = false, Theme = "@style/Maui.SplashTheme", LaunchMode = LaunchMode.SingleTop, ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode | ConfigChanges.ScreenLayout | ConfigChanges.SmallestScreenSize | ConfigChanges.Density)]
[IntentFilter(new[] {Intent.ActionView},
    Categories = new[] {Intent.CategoryDefault, "com.example.azureaivisionfaceuiwrapper.result"})]
public class ResultActivity : MauiAppCompatActivity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        if (Intent is Intent intent &&
            intent.AsLivenessDetectionResult() is LivenessDetectionResult result &&
            intent.HasExtra("com.example.azureaivisionfaceuiwrapper.result.sessionId") &&
            intent.GetStringExtra("com.example.azureaivisionfaceuiwrapper.result.sessionId") is string sessionId &&
            AppShell.Shared is Shell shell)
        {
            var page = new ResultPage(result, sessionId);
            shell.Navigation.PushAsync(page);
        }
    }
}
