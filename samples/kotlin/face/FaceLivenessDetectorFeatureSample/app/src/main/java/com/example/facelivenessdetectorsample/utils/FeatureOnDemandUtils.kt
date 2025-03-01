package com.example.facelivenessdetectorsample.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.unit.dp
import com.example.facelivenessdetectorsample.utils.FeatureOnDemandUtils.Companion.findMethodByReflection
import com.example.facelivenessdetectorsample.utils.FeatureOnDemandUtils.Companion.invokeMethod
import com.example.facelivenessdetectorsample.utils.FeatureOnDemandUtils.Companion.loadClassByReflection
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus.DOWNLOADING
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus.INSTALLED
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel.LivenessScreenViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.function.Consumer


class FeatureOnDemandUtils {
    companion object {
        fun loadClassByReflection(className: String): Class<*>? {
            return try {
                val classLoader = ::loadClassByReflection.javaClass.classLoader
                classLoader?.loadClass(className)
            } catch (e: Throwable) {
                null
            }
        }
        fun findMethodByReflection(classMethod: Class<*>?, methodName: String): Method? {
            return try {
                if (!TextUtils.isEmpty(methodName)) {
                    classMethod?.let { clazz ->
                        clazz.methods.find { it.name.equals(methodName) && java.lang.reflect.Modifier.isStatic(it.modifiers) }
                    } ?: run {
                        null
                    }
                } else {
                    null
                }
            }catch (e:Throwable){
                null
            }
        }
        fun invokeMethod(method: Method, obj: Any, vararg args: Any): Boolean {
            return try {
                method.invoke(obj,*(args))
                true
            } catch (e: Throwable) {
                false
            }
        }

        val moduleName = "dynamicfeature"
        private var _ctx: Context? = null
        private var splitInstallManager: SplitInstallManager? = null
        fun InitializeContext(ctx: Context){
            _ctx = ctx
            splitInstallManager = SplitInstallManagerFactory.create(ctx)
        }
        fun CheckModule() : Boolean {
            splitInstallManager?.let {
                val installedModules: Set<String> = it.installedModules
                if(installedModules.contains(moduleName)){
                    return true
                }
            }
            return false
        }

        private fun getCurrentActivity(): Activity? {
            var context = _ctx
            while (context is ContextWrapper) {
                if (context is Activity) {
                    return context
                }
                context = context.baseContext
            }
            return null
        }
        fun installModule(){
            splitInstallManager?.let{
                val mainScope = MainScope()
                if(CheckModule()){
                    return
                }
                val request =
                    SplitInstallRequest
                        .newBuilder()
                        // You can download multiple on demand modules per
                        // request by invoking the following method for each
                        // module you want to install.
                        .addModule(moduleName)
                        .build()

                var mySessionId = 0
                val listener = object: SplitInstallStateUpdatedListener {
                    val mListener: SplitInstallStateUpdatedListener = this
                    override fun onStateUpdate(state: SplitInstallSessionState) {
                        if (state.sessionId() == mySessionId) {
                            when (state.status()) {
                                DOWNLOADING ->{
                                    mainScope.launch { Toast.makeText(_ctx, "downloaded: " + state.bytesDownloaded().toString(), Toast.LENGTH_SHORT) }
                                }
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
                        }
                    }
                }
                it.registerListener(listener)

                it.startInstall(request)
                    .addOnSuccessListener { sessionId ->
                        mySessionId = sessionId
                    }
                    .addOnFailureListener { exception ->
                        mainScope.launch { Toast.makeText(_ctx, exception.message?:"", Toast.LENGTH_LONG) }
                    }
            }
        }
    }
}


@Composable
private fun loadDynamicFeatureScreen(
    model: Any,
    className: String,
    methodName: String,
    objectInstance: Any = Any()
) {
    val dfClass = loadClassByReflection(className)
    if (dfClass != null) {
        val composer = currentComposer
        val method = findMethodByReflection(
            dfClass,
            methodName
        )
        if (method != null) {
            val isMethodInvoked =
                invokeMethod(method, objectInstance, model, composer, 0)
            if (!isMethodInvoked) {
                Text(text = "Liveness Module loading method invoke failed!", Modifier.padding(16.dp), textAlign = Center)

            }

        } else {
            Text(text = "Liveness Module loading method found failed!", Modifier.padding(16.dp), textAlign = Center)

        }
    } else {
        Text(text = "Liveness Module loading failed!", Modifier.padding(16.dp), textAlign = Center)

    }
}


@Composable
fun FaceLivenessDetectorDynamicFeature(
    viewModel: LivenessScreenViewModel,
    sessionAuthorizationToken: String,
    verifyImageFileContent: ByteArray?,
    deviceCorrelationId: String?,
    onSuccess: (Any) -> Unit,
    onError: (Any)-> Unit
) {
    val packageName = "com.microsoft.azure.ai.vision.facelivenessdetectorsample.dynamicfeature."
    val detectorScreenName = packageName.plus("FaceLivenessDetectorModuleKt")
    val faceModelName = packageName.plus("FaceLivenessDetectorModel")
    SplitCompat.install(LocalContext.current)
    //get model class initialized
    val fmClass = Class.forName(faceModelName)
    if(fmClass != null){
        val constructor = fmClass.getConstructor(
            String::class.java,    // Corresponds to sessionAuthorizationToken
            ByteArray::class.java, // Corresponds to verifyImageFileContent
            String::class.java,    // Corresponds to deviceCorrelationId
            Consumer::class.java,  // Corresponds to onSuccess (using Any -> Unit as proxy for a functional type)
            Consumer::class.java   // Corresponds to onError
        )
        val onSuccess: Consumer<Any> = Consumer{
            viewModel.onSuccess(it)
        }
        val onError: Consumer<Any> = Consumer{
            viewModel.onError(it)
        }
        val faceModel = constructor.newInstance(
            sessionAuthorizationToken,
            verifyImageFileContent,
            deviceCorrelationId,
            onSuccess,
            onError
        )
        //get compose method initialized
        val composeMethod = "FaceLivenessDetectorModule"
        loadDynamicFeatureScreen(model = faceModel, className = detectorScreenName, methodName = composeMethod)
    }
}
