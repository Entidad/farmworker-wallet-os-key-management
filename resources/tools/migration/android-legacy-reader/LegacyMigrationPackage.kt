package fwos.keymanagement.migration

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * Registers the one-shot legacy migration reader as a React Native module.
 * A classic bridge ReactPackage; runs under the New Architecture via the interop layer.
 */
class LegacyMigrationPackage : ReactPackage {
    override fun createNativeModules(
        reactContext: ReactApplicationContext
    ): List<NativeModule> = listOf(LegacySensitiveInfoReaderModule(reactContext))

    override fun createViewManagers(
        reactContext: ReactApplicationContext
    ): List<ViewManager<*, *>> = emptyList()
}
