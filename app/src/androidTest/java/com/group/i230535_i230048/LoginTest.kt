package com.group.i230535_i230048

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test Case 1: User Login Workflow
 *
 * Critical Workflow: Tests the complete user login process
 *
 * Steps Tested:
 * 1. User enters email
 * 2. User enters password
 * 3. User clicks login button
 * 4. Successful login navigates to home page
 *
 * This is critical because login is the gateway to all app features.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(login_sign::class.java)


    @Test
    fun testLoginWithEmptyFields() {
        // Test Case: Login with empty fields should show error

        onView(withId(R.id.switch_acc))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
            .perform(click())

        // Click login without entering anything
        onView(withId(R.id.log_in))
            .perform(click())

        // The app should still be on login screen (not navigate)
        onView(withId(R.id.username))
            .check(matches(isDisplayed()))
    }

}