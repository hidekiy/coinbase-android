package com.coinbase.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Account;
import com.google.inject.Inject;

import java.util.List;

import roboguice.fragment.RoboDialogFragment;

public class AccountsFragment extends RoboDialogFragment {

  public static interface ParentActivity {
    public void onAccountChosen(Account account);
  }

  @Inject
  protected LoginManager mLoginManager;

  boolean widgetMode = false;
  int selectedIndex = -1;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.accounts_title);

    widgetMode = getArguments() == null ? false : getArguments().getBoolean("widgetMode");

    final List<Account> accounts = mLoginManager.getAccounts();
    final String activeAccountId  = mLoginManager.getActiveAccountId();

    final String[] accountNames = new String[accounts.size()];

    for (int i = 0; i < accounts.size(); ++i) {
      if (accounts.get(i).getId().equals(activeAccountId)) {
        selectedIndex = i;
      }
      accountNames[i] = accounts.get(i).getName();
    }

    builder.setSingleChoiceItems(accountNames,
            selectedIndex, new DialogInterface.OnClickListener() {

              public void onClick(DialogInterface dialog, int which) {
                selectedIndex = which;
              }
            })
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                // Select account
                ParentActivity activity = (ParentActivity) getActivity();
                activity.onAccountChosen(accounts.get(selectedIndex));
              }
            })
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
                if(widgetMode) {
                  getActivity().finish();
                }
              }
            });
    return builder.create();
  }
}
