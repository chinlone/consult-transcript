package com.xiaodong.consulttranscript

import android.Manifest
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.rememberNavController
import com.xiaodong.consulttranscript.ui.navigation.AppNav
import com.xiaodong.consulttranscript.ui.theme.ConsultTheme

class MainActivity : FragmentActivity() {

    // 麦克风权限请求器：必须在 onCreate 之前注册（作为字段），否则旋转屏幕后回调丢失。
    // onResult 只回传是否授予，真正的「启动录音」由调用方在授予成功后再做——
    // 这样杜绝了「还没授权就开录音」导致的崩溃。
    private val micLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micResultCallback?.invoke(granted) }

    private var micResultCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ConsultTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    AppNav(navController = nav, requestMicPermission = { onResult ->
                        micResultCallback = onResult
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            // 已授权：直接回调，不再弹系统框
                            onResult(true)
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    })
                }
            }
        }
    }

    override fun onDestroy() {
        micResultCallback = null
        super.onDestroy()
    }
}
