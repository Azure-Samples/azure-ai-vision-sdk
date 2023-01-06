# How to set up a local NuGet feed

Since the Azure AI Vision SDK is not yet public, the NuGet package cannot be fetched from the public nuget.org feed. Instead, you need to set up a local NuGet feed.

As a first step, you should downloaded the relevant Azure AI Vision SDK NuGet files from the [latest release](https://github.com/Azure-Samples/azure-ai-vision-sdk/releases) in this repository.
They will have names similar to `Azure.AI.Vision.Core.0.8.0-alpha.0.33370873.nupkg` and `Azure.AI.Vision.ImageAnalysis.0.8.0-alpha.0.33370873.nupkg`

You have several options to set up the local feed, depending if you plan to use Visual Studio to compile the sample application (C++, C#), or if you plan to use the .NET Core CLI (C#).

## Using Visual Studio

This assumes you have Visual Studio 2019 installed (or later). The described steps are for Visual Studio 2019, but similar steps should work for other versions of Visual Studio.

### Using Visual Studio NuGet Package Manager UI

* Open `Tools` > `NuGet Package Manager` > `Package Manager Settings`. Select the `Package Sources` tab.

* Press the green plus sign on the top right corner of the tab to create a new NuGet source. Then:
  * In the `Name` field, enter `Local` (or any other name of your choice to identify this local NuGet source).
  * In the `Source` field, enter `c:\packages` (or any other folder where you want to store local NuGet files).

* Press the `Update` button to save the new NuGet source. Make sure the box is checked next to this new source

* Press `OK` to close the settings window.

* Now copy the Azure AI Vision SDK NuGet file to the `Source` folder you specified above.

When the above is done, you are ready to compile the sample code.

When you are done using this local NuGet feed, you can use the `Packages Sources` tab to disable or completely remove the above feed. You may also want to `Clear All NuGet Cashe(s)` (see `Package Manager` > `General` tab).

### Using Visual Studio NuGet Package Manager Console

* In Visual Studio, open `Tools` > `NuGet Package Manager` > `Package Manager Console`.

* Enter the following, replacing `local` with a name of your choice to identify the local NuGet source, and `c:\packages` with the folder where you want to stroe local NuGet files:

    ```cmd
    nuget sources add -name local -source c:\packages
    ```

  It should show `Package source with Name: local added successfully`.

* Now run:

    ```cmd
    nuget restore
    ```

* Now copy the Azure AI Vision SDK NuGet file to the source folder you specified above.

When the above is done, you are ready to compile the sample code.

If you want to remove this local NuGet source, you can do so by entering the following in the Package Manager Console:

```cmd
nuget sources remove -name local
```

It should show `Package source with Name: local removed successfully`.

For more information see [sources command (NuGet CLI)](https://docs.microsoft.com/nuget/reference/cli-reference/cli-ref-sources)

## Using nuget.exe CLI (Windows)

* Download and install the [latest version of nuget.exe for Windows](https://www.nuget.org/downloads) to a folder on your development PC.

* Add this folder to your `PATH` environment variable.

* Follow the nuget commands in the above section to set up a local feed and remove it when you are done.

## Using .NET Core CLI (Windows, Linux, MacOS)

.NET Core CLI gets installed with .NET Core. Instructions below assume Windows file syntax, but similar commands will work for Linux and MacOS.

* Open a command prompt and type `dotnet.exe` to make sure it is in your search path.

* Enter the following, replacing `local` with a name of your choice to identify the local NuGet source, and `c:\packages` with the folder where you want to store local NuGet files:

    ```cmd
    dotnet nuget add source c:\packages --name local
    ```

  It should show `Package source with Name: local added successfully`.

* Now copy the Azure AI Vision SDK NuGet file to the source folder you specified above.

* That's it! you are ready to compile the sample code.

* If you want to remove this local NuGet source, you can do so by typing:

    ```cmd
    dotnet nuget remove source local
    ```

  It should show `Package source with Name: local removed successfully`.

For more information see [dotnet nuget documentation](https://docs.microsoft.com/dotnet/core/tools/dotnet-nuget-add-source).

## By editing NuGet.config file directly

On Windows, the default NuGet configuration file is the following: `%appdata%\NuGet\NuGet.config` (unless you have another NuGet.config file in the current folder or any folder above the current folder). As you add and remove the local source feed based on the instructions below, you can see how this file changes. You can edit this file directly (but not recommended).
