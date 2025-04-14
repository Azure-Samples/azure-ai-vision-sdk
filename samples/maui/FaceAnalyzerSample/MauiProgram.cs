//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using Microsoft.Extensions.Logging;
using AzureAIVisionFaceUIHandler;

namespace FaceAnalyzerSample;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .ConfigureFonts(fonts =>
            {
                fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
                fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
            })
            .ConfigureMauiHandlers(handlers =>
            {
                handlers.AddHandler(typeof(FaceLivenessDetectorControl), typeof(FaceLivenessDetectorHandlerImpl));
            });

#if DEBUG
        builder.Logging.AddDebug();
#endif

        return builder.Build();
    }
}
