package net.imknown.android.forefrontinfo.ui.home

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.webkit.WebViewCompat
import com.g00fy2.versioncompare.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.imknown.android.forefrontinfo.MyApplication
import net.imknown.android.forefrontinfo.R
import net.imknown.android.forefrontinfo.base.Event
import net.imknown.android.forefrontinfo.base.GatewayApi
import net.imknown.android.forefrontinfo.base.JsonIo
import net.imknown.android.forefrontinfo.base.fromJson
import net.imknown.android.forefrontinfo.ui.base.BaseListViewModel
import net.imknown.android.forefrontinfo.ui.base.MyModel
import net.imknown.android.forefrontinfo.ui.home.model.Lld
import net.imknown.android.forefrontinfo.ui.home.model.Subtitle

class HomeViewModel : BaseListViewModel() {

    companion object {
        private const val PROP_RO_SYSTEM_BUILD_ID = "ro.system.build.id"
        private const val PROP_RO_VENDOR_BUILD_ID = "ro.vendor.build.id"
        private const val PROP_RO_ODM_BUILD_ID = "ro.odm.build.id"

        private const val PROP_SECURITY_PATCH = "ro.build.version.security_patch"

        private const val PROP_VENDOR_SECURITY_PATCH = "ro.vendor.build.security_patch"

        private val SYSTEM_PROPERTY_LINUX_VERSION = System.getProperty("os.version")

        // https://source.android.com/devices/tech/ota/ab?hl=en
        // /* root needed */ private const val CMD_BOOT_PARTITION = "ls /dev/block/bootdevice/by-name | grep boot_"
        private const val PROP_AB_UPDATE = "ro.build.ab_update"
        private const val PROP_SLOT_SUFFIX = "ro.boot.slot_suffix"

        // https://source.android.com/devices/tech/ota/dynamic_partitions/ab_legacy?hl=en
        // https://source.android.com/devices/tech/ota/dynamic_partitions/ab_launch?hl=en
        // https://codelabs.developers.google.com/codelabs/using-Android-GSI?hl=en
        private const val PROP_DYNAMIC_PARTITIONS = "ro.boot.dynamic_partitions"
        private const val PROP_DYNAMIC_PARTITIONS_RETROFIT = "ro.boot.dynamic_partitions_retrofit"

        // https://developer.android.com/topic/dsu?hl=en
        // private const val PROP_DYNAMIC_SYSTEM_UPDATE = "persist.sys.fflag.override.settings_dynamic_system"
        private const val FFLAG_PREFIX = "sys.fflag."
        private const val FFLAG_OVERRIDE_PREFIX = FFLAG_PREFIX + "override."
        private const val DYNAMIC_SYSTEM = "settings_dynamic_system"

        // https://codelabs.developers.google.com/codelabs/using-Android-GSI?hl=en#2
        // https://developer.android.google.cn/topic/generic-system-image?hl=en
        // https://source.android.com/setup/build/gsi?hl=en
        private const val CMD_VENDOR_NAMESPACE_DEFAULT_ISOLATED =
            "cat /system/etc/ld.config*.txt | grep -A 20 '\\[vendor\\]' | grep namespace.default.isolated"

        // https://source.android.com/devices/architecture?hl=en#hidl
        private const val PROP_TREBLE_ENABLED = "ro.treble.enabled"

        // https://source.android.com/devices/architecture/vndk?hl=en
        private const val PROP_VNDK_LITE = "ro.vndk.lite"
        private const val PROP_VNDK_VERSION = "ro.vndk.version"

        // https://source.android.com/devices/bootloader/system-as-root?hl=en
        // https://twitter.com/topjohnwu/status/1174392824625676288
        // https://github.com/topjohnwu/Magisk/blob/master/scripts/util_functions.sh#L193
        // https://github.com/opengapps/opengapps/blob/master/scripts/inc.installer.sh#L710
        // https://github.com/penn5/TrebleCheck/blob/master/app/src/main/java/tk/hack5/treblecheck/MountDetector.kt
        private const val PROP_SYSTEM_ROOT_IMAGE = "ro.build.system_root_image"
        private const val CMD_MOUNT_DEV_ROOT = "grep '/dev/root / ' /proc/mounts"
        private const val CMD_MOUNT_SYSTEM =
            "grep ' /system ' /proc/mounts | grep -v 'tmpfs' | grep -v 'none'"

        // https://source.android.com/devices/tech/ota/apex?hl=en
        private const val PROP_APEX_UPDATABLE = "ro.apex.updatable"
        private const val CMD_FLATTENED_APEX_MOUNT = "grep 'tmpfs /apex tmpfs' /proc/mounts"

        private const val SETTINGS_DISABLED = 0

        // https://cs.android.com/android/platform/superproject/+/master:bootable/recovery/README.md;l=139
        private const val PROP_ADB_SECURE = "ro.adb.secure"

        // https://source.android.com/security/encryption/full-disk
        // https://source.android.com/security/encryption/file-based
        // private const val PROP_CRYPTO_STATE = "ro.crypto.state"

        // https://source.android.com/security/selinux
        // https://cs.android.com/android/platform/superproject/+/master:external/selinux/libsepol/include/sepol/policydb/policydb.h;l=745
        // https://github.com/torvalds/linux/blob/master/security/selinux/include/security.h#L43
        // /* root needed */ private const val CMD_GETENFORCE = "getenforce"
        private const val SELINUX_MOUNT = "/sys/fs/selinux"
        private const val CMD_SELINUX_STATUS = "cat $SELINUX_MOUNT/enforce"
        private const val CMD_SELINUX_POLICY_VERSION = "cat $SELINUX_MOUNT/policyvers"
        private const val CMD_ERROR_PERMISSION_DENIED = "Permission denied"
        private const val CMD_ERROR_NO_SUCH_FILE_OR_DIRECTORY = "No such file or directory"
        private const val PROP_BUILD_SELINUX = "ro.build.selinux"
        private const val PROP_BOOT_SELINUX = "ro.boot.selinux"
        private const val SELINUX_STATUS_DISABLE = "disable"
        private const val SELINUX_STATUS_PERMISSIVE = "permissive"
        private const val SELINUX_STATUS_ENFORCING = "enforcing"

        private const val CMD_TOYBOX_VERSION = "toybox --version"

        private const val WEB_VIEW_BUILT_IN_PACKAGE_NAME = "com.android.webview"
        private const val WEB_VIEW_STABLE_PACKAGE_NAME = "com.google.android.webview"
        private const val WEB_VIEW_BETA_PACKAGE_NAME = "com.google.android.webview.beta"
        private const val WEB_VIEW_DEV_IN_PACKAGE_NAME = "com.google.android.webview.dev"
        private const val WEB_VIEW_CANARY_PACKAGE_NAME = "com.google.android.webview.canary"
        private const val CHROME_STABLE_PACKAGE_NAME = "com.android.chrome"
        private const val CHROME_BETA_PACKAGE_NAME = "com.chrome.beta"
        private const val CHROME_DEV_IN_PACKAGE_NAME = "com.chrome.dev"
        private const val CHROME_CANARY_PACKAGE_NAME = "com.chrome.canary"

        private const val PROP_RO_PRODUCT_FIRST_API_LEVEL = "ro.product.first_api_level"
    }

