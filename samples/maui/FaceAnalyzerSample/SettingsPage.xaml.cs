//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using System.ComponentModel;

namespace FaceAnalyzerSample;

public partial class SettingsPage : ContentPage, INotifyPropertyChanged
{
    public SettingsPage()
    {
        InitializeComponent();
        BindingContext = this;
    }

    private void SaveButton_Clicked(object sender, EventArgs e)
    {
        FaceAPI.Endpoint = Endpoint;
        FaceAPI.SubscriptionKey = Key;
        Navigation.PopAsync();
    }

    private void BackButton_Clicked(object sender, EventArgs e)
    {
        Navigation.PopAsync();
    }

    public string Endpoint { get; set; } = FaceAPI.Endpoint;

    public string Key { get; set; } = FaceAPI.SubscriptionKey;

    private bool showKey = false;
    public bool ShowKey
    {
        get => showKey;
        set
        {
            if (showKey != value)
            {
                showKey = value;
                OnPropertyChanged(nameof(ShowKey));
                OnPropertyChanged(nameof(HideKey));
            }
        }
    }

    public bool HideKey => !ShowKey;
}