package com.rayaanraza.i230535

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class button_test {

    private lateinit var scenario: ActivityScenario<home_page>

    @Before
    fun setup() {
        // Init Espresso-Intents and launch home_page
        Intents.init()
        scenario = ActivityScenario.launch(home_page::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        Intents.release()
    }

    @Test
    fun click_like_button_toggles() {
        onView(withId(R.id.like)).perform(click())
        onView(withId(R.id.like)).check(matches(isDisplayed()))
    }



    @Test
    fun navigate_home_to_profile() {
        onView(withId(R.id.profile)).perform(click())
        Intents.intended(hasComponent(my_profile::class.java.name))
    }

    @Test
    fun navigate_home_to_search_feed() {
        onView(withId(R.id.search)).perform(click())
        Intents.intended(hasComponent(search_feed::class.java.name))
    }
}
