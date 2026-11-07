package com.wordpilot.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wordpilot.assistant.ui.dictionary.DictionaryScreen
import com.wordpilot.assistant.ui.editor.EditorScreen
import com.wordpilot.assistant.ui.history.HistoryScreen
import com.wordpilot.assistant.ui.home.HomeScreen
import com.wordpilot.assistant.ui.rewrite.RewriteScreen
import com.wordpilot.assistant.ui.settings.ApiSettingsScreen
import com.wordpilot.assistant.ui.settings.AppearanceSettingsScreen
import com.wordpilot.assistant.ui.settings.SettingsScreen
import com.wordpilot.assistant.ui.onboarding.OnboardingPagerScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val EDITOR = "editor"
    const val EDITOR_WITH_DOC = "editor/{docId}"
    const val SETTINGS = "settings"
    const val DICTIONARY = "dictionary"
    const val HISTORY = "history"
    const val REWRITE = "rewrite"
    const val API_SETTINGS = "api_settings"
    const val MODEL_MANAGEMENT = "api_settings"
    const val BUBBLE_CUSTOMIZATION = "bubble_customization"
    const val APPEARANCE_STUDIO = "bubble_customization"
    const val AI_WINDOW_STUDIO = "ai_window_studio"
}

@Composable
fun WordPilotNavGraph(
    startDestination: String = Routes.HOME,
    initialSharedText: String? = null
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingPagerScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToEditor = { navController.navigate(Routes.EDITOR) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToDictionary = { navController.navigate(Routes.DICTIONARY) },
                onNavigateToApiSettings = { navController.navigate(Routes.API_SETTINGS) },
                onNavigateToBubbleCustomization = { navController.navigate(Routes.BUBBLE_CUSTOMIZATION) },
                onNavigateToAiWindowStudio = { navController.navigate(Routes.AI_WINDOW_STUDIO) }
            )
        }
        composable(Routes.EDITOR) {
            EditorScreen(
                initialText = initialSharedText,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRewrite = { text -> 
                    navController.navigate(Routes.REWRITE)
                }
            )
        }
        composable(
            Routes.EDITOR_WITH_DOC,
            arguments = listOf(navArgument("docId") { type = NavType.IntType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getInt("docId") ?: -1
            EditorScreen(
                documentId = docId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRewrite = { text ->
                    navController.navigate(Routes.REWRITE)
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDictionary = { navController.navigate(Routes.DICTIONARY) },
                onNavigateToApiSettings = { navController.navigate(Routes.API_SETTINGS) },
                onNavigateToBubbleCustomization = { navController.navigate(Routes.BUBBLE_CUSTOMIZATION) },
                onNavigateToAiWindowStudio = { navController.navigate(Routes.AI_WINDOW_STUDIO) }
            )
        }
        composable(Routes.BUBBLE_CUSTOMIZATION) {
            AppearanceSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.DICTIONARY) {
            DictionaryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenDocument = { docId -> navController.navigate("editor/$docId") }
            )
        }
        composable(Routes.REWRITE) {
            RewriteScreen(onNavigateBack = { navController.popBackStack() })
        }
                composable(Routes.AI_WINDOW_STUDIO) {
            com.wordpilot.assistant.ui.settings.AiWindowCustomizationScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.API_SETTINGS) {
            ApiSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
