package com.coinbase.android.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.coinbase.android.MainActivity;
import com.coinbase.android.R;
import com.coinbase.api.LoginManager;
import com.google.inject.Inject;

import roboguice.fragment.RoboDialogFragment;

public class SignOutFragment extends RoboDialogFragment {

  @Inject
  protected LoginManager mLoginManager;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setMessage(R.string.sign_out_confirm);

    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        mLoginManager.signout();
        getActivity().finish();
      }
    });

    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        // Dismiss
      }
    });

    return builder.create();
  }
}
