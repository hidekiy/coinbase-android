package com.coinbase.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import com.bugsnag.android.Bugsnag;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.coinbase.android.db.DatabaseManager;
import com.coinbase.android.db.DelayedTransactionORM;
import com.coinbase.android.db.TransactionORM;
import com.coinbase.android.event.RefreshRequestedEvent;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.task.FetchTransactionTask;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Transaction;
import com.coinbase.api.entity.User;
import com.coinbase.api.exception.CoinbaseException;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;

public class TransactionDetailsFragment extends RoboFragment {

  @Inject
  protected Bus mBus;

  @Inject
  protected LoginManager mLoginManager;

  @Inject
  protected PINManager mPinManager;

  @Inject
  protected DatabaseManager mDbManager;

  public static final String ID = "id";
  public static final String DELAYED = "delayed";

  private static enum ActionType {
    RESEND,
    COMPLETE,
    CANCEL;
  }

  private String mPinReturnTransactionId;
  private ActionType mPinReturnActionType;

  ViewGroup mView;
  View mContainer;

  @InjectView(R.id.transactiondetails_amount)          TextView amount;
  @InjectView(R.id.transactiondetails_label_amount)    TextView amountLabel;
  @InjectView(R.id.transactiondetails_from)            TextView from;
  @InjectView(R.id.transactiondetails_to)              TextView to;
  @InjectView(R.id.transactiondetails_date)            TextView date;
  @InjectView(R.id.transactiondetails_status)          TextView status;
  @InjectView(R.id.transactiondetails_label_notes)         View notesLabel;
  @InjectView(R.id.transactiondetails_notes)           TextView notes;
  @InjectView(R.id.transactiondetails_action_positive) TextView positive;
  @InjectView(R.id.transactiondetails_action_negative) TextView negative;
  @InjectView(R.id.transactiondetails_actions)             View actions;

  private class ResendRequestTask extends com.coinbase.android.task.ResendRequestTask {
    ProgressDialog mDialog;

    @InjectResource(R.string.transactiondetails_action_success_resend)
    String successMessage;

    public ResendRequestTask(String txId) {
      super(getActivity(), txId);
    }

    @Override
    protected void onPreExecute() {
      mDialog = ProgressDialog.show(getActivity(), null, getString(R.string.transactiondetails_progress));
    }

    @Override
    public void onSuccess(Void v) {
      Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onException(Exception ex) {
      String result = "";
      if (ex instanceof CoinbaseException) {
        result = ex.getMessage();
      }

      Toast.makeText(
              context,
              String.format(
                      getActivity().getString(R.string.transactiondetails_action_error),
                      result
              ),
              Toast.LENGTH_LONG
      ).show();
    }

    @Override
    public void onFinally() {
      mDialog.dismiss();
    }
  }

  private class CompleteRequestTask extends com.coinbase.android.task.CompleteRequestTask {
    ProgressDialog mDialog;

    @InjectResource(R.string.transactiondetails_action_success_complete)
    String successMessage;

    public CompleteRequestTask(String txId) {
      super(getActivity(), txId);
    }

    @Override
    protected void onPreExecute() {
      mDialog = ProgressDialog.show(getActivity(), null, getString(R.string.transactiondetails_progress));
    }

    @Override
    public void onSuccess(Transaction t) {
      Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onException(Exception ex) {
      String result = "";
      if (ex instanceof CoinbaseException) {
        result = ex.getMessage();
      }

      Toast.makeText(
              context,
              String.format(
                      getActivity().getString(R.string.transactiondetails_action_error),
                      result
              ),
              Toast.LENGTH_LONG
      ).show();
    }

    @Override
    public void onFinally() {
      mDialog.dismiss();

      if (context instanceof TransactionDetailsActivity) {
        ((Activity) context).finish();
      } else {
        mBus.post(new RefreshRequestedEvent());
        ((TransactionsFragment) getParentFragment()).hideDetails(true);
      }
    }
  }

  private class CancelRequestTask extends com.coinbase.android.task.CancelRequestTask {
    ProgressDialog mDialog;

    @InjectResource(R.string.transactiondetails_action_success_cancel)
    String successMessage;

    public CancelRequestTask(String txId) {
      super(getActivity(), txId);
    }

    @Override
    protected void onPreExecute() {
      mDialog = ProgressDialog.show(getActivity(), null, getString(R.string.transactiondetails_progress));
    }

    @Override
    public void onSuccess(Void v) {
      Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onException(Exception ex) {
      String result = "";
      if (ex instanceof CoinbaseException) {
        result = ex.getMessage();
      }

      Toast.makeText(
              context,
              String.format(
                      getActivity().getString(R.string.transactiondetails_action_error),
                      result
              ),
              Toast.LENGTH_LONG
      ).show();
    }

    @Override
    public void onFinally() {
      mDialog.dismiss();

      if (context instanceof TransactionDetailsActivity) {
        ((Activity) context).finish();
      } else {
        mBus.post(new RefreshRequestedEvent());
        ((TransactionsFragment) getParentFragment()).hideDetails(true);
      }
    }
  }

