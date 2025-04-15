//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using System.Threading.Tasks;
using Microsoft.Maui.ApplicationModel;

namespace FaceAnalyzerSample;

public partial class MainPage : ContentPage
{
    public MainPage()
    {
        InitializeAsync();
    }

    private async void InitializeAsync()
    {
        await RequestCameraPermissionAsync();
        InitializeComponent();
    }

    private async Task RequestCameraPermissionAsync()
    {
        var status = await Permissions.CheckStatusAsync<Permissions.Camera>();
        if (status != PermissionStatus.Granted)
        {
            status = await Permissions.RequestAsync<Permissions.Camera>();
        }

        if (status != PermissionStatus.Granted)
        {
            // Handle the case when the permission is not granted
            await DisplayAlert("Permission Denied", "Camera permission is required to use this feature.", "OK");
        }
    }

    private void StartButton_Clicked(object sender, EventArgs e)
    {
        Navigation.PushAsync(new AnalysisPage());
    }

    private void SettingsButton_Clicked(object sender, EventArgs e)
    {
        Navigation.PushAsync(new SettingsPage());
    }
}
