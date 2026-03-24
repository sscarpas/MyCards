package com.example.mycards

import android.os.Bundle
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mycards.CardImagePickerHelper.PickerTarget
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso tests for [AddCardFragment].
 *
 * Covers the Finish button enabled/disabled state when a front image
 * is set and then removed via the ✕ button (PE-001 regression guard).
 */
@RunWith(AndroidJUnit4::class)
class AddCardFragmentTest {

    /**
     * Given: card name is entered and front image state is seeded programmatically.
     * When:  the ✕ remove-front button is tapped.
     * Then:  the Finish button becomes disabled.
     */
    @Test
    fun finishButton_disabledAfterFrontImageRemoved() {
        val scenario = launchFragmentInContainer<AddCardFragment>(
            fragmentArgs = Bundle(),
            themeResId   = R.style.Theme_MyCards
        )

        // Seed a front-image URI and sync UI state on the main thread.
        scenario.onFragment { fragment ->
            fragment.frontImageUri = "file:///test/fake_front.jpg"
            fragment.setImageState(PickerTarget.FRONT, hasImage = true)
            fragment.updateFinishButton()
        }

        // Enter a card name so the Finish button would otherwise be enabled.
        onView(withId(R.id.edit_card_name))
            .perform(replaceText("Test Card"))

        // Pre-condition: Finish should now be enabled.
        onView(withId(R.id.btn_finish))
            .check(matches(isEnabled()))

        // Tap the ✕ remove-front button.
        onView(withId(R.id.btn_remove_front))
            .perform(click())

        // Finish must be disabled once the front image is gone.
        onView(withId(R.id.btn_finish))
            .check(matches(not(isEnabled())))
    }

    /**
     * Verify Finish is disabled when only a card name is entered but no front image is set.
     */
    @Test
    fun finishButton_disabledWithNameButNoFrontImage() {
        launchFragmentInContainer<AddCardFragment>(
            fragmentArgs = Bundle(),
            themeResId   = R.style.Theme_MyCards
        )

        onView(withId(R.id.edit_card_name))
            .perform(replaceText("Some Card"))

        onView(withId(R.id.btn_finish))
            .check(matches(not(isEnabled())))
    }
}