  private class LoadTransactionTask extends FetchTransactionTask {

    public LoadTransactionTask(String txId) {
      super(getActivity(), txId);
    }

    @Override
    protected void onSuccess(Transaction transaction) {
      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        TransactionORM.insertOrUpdate(db, mLoginManager.getActiveAccountId(), transaction);
      } finally {
        mDbManager.closeDatabase();
      }

      mContainer.setVisibility(View.VISIBLE);
      fillViewsForTransaction(transaction);
    }

    @Override
    protected void onException(Exception ex) {
      super.onException(ex);

      // Error
      Toast.makeText(context, R.string.transactiondetails_error, Toast.LENGTH_SHORT).show();
      if (context instanceof MainActivity) {
        ((TransactionsFragment) getParentFragment()).hideDetails(true);
      } else {
        // Transaction details activity
        ((Activity) context).finish();
      }
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mView = null;
    mContainer = null;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    // Inflate base layout
    mView = (ViewGroup) inflater.inflate(R.layout.fragment_transactiondetails, container, false);
    return mView;
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mContainer = view.findViewById(R.id.transactiondetails_container);

    // Get arguments
    Bundle args = getArguments();
    if(args.containsKey("data")) {
      // From browser link
      // Fetch transaction information from internet.
      Uri uri = args.getParcelable("data");
      String transactionId = uri.getPath().substring("/transactions/".length());

      new LoadTransactionTask(transactionId).execute();
      mContainer.setVisibility(View.GONE);
    } else if (args.getBoolean(DELAYED)) {
      // Fetch transaction from cache
      Transaction transaction;
      final String transactionId = getArguments().getString(ID);
      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        transaction = DelayedTransactionORM.find(db, transactionId);
      } finally {
        mDbManager.closeDatabase();
      }

      fillViewsForDelayedTransaction(transaction);
    } else {
      // Fetch transaction from cache
      Transaction transaction;
      final String transactionId = getArguments().getString(ID);
      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        transaction = TransactionORM.find(db, transactionId);
      } finally {
        mDbManager.closeDatabase();
      }

      if (transaction != null) {
        fillViewsForTransaction(transaction);
      } else {
        new LoadTransactionTask(transactionId).execute();
      }
    }
  }