    private val _subtitle by lazy { MutableLiveData<Subtitle>() }
    val subtitle: LiveData<Subtitle> by lazy { _subtitle }

    private val _error by lazy { MutableLiveData<Event<Exception>>() }
    val error: LiveData<Event<Exception>> by lazy { _error }

    private fun copyJsonIfNeeded() {
        if (JsonIo.whetherNeedCopyAssets(MyApplication.instance.assets)) {
            JsonIo.copyJsonFromAssetsToContextFilesDir(
                MyApplication.instance.assets,
                JsonIo.savedLldJsonFile,
                JsonIo.LLD_JSON_NAME
            )
        }
    }

    override fun collectModels() = viewModelScope.launch(Dispatchers.IO) {
        val allowNetwork = MyApplication.sharedPreferences.getBoolean(
            MyApplication.getMyString(R.string.function_allow_network_data_key), false
        )

        if (allowNetwork) {
            GatewayApi.downloadLldJsonFile({
                launch(Dispatchers.IO) {
                    prepareResult(true)
                }
            }, {
                launch(Dispatchers.IO) {
                    onError(
                        Exception(
                            MyApplication.getMyString(
                                R.string.lld_json_download_failed,
                                it.message
                            )
                        )
                    )

                    prepareResult(false)
                }
            })
        } else {
            prepareResult(false)
        }
    }

    private suspend fun onError(exception: Exception) = withContext(Dispatchers.Main) {
        _error.value = Event(exception)
    }

    private suspend fun prepareResult(isOnline: Boolean) {
        if (!isOnline) {
            try {
                copyJsonIfNeeded()
            } catch (e: Exception) {
                onError(e)
            }
        }

        prepareResultWhenOk(isOnline)
    }

