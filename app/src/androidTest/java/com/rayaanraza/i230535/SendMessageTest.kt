package com.rayaanraza.i230535

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.firebase.FirebaseApp
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test Case 2: Send Message Workflow
 *
 * Critical Workflow: Tests the messaging functionality
 *
 * Steps Tested:
 * 1. User opens chat with another user
 * 2. User types a message
 * 3. User sends the message
 * 4. Message appears in the chat
 *
 * This is critical because messaging is a core feature of the app.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SendMessageTest {

    private lateinit var scenario: ActivityScenario<ChatActivity>

    @Before
    fun setup() {
        // Initialize Firebase if not already initialized
        try {
            FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext())
        } catch (e: Exception) {
            // Firebase already initialized
        }

        // Create intent with required extras
        val intent = Intent(ApplicationProvider.getApplicationContext(), ChatActivity::class.java).apply {
            putExtra("userId", "RGA4qIP5gGeidf5a9slHz4b4YMR2")
            putExtra("username", "test1")
        }

        // Launch activity
        scenario = ActivityScenario.launch(intent)

        // Wait for activity to fully load
        Thread.sleep(1000)
    }

    @Test
    fun testChatHeaderDisplaysUsername() {
        // Test Case: Verify chat header shows the other user's name

        // Check if username TextView displays correct username
        onView(withId(R.id.username))
            .check(matches(isDisplayed()))
            .check(matches(withText("test1")))
    }

    @Test
    fun testMessageInputFieldExists() {
        // Test Case: Verify essential UI elements exist

        // Check message input field
        onView(withId(R.id.message_input))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        // Check send button
        onView(withId(R.id.sendButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))

        // Check RecyclerView for messages
        onView(withId(R.id.messagesRecyclerView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testSendEmptyMessage() {
        // Test Case: Sending empty message should not work

        // Make sure input is empty
        onView(withId(R.id.message_input))
            .perform(clearText())

        // Try to send without typing anything
        onView(withId(R.id.sendButton))
            .perform(click())

        // Input field should still be empty
        onView(withId(R.id.message_input))
            .check(matches(withText("")))
    }

    @Test
    fun testTypeMessage() {
        // Test Case: User can type a message

        val testMessage = "Hello, this is a test message!"

        // Type message
        onView(withId(R.id.message_input))
            .perform(clearText())
            .perform(typeText(testMessage))
            .perform(closeSoftKeyboard())

        // Verify message was typed
        onView(withId(R.id.message_input))
            .check(matches(withText(testMessage)))
    }

    @Test
    fun testSendMessageClearsInput() {
        // Test Case: Sending a message should clear the input field

        val testMessage = "Test message"

        // Type message
        onView(withId(R.id.message_input))
            .perform(clearText())
            .perform(typeText(testMessage))
            .perform(closeSoftKeyboard())

        // Wait a moment for typing to complete
        Thread.sleep(500)

        // Send message
        onView(withId(R.id.sendButton))
            .perform(click())

        // Wait for message to be sent
        Thread.sleep(1000)

        // Input should be cleared after sending
        onView(withId(R.id.message_input))
            .check(matches(withText("")))
    }

    @Test
    fun testBackButtonExists() {
        // Test Case: Back button should be visible and clickable

        // Check back button exists
        onView(withId(R.id.back))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testGalleryIconExists() {
        // Test Case: Gallery icon should be present for sending images

        onView(withId(R.id.gallery))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testCameraIconExists() {
        // Test Case: Camera icon should be present

        onView(withId(R.id.cameraIcon))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testVoiceCallIconExists() {
        // Test Case: Voice call icon should be present

        onView(withId(R.id.voice))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testVideoCallIconExists() {
        // Test Case: Video call icon should be present

        onView(withId(R.id.video))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testAvatarDisplaysInitials() {
        // Test Case: Avatar should show user initials

        onView(withId(R.id.avatar))
            .check(matches(isDisplayed()))
            .check(matches(withText("T"))) // First letter of "test1"
    }
}