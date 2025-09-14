package com.rayaanraza.i230535

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class NavigationTest {

    private lateinit var scenario: ActivityScenario<login_sign>

    @Before
    fun setup() {
        Intents.init() // must be active before any navigation occurs
        scenario = ActivityScenario.launch(login_sign::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        Intents.release()
    }

    @Test
    fun login_to_signup_to_home_navigates_successfully() {
        onView(withId(R.id.Sign_up)).perform(click())
        Intents.intended(hasComponent(signup_page::class.java.name))

        onView(withId(R.id.create_account)).perform(click())
        Intents.intended(hasComponent(home_page::class.java.name))

        onView(withId(R.id.like)).check { _, e -> if (e != null) throw e }
    }
}