    private suspend fun prepareResultWhenOk(isOnline: Boolean) {
        @StringRes var lldDataModeResId: Int
        var dataVersion: String

        try {
            val lld = JsonIo.savedLldJsonFile.fromJson<Lld>()

            detect(lld)

            lldDataModeResId = if (isOnline) {
                R.string.lld_json_online
            } else {
                R.string.lld_json_offline
            }
            dataVersion = lld.version
        } catch (e: Exception) {
            onError(Exception(MyApplication.getMyString(R.string.lld_json_parse_failed, e.message)))

            lldDataModeResId = R.string.lld_json_offline

            dataVersion = MyApplication.getMyString(android.R.string.unknownName)
        }

        withContext(Dispatchers.Main) {
            _subtitle.value = Subtitle(lldDataModeResId, dataVersion)
        }
    }

    // region [detect]
    private suspend fun detect(lld: Lld) {
        val tempModels = ArrayList<MyModel>()

        detectAndroid(tempModels, lld)

        detectBuildId(tempModels, lld)

        var securityPatch = if (isAtLeastAndroid6()) {
            Build.VERSION.SECURITY_PATCH
        } else {
            getStringProperty(PROP_SECURITY_PATCH)
        }
        detectSecurityPatch(tempModels, lld, securityPatch, R.string.security_patch_level_title)

        securityPatch = getStringProperty(PROP_VENDOR_SECURITY_PATCH, isAtLeastAndroid9())
        detectSecurityPatch(
            tempModels,
            lld,
            securityPatch,
            R.string.vendor_security_patch_level_title
        )

        detectMainline(tempModels, lld)

        detectKernel(tempModels, lld)

        detectSELinux(tempModels)

        detectAb(tempModels)

        detectDynamicPartitions(tempModels)

        detectDsu(tempModels)

        detectTreble(tempModels)

        detectGsiCompatibility(tempModels)

        detectVndk(tempModels, lld)

        detectSar(tempModels)

        detectApex(tempModels)

        detectDeveloperOptions(tempModels)

        detectAdb(tempModels)

        detectAdbAuthentication(tempModels)

        detectEncryption(tempModels)

        detectToybox(tempModels, lld)

        detectWebView(tempModels, lld)

        detectOutdatedTargetSdkVersionApk(tempModels)

        withContext(Dispatchers.Main) {
            _models.value = tempModels
        }
    }

    private fun detectAndroid(tempModels: ArrayList<MyModel>, lld: Lld) {
        @ColorRes val androidColor = when {
            isLatestStableAndroid(lld) || isLatestPreviewAndroid(lld) -> R.color.colorNoProblem
            isSupportedByUpstreamAndroid(lld) -> R.color.colorWaring
            else -> R.color.colorCritical
        }

        val previewVersion = lld.android.preview.version
        val previewApi = lld.android.preview.api
        val previewPhase = lld.android.preview.phase

        add(
            tempModels,
            MyApplication.getMyString(
                R.string.android_info_title
            ),
            MyApplication.getMyString(
                R.string.android_info_detail,
                MyApplication.getMyString(
                    R.string.android_info,
                    getAndroidVersionName(),
                    Build.VERSION.SDK_INT
                ),
                MyApplication.getMyString(
                    R.string.android_info,
                    lld.android.stable.version,
                    lld.android.stable.api
                ),
                MyApplication.getMyString(
                    R.string.android_info,
                    lld.android.support.version,
                    lld.android.support.api
                ),
                MyApplication.getMyString(R.string.android_info_preview),
                MyApplication.getMyString(
                    R.string.android_info,
                    "$previewVersion $previewPhase",
                    previewApi
                )
            ),
            androidColor
        )
    }

    private fun detectBuildId(tempModels: ArrayList<MyModel>, lld: Lld) {
        val buildIdResult = Build.ID
        val systemBuildIdResult = getStringProperty(PROP_RO_SYSTEM_BUILD_ID, isAtLeastAndroid9())
        val vendorBuildIdResult = getStringProperty(PROP_RO_VENDOR_BUILD_ID, isAtLeastAndroid9())
        val odmBuildIdResult = getStringProperty(PROP_RO_ODM_BUILD_ID, isAtLeastAndroid9())

        var builds = ""
        val details = lld.android.build.details
        details.forEachIndexed { index, detail ->
            builds += MyApplication.getMyString(
                R.string.android_build_id,
                detail.id,
                detail.revision
            )

            if (index != details.size - 1) {
                builds += "\n"
            }
        }

        fun getDate(buildId: String) = buildId.split('.')[1]
        fun isDateHigherThanConfig() =
            (buildIdResult.split('.').size == 3 && getDate(buildIdResult) >= getDate(details[0].id))
                    || isLatestPreviewAndroid(lld)

        @ColorRes val buildIdColor = when {
            isDateHigherThanConfig() -> R.color.colorNoProblem
            isLatestStableAndroid(lld) -> R.color.colorWaring
            else -> R.color.colorCritical
        }

        add(
            tempModels,
            MyApplication.getMyString(
                R.string.android_build_id_title
            ),
            MyApplication.getMyString(
                R.string.android_build_id_detail,
                buildIdResult,
                systemBuildIdResult,
                vendorBuildIdResult,
                odmBuildIdResult,
                lld.android.build.version,
                builds
            ),
            buildIdColor
        )
    }

