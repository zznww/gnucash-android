package org.gnucash.android.ui.homescreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.gnucash.android.R;
import org.gnucash.android.ui.transaction.ScheduledActionsActivity;

/**
 * Created by zz on 2015/12/5.
 */
public class ShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The meat of our shortcut
        Intent shortcutIntent = new Intent(this, ScheduledActionsActivity.class);
        Intent.ShortcutIconResource iconResource = Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);

        // The result we are passing back from this activity
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "Add Txn");
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        setResult(RESULT_OK, intent);

        finish(); // Must call finish for result to be returned immediately
    }
}
