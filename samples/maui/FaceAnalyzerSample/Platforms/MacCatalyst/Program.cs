//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using ObjCRuntime;
using UIKit;

namespace FaceAnalyzerSample;

public class Program
{
    // This is the main entry point of the application.
    static void Main(string[] args)
    {
        // if you want to use a different Application Delegate class from "AppDelegate"
        // you can specify it here.
        UIApplication.Main(args, null, typeof(AppDelegate));
    }
}
