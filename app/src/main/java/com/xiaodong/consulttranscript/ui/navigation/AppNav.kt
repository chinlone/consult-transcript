package com.xiaodong.consulttranscript.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xiaodong.consulttranscript.ui.consent.ConsentScreen
import com.xiaodong.consulttranscript.ui.home.HomeScreen
import com.xiaodong.consulttranscript.ui.importaudio.ImportScreen
import com.xiaodong.consulttranscript.ui.progress.ProgressScreen
import com.xiaodong.consulttranscript.ui.record.RecordScreen
import com.xiaodong.consulttranscript.ui.settings.SettingsScreen
import com.xiaodong.consulttranscript.ui.transcript.TranscriptScreen

/**
 * 页面跳转总表（本机自用版：无进入锁，打开即首页）：
 *   home → consent / transcript/{id} / settings
 *   consent → record/{id}（取消回 home）
 *   record → progress/{id}
 *   progress → transcript/{id}（转写完成自动跳）
 *   transcript → home（返回）/ 导出弹层（不换页）
 */
@Composable
fun AppNav(navController: NavHostController, requestMicPermission: (onResult: (Boolean) -> Unit) -> Unit) {
    NavHost(navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                onNew = { navController.navigate("consent") },
                onImport = { navController.navigate("import") },
                onOpen = { id -> navController.navigate("transcript/$id") },
                onSettings = { navController.navigate("settings") }
            )
        }

        composable("import") {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onFinished = { id -> navController.navigate("progress/$id") {
                    popUpTo("home") { inclusive = false }
                } }
            )
        }

        composable("consent") {
            ConsentScreen(
                onCancel = { navController.popBackStack() },
                onStart = { caseId -> navController.navigate("record/$caseId") }
            )
        }

        composable(
            "record/{caseId}",
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) { back ->
            RecordScreen(
                caseId = back.arguments?.getString("caseId").orEmpty(),
                requestMicPermission = requestMicPermission,
                onFinished = { id -> navController.navigate("progress/$id") {
                    popUpTo("home") { inclusive = false }
                } },
                onAbandon = { navController.popBackStack("home", inclusive = false) }
            )
        }

        composable(
            "progress/{caseId}",
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) { back ->
            val id = back.arguments?.getString("caseId").orEmpty()
            ProgressScreen(
                caseId = id,
                onDone = { navController.navigate("transcript/$id") {
                    popUpTo("home") { inclusive = false }
                } }
            )
        }

        composable(
            "transcript/{caseId}",
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) { back ->
            TranscriptScreen(
                caseId = back.arguments?.getString("caseId").orEmpty(),
                onBack = { navController.popBackStack("home", inclusive = false) }
            )
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
