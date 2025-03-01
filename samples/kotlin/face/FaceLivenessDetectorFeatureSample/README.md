# Get started with the Azure AI Vision Face UI SDK for Android
This sample extends the FaceLivenessDetectorSample and demonstrates how to use the Azure AI Vision Face UI SDK with Android’s on-demand feature capability. For more details on Android’s on-demand feature, see [Configure_on_demand_delivery](https://developer.android.com/guide/playcore/feature-delivery/on-demand). This capability allows you to separate the SDK-dependent code from your main application, so you can download and install the SDK only when necessary.

## Important
Before using this sample, please review the FaceLivenessDetectorSample [README](../FaceLivenessDetectorSample/README.md) to familiarize yourself with building and running the samples, as well as integrating the SDK into your own application. 

This document explains the modifications in the SDK integration required to support the on-demand feature capability.

## dynamicfeature
It consists a module "dynamicfeature" that's installable, wrapping the SDK UI code into a compose function.
* `dynamicfeature/build.gradle.kts` enables compose in `buildFeatures` flag
```
    buildFeatures {
        compose = true
    }
```
* `FaceLivenessDetectorModule` wraps the Face UI SDK code and provides `SplitInstallHelper` setups
```
    SplitCompat.install(context)
    SplitInstallHelper.loadLibrary(context, "Azure-AI-Vision-Native")
    SplitInstallHelper.loadLibrary(context, "onnxruntime")
    SplitInstallHelper.loadLibrary(context, "Azure-AI-Vision-Extension-Face")
    SplitInstallHelper.loadLibrary(context, "Azure-AI-Vision-JNI")
```

## main app
In `app/src/main/java/com/example/facelivenessdetectorsample/utils/FeatureOnDemandUtils.kt` you can see a demostration on how the compose UI can be called through reflection.
* The class name for the compose function in the `dynamicfeature` module is `com.microsoft.azure.ai.vision.facelivenessdetectorsample.dynamicfeature.FaceLivenessDetectorModuleKt`.  The `FaceLivenessDetectorModuleKt` is from the kotlin file name contains the compose function you trying to call through reflection.
```
val packageName = "com.microsoft.azure.ai.vision.facelivenessdetectorsample.dynamicfeature."
val detectorScreenName = packageName.plus("FaceLivenessDetectorModuleKt")
val composeMethod = "FaceLivenessDetectorModule"
loadDynamicFeatureScreen(model = faceModel, className = detectorScreenName, methodName = composeMethod)
```
* Compose function signiture slightly changes and it takes `currentComposer` as input too
```
val composer = currentComposer
invokeMethod(method, objectInstance, model, composer, 0)
```
* In `installModule` function you can see how to handle module installation.  For more on feature on demand setup, see [Configure_on_demand_delivery](https://developer.android.com/guide/playcore/feature-delivery/on-demand)
```
private var splitInstallManager: SplitInstallManager? = null

```

```
val listener = object: SplitInstallStateUpdatedListener {

```
```
                                INSTALLED -> {
                                    mainScope.launch {
                                        getCurrentActivity()?.let{
                                            SplitCompat.install(it.applicationContext);
                                            it.recreate()
                                        }
                                        it.unregisterListener(mListener)
                                    }
                                }
}

```
```
it.startInstall(request)

```