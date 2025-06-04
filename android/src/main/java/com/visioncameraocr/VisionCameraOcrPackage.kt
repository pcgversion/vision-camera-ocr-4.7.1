package com.visioncameraocr

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin
import com.mrousavy.camera.frameprocessors.FrameProcessorPluginRegistry


class VisionCameraOcrPackage : ReactPackage {
    companion object {
        init {
            FrameProcessorPluginRegistry.addFrameProcessorPlugin("scanOCR") { proxy, options ->
                VisionCameraOcrPlugin(proxy, options)
            }
        }
    }
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        //FrameProcessorPlugin.register(VisionCameraOcrPlugin())
        return emptyList()
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }
}
