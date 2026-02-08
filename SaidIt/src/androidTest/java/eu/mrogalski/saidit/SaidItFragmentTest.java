package eu.mrogalski.saidit;

import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class SaidItFragmentTest {

    @Rule
    public ActivityScenarioRule<SaidItActivity> activityRule =
            new ActivityScenarioRule<>(SaidItActivity.class);

    @Test
    public void testSaveClipFlow_showsProgressDialog() {
        // 1. Click the "Save Clip" button to show the bottom sheet
        onView(withId(R.id.save_clip_button)).perform(ViewActions.click());

        // 2. In the bottom sheet, click a duration button.
        // We'll assume the layout for the bottom sheet has buttons with text like "15 seconds"
        // Let's click a common one, like "30 seconds"
        onView(withText("30 seconds")).perform(ViewActions.click());

        // 3. Verify that the "Saving Recording" progress dialog appears.
        // The dialog is a system window, so we check for the title text.
        onView(withText("Saving Recording")).check(matches(isDisplayed()));
    }
}