  private void fillViewsForDelayedTransaction(final Transaction tx) {
    // Amount
    String amountText = Utils.formatMoney(tx.getAmount().abs());
    amount.setText(amountText);
    int amountLabelResource;
    if (tx.isRequest()) {
      amountLabelResource = R.string.transactiondetails_amountrequested;
    } else {
      amountLabelResource = R.string.transactiondetails_amountsent;
    }
    amountLabel.setText(amountLabelResource);

    String fromText, toText;
    if (tx.isRequest()) {
      fromText = tx.getFrom();
      toText = getString(R.string.transaction_user_you);
    } else {
      fromText = getString(R.string.transaction_user_you);
      toText = tx.getTo();
    }
    from.setText(fromText);
    to.setText(toText);
    if (Build.VERSION.SDK_INT >= 11) {
      from.setTextIsSelectable(true);
      to.setTextIsSelectable(true);
    }

    // Date
    if (tx.getCreatedAt() != null) {
      date.setText(tx.getCreatedAt().toString("MMMM dd, yyyy, 'at' hh:mma zzz"));
    } else {
      date.setText(null);
    }
    date.setTypeface(FontManager.getFont(getActivity(), "Roboto-Light"));

    status.setText(getString(R.string.transaction_status_delayed));
    status.setBackgroundResource(R.drawable.transaction_delayed);

    // Notes
    String notesText = tx.getNotes();

    boolean noNotes = "null".equals(notesText) || notesText == null || "".equals(notesText);
    notes.setText(noNotes ? null : Html.fromHtml(notesText.replace("\n", "<br>")));
    notes.setVisibility(noNotes ? View.GONE : View.VISIBLE);

    notesLabel.setVisibility(noNotes ? View.INVISIBLE : View.VISIBLE);

    // Transaction has not actually been sent - show cancel button
    positive.setText(R.string.transactiondetails_delayed_cancel);
    negative.setVisibility(View.GONE);
    positive.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        cancelDelayedTransaction(tx);
      }
    });
  }

  private void fillViewsForTransaction(final Transaction tx) {
    // Get user ID
    String currentUserId = mLoginManager.getActiveUserId();

    boolean sentToMe = tx.getSender() == null || !currentUserId.equals(tx.getSender().getId());

    // Amount
    String amountText = Utils.formatMoney(tx.getAmount().abs());
    amount.setText(amountText);
    int amountLabelResource = R.string.transactiondetails_amountsent;
    if (tx.isRequest()) {
      amountLabelResource = R.string.transactiondetails_amountrequested;
    } else if(sentToMe) {
      amountLabelResource = R.string.transactiondetails_amountreceived;
    }
    amountLabel.setText(amountLabelResource);

    String toText   = getDisplayName(tx.getRecipient(), tx.getRecipientAddress());
    String fromText = getDisplayName(tx.getSender(), null);
    from.setText(fromText);
    to.setText(toText);
    if (Build.VERSION.SDK_INT >= 11) {
      from.setTextIsSelectable(true);
      to.setTextIsSelectable(true);
    }

    // Date
    if (tx.getCreatedAt() != null) {
      date.setText(tx.getCreatedAt().toString("MMMM dd, yyyy, 'at' hh:mma zzz"));
    } else {
      date.setText(null);
    }
    date.setTypeface(FontManager.getFont(getActivity(), "Roboto-Light"));

    Transaction.Status transactionStatus = tx.getStatus();
    int background = R.drawable.transaction_unknown;
    String readable = getString(R.string.transaction_status_error);

    switch (transactionStatus) {
      case COMPLETE:
        readable = getString(R.string.transaction_status_complete);
        background = R.drawable.transaction_complete;
        break;
      case PENDING:
        readable = getString(R.string.transaction_status_pending);
        background = R.drawable.transaction_pending;
        break;
    }

    status.setText(readable);
    status.setBackgroundResource(background);

    // Notes
    String notesText = tx.getNotes();

    boolean noNotes = "null".equals(notesText) || notesText == null || "".equals(notesText);
    notes.setText(noNotes ? null : Html.fromHtml(notesText.replace("\n", "<br>")));
    notes.setVisibility(noNotes ? View.GONE : View.VISIBLE);

    notesLabel.setVisibility(noNotes ? View.INVISIBLE : View.VISIBLE);

    // Buttons
    boolean senderOrRecipientIsExternal = tx.getSender() == null || tx.getRecipient() == null;
    negative.setTypeface(FontManager.getFont(getActivity(), "Roboto-Light"));
    positive.setTypeface(FontManager.getFont(getActivity(), "Roboto-Light"));

    if(!tx.isRequest() || senderOrRecipientIsExternal || tx.getStatus() != Transaction.Status.PENDING) {
      // No actions
      actions.setVisibility(View.GONE);
    } else if(sentToMe) {

      positive.setText(R.string.transactiondetails_request_resend);
      negative.setText(R.string.transactiondetails_request_cancel);

      negative.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          takeAction(ActionType.CANCEL, tx.getId());
        }
      });

      positive.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          takeAction(ActionType.RESEND, tx.getId());
        }
      });
    } else {

      positive.setText(String.format(getString(R.string.transactiondetails_request_send), amountText, toText));
      negative.setText(R.string.transactiondetails_request_decline);

      positive.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          takeAction(ActionType.COMPLETE, tx.getId());
        }
      });

      negative.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          takeAction(ActionType.CANCEL, tx.getId());
        }
      });
    }
  }

  private void takeAction(ActionType type, String transactionId) {
    if(!mPinManager.checkForEditAccess(getActivity())) {
      mPinReturnTransactionId = transactionId;
      mPinReturnActionType = type;
      return;
    }

    switch (type) {
      case RESEND:
        new ResendRequestTask(transactionId).execute();
        break;
      case CANCEL:
        new CancelRequestTask(transactionId).execute();
        break;
      case COMPLETE:
        new CompleteRequestTask(transactionId).execute();
        break;
    }
  }

  public void onPINPromptSuccessfulReturn() {
    if (mPinReturnActionType != null) {

      takeAction(mPinReturnActionType, mPinReturnTransactionId);
      mPinReturnActionType = null;
    }
  }

  private String getDisplayName (User user, String address) {
    String currentUserId = mLoginManager.getActiveUserId();
    String name = user == null ? null : user.getName();
    String email = user == null ? null : user.getEmail();
    boolean hasEmail = email != null && !email.equals("");

    if(user != null && currentUserId.equals(user.getId())) {
      return getString(R.string.transaction_user_you);
    }

    if(name != null) {
      String addition = "";

      if(!name.equals(email)) {
        addition = (hasEmail ? String.format(" (%s)", email) : "");
      }

      return name + addition;
    } else if(hasEmail) {
      return email;
    } else if(address != null) {
      return address;
    } else {
      return getString(R.string.transaction_user_external);
    }
  }

  private void cancelDelayedTransaction(Transaction tx) {
    SQLiteDatabase db = mDbManager.openDatabase();
    try {
      DelayedTransactionORM.delete(db, tx);
    } finally {
      mDbManager.closeDatabase();
    }

    // Show toast
    Toast.makeText(getActivity(), R.string.transactiondetails_delayed_canceled, Toast.LENGTH_SHORT).show();

    // Return to transactions list
    if (getActivity() instanceof MainActivity) {
      mBus.post(new RefreshRequestedEvent());
      ((TransactionsFragment) getParentFragment()).hideDetails(true);
    } else {
      getActivity().finish();
    }
  }
}
