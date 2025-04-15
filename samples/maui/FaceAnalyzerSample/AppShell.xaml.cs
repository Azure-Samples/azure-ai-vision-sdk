//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

namespace FaceAnalyzerSample;

public partial class AppShell : Shell
{
    public static Shell? Shared { get; private set; } = null;

    public AppShell()
    {
        InitializeComponent();
        Shared = this;
    }
}
