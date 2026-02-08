package eu.mrogalski.saidit;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;

@RunWith(AndroidJUnit4.class)
public class SaidItFragmentTest {

    @Rule
    public ActivityScenarioRule<SaidItActivity> activityRule =
            new ActivityScenarioRule<>(SaidItActivity.class);

    @Before
    public void setUp() {
        // Disable the "How To Use" screen by setting the "is_first_run" flag to false
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(PACKAGE_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("is_first_run", false).apply();
    }

    @Test
    public void testSaveClipFlow_showsProgressDialog() {
        // It can take a moment for the service to connect and update the UI.
        // We'll poll for a UI change that indicates the service is ready.
        long giveUpAt = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < giveUpAt) {
            try {
                onView(withId(R.id.history_size)).check(matches(withText("0:00")));
                // The view is now in the expected state, so we can exit the loop.
                break;
            } catch (Throwable e) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    // Do nothing.
                }
            }
        }

        // 1. Click the "Save Clip" button to show the bottom sheet
        onView(withId(R.id.save_clip_button)).perform(ViewActions.click());

        // 2. In the bottom sheet, click a duration button.
        // The layout for the bottom sheet has chips for duration.
        // Let's click "1 minute"
        onView(withText("1 minute")).perform(ViewActions.click());

        // Click the save button in the bottom sheet
        onView(withId(R.id.save_button)).perform(ViewActions.click());

        // 3. Verify that the "Saving Recording" progress dialog appears.
        // The dialog is a system window, so we check for the title text.
        onView(allOf(withText("Saving Recording"), withId(R.id.alertTitle)))
                .check(matches(isDisplayed()));
        onView(withText("Please wait...")).check(matches(isDisplayed()));
    }
}