    private fun detectSecurityPatch(
        tempModels: ArrayList<MyModel>,
        lld: Lld,
        securityPatch: String, @StringRes titleId: Int
    ) {
        val lldSecurityPatch = lld.android.securityPatchLevel
        @ColorRes val securityPatchColor = when {
            !isPropertyValueNotEmpty(securityPatch) -> R.color.colorCritical
            securityPatch >= lldSecurityPatch -> R.color.colorNoProblem
            getSecurityPatchYearMonth(securityPatch) >= getSecurityPatchYearMonth(lldSecurityPatch) -> R.color.colorWaring
            else -> R.color.colorCritical
        }

        add(
            tempModels,
            MyApplication.getMyString(titleId),
            MyApplication.getMyString(
                R.string.security_patch_level_detail,
                securityPatch,
                lld.android.securityPatchLevel
            ),
            securityPatchColor
        )
    }

    private fun getSecurityPatchYearMonth(securityPatch: String) =
        securityPatch.substringBeforeLast('-')

    /**
     * {@link com.android.settings.deviceinfo.firmwareversion.MainlineModuleVersionPreferenceController}
     */
    private fun detectMainline(tempModels: ArrayList<MyModel>, lld: Lld) {
        // com.android.internal.R.string.config_defaultModuleMetadataProvider
        val idConfigDefaultModuleMetadataProvider = Resources.getSystem().getIdentifier(
            "config_defaultModuleMetadataProvider",
            "string",
            "android"
        )

        @ColorRes var moduleColor = R.color.colorCritical

        val moduleVersion = if (idConfigDefaultModuleMetadataProvider != 0) {
            try {
                // com.android.modulemetadata
                // com.google.android.modulemetadata
                val moduleProvider = MyApplication.getMyString(
                    idConfigDefaultModuleMetadataProvider
                )

                val versionName = MyApplication.instance.packageManager.getPackageInfo(
                    moduleProvider,
                    0
                ).versionName

                if ((versionName.contains('-') && isLatestPreviewAndroid(lld))
                    || versionName >= lld.android.stable.version
                ) {
                    moduleColor = R.color.colorNoProblem
                }

                MyApplication.getMyString(R.string.mainline_detail, versionName, moduleProvider)
            } catch (e: Exception) {
                Log.e(javaClass.simpleName, "Failed to get mainline version.", e)
                MyApplication.getMyString(R.string.result_not_supported)
            }
        } else {
            MyApplication.getMyString(R.string.result_not_supported)
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.mainline_title),
            moduleVersion,
            moduleColor
        )
    }

    private fun detectKernel(tempModels: ArrayList<MyModel>, lld: Lld) {
        val linuxVersionString = SYSTEM_PROPERTY_LINUX_VERSION
        val linuxVersion = Version(linuxVersionString)

        @ColorRes var linuxColor = R.color.colorCritical

        val versionsSupported = lld.linux.google.versions
        versionsSupported.forEach {
            if (linuxVersion.major == Version(it).major
                && linuxVersion.minor == Version(it).minor
            ) {
                linuxColor = if (linuxVersion.isAtLeast(it)) {
                    R.color.colorNoProblem
                } else {
                    R.color.colorWaring
                }

                return@forEach
            }
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.linux_title),
            MyApplication.getMyString(
                R.string.linux_version_detail,
                linuxVersionString,
                versionsSupported.joinToString("｜"),
                lld.linux.mainline.version
            ),
            linuxColor
        )
    }

    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    private fun detectSELinux(tempModels: ArrayList<MyModel>) {
        val seLinuxClassName = "android.os.SELinux"
        val isSELinuxEnabled = Class.forName(seLinuxClassName)
            .getDeclaredMethod("isSELinuxEnabled")
            .invoke(null) as Boolean
        val isSELinuxEnforced = Class.forName(seLinuxClassName)
            .getDeclaredMethod("isSELinuxEnforced")
            .invoke(null) as Boolean

        val seLinuxStatus = sh(CMD_SELINUX_STATUS, isAtLeastAndroid8())
        val seLinuxPolicyVersion = sh(CMD_SELINUX_POLICY_VERSION, isAtLeastAndroid8())

//        if (hasResult(seLinuxStatus)) {
//            // "permissive"
//        } else {
//            if (seLinuxStatus.output[0].endsWith(CMD_ERROR_PERMISSION_DENIED)) {
//                // enforcing
//            } else {
//                // permissive
//            }
//        }

        val buildSELinuxProp = getStringProperty(PROP_BUILD_SELINUX)
        val bootSELinuxProp = getStringProperty(PROP_BOOT_SELINUX)

        add(
            tempModels,
            MyApplication.getMyString(R.string.selinux_status),
            "isSELinuxEnabled: $isSELinuxEnabled\n" +
                    "isSELinuxEnforced: $isSELinuxEnforced\n" +
                    "seLinuxStatus: $seLinuxStatus\n" +
                    "seLinuxPolicyVersion: $seLinuxPolicyVersion\n" +
                    "buildSELinuxProp: $buildSELinuxProp\n" +
                    "bootSELinuxProp: $bootSELinuxProp",
            isSELinuxEnabled
        )
    }

    private fun detectAb(tempModels: ArrayList<MyModel>) {
        // val bootPartitions = sh(CMD_BOOT_PARTITION).output[0]

        val isAbUpdateSupported = getStringProperty(PROP_AB_UPDATE, isAtLeastAndroid7()).toBoolean()
        var abUpdateSupportedArgs = translate(isAbUpdateSupported)

        val abFinalResult =
            MyApplication.getMyString(
                R.string.ab_seamless_update_status_title
            )
        if (isAbUpdateSupported) {
            val slotSuffixResult = getStringProperty(PROP_SLOT_SUFFIX)
            val hasVndkVersion = slotSuffixResult.isNotEmpty()
            val slotSuffixUsing = if (hasVndkVersion) {
                slotSuffixResult
            } else {
                MyApplication.getMyString(android.R.string.unknownName)
            }

            abUpdateSupportedArgs += MyApplication.getMyString(
                R.string.current_using_ab_slot_result,
                slotSuffixUsing
            )
        }

        add(tempModels, abFinalResult, abUpdateSupportedArgs, isAbUpdateSupported)
    }

    private fun detectDynamicPartitions(tempModels: ArrayList<MyModel>) {
        val isDynamicPartitionsEnabled =
            getStringProperty(PROP_DYNAMIC_PARTITIONS, isAtLeastAndroid10()).toBoolean()
        val isDynamicPartitionsRetrofitEnabled =
            getStringProperty(PROP_DYNAMIC_PARTITIONS_RETROFIT, isAtLeastAndroid10()).toBoolean()

        var detail = translate(isDynamicPartitionsEnabled)
        if (isDynamicPartitionsRetrofitEnabled) {
            detail += MyApplication.getMyString(
                R.string.dynamic_partitions_enabled_retrofitted
            )
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.dynamic_partitions_status_title),
            detail,
            isDynamicPartitionsEnabled
        )
    }

    /** {@link android.util.FeatureFlagUtils} */
    private fun detectDsu(tempModels: ArrayList<MyModel>) {
        val isDsuEnabled = getStringProperty(
            FFLAG_OVERRIDE_PREFIX + DYNAMIC_SYSTEM,
            isAtLeastAndroid10()
        ).toBoolean()

        add(
            tempModels,
            MyApplication.getMyString(R.string.dsu_status_title),
            translate(isDsuEnabled),
            isDsuEnabled
        )
    }

    private fun detectTreble(tempModels: ArrayList<MyModel>) {
        val isTrebleEnabled =
            getStringProperty(PROP_TREBLE_ENABLED, isAtLeastAndroid8()).toBoolean()

        add(
            tempModels,
            MyApplication.getMyString(R.string.treble_status_title),
            translate(isTrebleEnabled),
            isTrebleEnabled
        )
    }

    private fun detectGsiCompatibility(tempModels: ArrayList<MyModel>) {
        val gsiCompatibilityResult = sh(CMD_VENDOR_NAMESPACE_DEFAULT_ISOLATED, isAtLeastAndroid9())
        var isCompatible = false
        val result = if (isShellResultSuccessful(gsiCompatibilityResult)) {
            val lineResult = gsiCompatibilityResult.output[0].split('=')
            isCompatible = lineResult.isNotEmpty() && lineResult[1].trim().toBoolean()
            if (isCompatible) {
                MyApplication.getMyString(R.string.result_compliant)
            } else {
                MyApplication.getMyString(R.string.result_not_compliant)
            }
        } else {
            MyApplication.getMyString(R.string.result_not_supported)
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.gsi_status_title),
            result,
            isCompatible
        )
    }

    private fun detectVndk(tempModels: ArrayList<MyModel>, lld: Lld) {
        val vndkVersionResult = getStringProperty(PROP_VNDK_VERSION, isAtLeastAndroid8())
        val hasVndkVersion = isPropertyValueNotEmpty(vndkVersionResult)

        @ColorRes val vndkColor: Int

        var isVndkBuiltInResult = translate(hasVndkVersion)
        if (hasVndkVersion) {
            val vndkVersion = if (hasVndkVersion) {
                vndkVersionResult
            } else {
                MyApplication.getMyString(android.R.string.unknownName)
            }

            val hasVndkLite = getStringProperty(PROP_VNDK_LITE).toBoolean()

            vndkColor = if (
                (vndkVersion == lld.android.stable.api || isLatestPreviewAndroid(lld)) && !hasVndkLite
            ) {
                R.color.colorNoProblem
            } else {
                R.color.colorWaring
            }

            isVndkBuiltInResult += MyApplication.getMyString(
                R.string.built_in_vndk_version_result,
                if (hasVndkLite) "$vndkVersion, Lite" else vndkVersion
            )
        } else {
            vndkColor = R.color.colorCritical
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.vndk_built_in_title),
            isVndkBuiltInResult,
            vndkColor
        )
    }

    private fun detectSar(tempModels: ArrayList<MyModel>) {
        val hasSystemRootImage =
            getStringProperty(PROP_SYSTEM_ROOT_IMAGE, isAtLeastAndroid9()).toBoolean()

        val mountDevRootResult = sh(CMD_MOUNT_DEV_ROOT, isAtLeastAndroid9())
        val hasMountDevRoot = isShellResultSuccessful(mountDevRootResult)

        val mountSystemResult = sh(CMD_MOUNT_SYSTEM, isAtLeastAndroid9() && !hasSystemRootImage)
        val hasMountSystem = isShellResultSuccessful(mountSystemResult)

        val isSar =
            isAtLeastAndroid9() && (hasSystemRootImage || hasMountDevRoot || !hasMountSystem)
        add(
            tempModels,
            MyApplication.getMyString(R.string.sar_status_title),
            translate(isSar),
            isSar
        )
    }

    private fun detectApex(tempModels: ArrayList<MyModel>) {
        val apexUpdatable = getStringProperty(PROP_APEX_UPDATABLE, isAtLeastAndroid10()).toBoolean()

        val flattenedApexMountedResult = sh(CMD_FLATTENED_APEX_MOUNT, isAtLeastAndroid10())
        val isFlattenedApexMounted = isShellResultSuccessful(flattenedApexMountedResult)

        val isApex = apexUpdatable || isFlattenedApexMounted
        val isLegacyFlattenedApex = !apexUpdatable && isFlattenedApexMounted

        var apexEnabledResult = translate(isApex)
        if (isLegacyFlattenedApex) {
            apexEnabledResult += MyApplication.getMyString(R.string.apex_legacy_flattened)
        }

        @ColorRes val apexColor = when {
            apexUpdatable -> R.color.colorNoProblem
            isLegacyFlattenedApex -> R.color.colorWaring
            else -> R.color.colorCritical
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.apex_status_title),
            apexEnabledResult,
            apexColor
        )
    }

    private fun detectDeveloperOptions(tempModels: ArrayList<MyModel>) {
        val isDeveloperOptionsDisabled = Settings.Global.getInt(
            MyApplication.instance.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            SETTINGS_DISABLED
        ) == SETTINGS_DISABLED

        add(
            tempModels,
            MyApplication.getMyString(R.string.developer_options_status_title),
            translateDisabled(isDeveloperOptionsDisabled),
            isDeveloperOptionsDisabled
        )
    }

    private fun detectAdb(tempModels: ArrayList<MyModel>) {
        val isAdbDebuggingDisabled = Settings.Global.getInt(
            MyApplication.instance.contentResolver,
            Settings.Global.ADB_ENABLED,
            SETTINGS_DISABLED
        ) == SETTINGS_DISABLED

        add(
            tempModels,
            MyApplication.getMyString(R.string.adb_debugging_status_title),
            translateDisabled(isAdbDebuggingDisabled),
            isAdbDebuggingDisabled
        )
    }

    private fun detectAdbAuthentication(tempModels: ArrayList<MyModel>) {
        val isAdbAuthenticationEnabled = getStringProperty(PROP_ADB_SECURE).toBoolean()

        add(
            tempModels,
            MyApplication.getMyString(R.string.adb_authentication_status_title),
            translateEnabled(isAdbAuthenticationEnabled),
            isAdbAuthenticationEnabled
        )
    }

    private fun detectEncryption(tempModels: ArrayList<MyModel>) {
        // val cryptoState = getStringProperty(PROP_CRYPTO_STATE)
        val devicePolicyManager =
            MyApplication.instance.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val storageEncryptionStatus = devicePolicyManager.storageEncryptionStatus
        @StringRes val result: Int
        @ColorRes val color: Int
        when (storageEncryptionStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> {
                result = R.string.result_encrypted
                color = R.color.colorNoProblem
            }
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> {
                result = R.string.result_encrypted_no_key_set
                color = R.color.colorWaring
            }
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> {
                result = R.string.result_not_encrypted
                color = R.color.colorCritical
            }
            DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> {
                result = R.string.result_not_supported
                color = R.color.colorCritical
            }
            else -> {
                result = R.string.result_not_supported
                color = R.color.colorCritical
            }
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.encryption_status_title),
            MyApplication.getMyString(result),
            color
        )
    }

    private fun detectToybox(tempModels: ArrayList<MyModel>, lld: Lld) {
        val toyboxVersionResult = sh(CMD_TOYBOX_VERSION, isAtLeastAndroid6())
        val hasToyboxVersion = isShellResultSuccessful(toyboxVersionResult)

        val toyboxVersion = if (hasToyboxVersion) {
            toyboxVersionResult.output[0]
        } else {
            translate(false)
        }

        @ColorRes val toyboxColor = if (hasToyboxVersion) {
            val toyboxRealVersionString = toyboxVersion.replace("toybox ", "")
            val toyboxRealVersion = Version(toyboxRealVersionString)
            when {
                toyboxRealVersion.isAtLeast(lld.toybox.stable.version) -> R.color.colorNoProblem
                toyboxRealVersion.isAtLeast(lld.toybox.support.version) -> R.color.colorWaring
                else -> R.color.colorCritical
            }
        } else {
            R.color.colorCritical
        }
        add(
            tempModels,
            MyApplication.getMyString(R.string.toybox_built_in_title),
            MyApplication.getMyString(
                R.string.toybox_built_in_detail,
                toyboxVersion,
                lld.toybox.stable.version,
                lld.toybox.support.version,
                lld.toybox.mainline.version,
                lld.toybox.master.version
            ),
            toyboxColor
        )
    }

    private fun getPackageInfo(packageName: String) =
        try {
            MyApplication.instance.packageManager.getPackageInfo(packageName, 0)
        } catch (e: Exception) {
            Log.d(javaClass.simpleName, "$packageName not found.")
            null
        }

    private fun detectWebView(tempModels: ArrayList<MyModel>, lld: Lld) {
        val builtInWebViewPackageInfo =
            getPackageInfo(WEB_VIEW_BUILT_IN_PACKAGE_NAME)
                ?: getPackageInfo(WEB_VIEW_STABLE_PACKAGE_NAME)
        val builtInWebViewVersion = builtInWebViewPackageInfo?.versionName ?: ""

        val implementWebViewPackageInfo =
            WebViewCompat.getCurrentWebViewPackage(MyApplication.instance)
        val implementWebViewVersion = implementWebViewPackageInfo?.versionName ?: ""

        val lldWebViewStable = lld.webView.stable.version
        @ColorRes val webViewColor = when {
            Version(builtInWebViewVersion).isAtLeast(lldWebViewStable) -> R.color.colorNoProblem
            Version(implementWebViewVersion).isAtLeast(lldWebViewStable) -> R.color.colorWaring
            else -> R.color.colorCritical
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.webview_title),
            """
            |${collectWebViewInfo(builtInWebViewPackageInfo, R.string.webview_built_in_version)}
            |
            |${collectWebViewInfo(implementWebViewPackageInfo, R.string.webview_implement_version)}
            |
            |${MyApplication.getMyString(
                R.string.webview_detail,
                lld.webView.stable.version,
                lld.webView.beta.version
            )}
            """.trimMargin(),
            webViewColor
        )
    }

    private fun getWebViewAppName(packageName: String?) = MyApplication.getMyString(
        when (packageName) {
            WEB_VIEW_BUILT_IN_PACKAGE_NAME -> R.string.webview_built_in

            WEB_VIEW_STABLE_PACKAGE_NAME -> R.string.webview_stable
            WEB_VIEW_BETA_PACKAGE_NAME -> R.string.webview_beta
            WEB_VIEW_DEV_IN_PACKAGE_NAME -> R.string.webview_developer
            WEB_VIEW_CANARY_PACKAGE_NAME -> R.string.webview_canary

            CHROME_STABLE_PACKAGE_NAME -> R.string.chrome_stable
            CHROME_BETA_PACKAGE_NAME -> R.string.chrome_beta
            CHROME_DEV_IN_PACKAGE_NAME -> R.string.chrome_developer
            CHROME_CANARY_PACKAGE_NAME -> R.string.chrome_canary

            else -> android.R.string.unknownName
        }
    )

    private fun collectWebViewInfo(packageInfo: PackageInfo?, @StringRes descId: Int): String {
        val desc = MyApplication.getMyString(descId)
        val appName = getWebViewAppName(packageInfo?.packageName)
        val versionName =
            packageInfo?.versionName ?: MyApplication.getMyString(android.R.string.unknownName)

        return "$desc\n$appName\n$versionName"
    }

    private fun detectOutdatedTargetSdkVersionApk(tempModels: ArrayList<MyModel>) {
        val systemApkList =
            MyApplication.instance.packageManager.getInstalledApplications(0).filter {
                it.flags and (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP or ApplicationInfo.FLAG_SYSTEM) > 0
            }.sortedWith(compareBy(ApplicationInfo::targetSdkVersion, ApplicationInfo::packageName))

        val firstApiLevelProp = getStringProperty(PROP_RO_PRODUCT_FIRST_API_LEVEL)

        val firstApiLevelLine = MyApplication.getMyString(
            R.string.outdated_target_version_sdk_version_apk_my_first_api_level,
            firstApiLevelProp
        )
        var result = firstApiLevelLine

        val outdatedSystemApkList = systemApkList.filter {
            it.targetSdkVersion < Build.VERSION.SDK_INT
        }

        outdatedSystemApkList.forEachIndexed { index, applicationInfo ->
            result += "(${applicationInfo.targetSdkVersion}) ${applicationInfo.packageName}"

            if (index != outdatedSystemApkList.size - 1) {
                result += "\n"
            }
        }

        val noOutdatedTotally = (result == firstApiLevelLine)

        @ColorRes val targetSdkVersionColor = if (noOutdatedTotally) {
            result += MyApplication.getMyString(R.string.outdated_target_version_sdk_version_apk_result_none)

            R.color.colorNoProblem
        } else {
            val outdatedFirstApiLevelSystemApkList =
                if (isPropertyValueNotEmpty(firstApiLevelProp) && firstApiLevelProp != "0") {
                    systemApkList.filter {
                        it.targetSdkVersion < firstApiLevelProp.toInt()
                    }
                } else {
                    outdatedSystemApkList
                }

            if (outdatedFirstApiLevelSystemApkList.isNotEmpty()) {
                R.color.colorCritical
            } else {
                R.color.colorWaring
            }
        }

        add(
            tempModels,
            MyApplication.getMyString(R.string.outdated_target_version_sdk_version_apk_title),
            result,
            targetSdkVersionColor
        )
    }
    // endregion [detect]

    private fun isPropertyValueNotEmpty(result: String) =
        result.isNotEmpty()
                && result != MyApplication.getMyString(R.string.result_not_supported)
                && result != MyApplication.getMyString(R.string.build_not_filled)

    private fun isShellResultSuccessful(result: MyShellResult) =
        result.isSuccess && result.output[0].isNotEmpty()

    private fun translate(condition: Boolean) = MyApplication.getMyString(
        if (condition) {
            R.string.result_supported
        } else {
            R.string.result_not_supported
        }
    )

    private fun translateEnabled(condition: Boolean) = translateDisabled(!condition)

    private fun translateDisabled(condition: Boolean) = MyApplication.getMyString(
        if (condition) {
            R.string.result_disabled
        } else {
            R.string.result_enabled
        }
    )

    private fun add(
        tempModels: ArrayList<MyModel>,
        title: String,
        detail: String?,
        condition: Boolean
    ) = tempModels.add(
        MyModel(
            title,
            detail.toString(),
            if (condition) {
                R.color.colorNoProblem
            } else {
                R.color.colorCritical
            }
        )
    )

    private fun add(
        tempModels: ArrayList<MyModel>,
        title: String,
        detail: String?,
        @ColorRes color: Int
    ) = tempModels.add(MyModel(title, detail.toString(), color))
}