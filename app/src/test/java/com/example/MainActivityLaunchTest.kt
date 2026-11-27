package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun mainActivity_launchesWithoutFirebaseInit_andLogsInOwner() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FirebaseApp.getApps(context).toList().forEach { it.delete() }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("staff_username_input").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            composeRule.onNodeWithTag("staff_password_input").assertIsDisplayed()
            composeRule.onNodeWithTag("staff_username_input").performTextInput("151500")
            composeRule.onNodeWithTag("staff_password_input").performTextInput("8888")
            composeRule.onNodeWithTag("staff_login_button")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("nav_tab_DASHBOARD").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
        }
    }
}
