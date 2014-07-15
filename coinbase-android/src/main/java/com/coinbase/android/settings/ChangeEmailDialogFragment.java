package com.coinbase.android.settings;

import android.text.InputType;

import com.coinbase.android.Constants;
import com.coinbase.android.R;
import com.coinbase.android.dialog.InputTextDialogFragment;
import com.coinbase.api.entity.User;

import roboguice.inject.InjectResource;

public class ChangeEmailDialogFragment extends InputTextDialogFragment {
  @InjectResource(R.string.settings_account_change_name)
  protected String mTitle;

  @Override
  public int getInputType() {
    return InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
  }

  @Override
  public void onSubmit(String newEmail) {
    User user = new User();
    user.setEmail(newEmail);
    UpdateUserTask task = new UpdateUserTask(getActivity(), user, Constants.KEY_ACCOUNT_EMAIL, newEmail);
    task.execute();
  }

  @Override
  public String getTitle() {
    return mTitle;
  }
}
