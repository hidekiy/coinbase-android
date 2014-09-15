package com.coinbase.android.merchant;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.coinbase.android.CoinbaseActivity;
import com.coinbase.android.CoinbaseFragment;
import com.coinbase.android.FontManager;
import com.coinbase.android.R;
import com.coinbase.android.Utils;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.settings.PreferencesManager;
import com.coinbase.android.task.ApiTask;
import com.coinbase.android.util.BitcoinUri;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Merchant;
import com.coinbase.api.entity.Order;
import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockFragment;
import com.google.inject.Inject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;

import org.apache.commons.lang3.StringUtils;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import roboguice.RoboGuice;
import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;

public class PointOfSaleFragment extends RoboSherlockFragment implements CoinbaseFragment {

  private class CreateOrderTask extends ApiTask<Order> {

    protected com.coinbase.api.entity.Button mButton;

    @InjectResource(R.string.pos_result_failure_creation_exception)
    protected String exceptionMessageString;

    public CreateOrderTask(Context context, com.coinbase.api.entity.Button button) {
      super(context);
      mButton = button;
    }

    @Override
    public Order call() throws Exception {
      if (mButton.getName() == null || mButton.getName().trim().equals("")) {
        mButton.setName("Android point of sale transaction");
        mButton.setDescription("Android point of sale transaction");
      }

      return getClient().createOrder(mButton);
    }

    @Override
    public void onSuccess(Order order) {
      startAccepting(order);
    }

    @Override
    public void onException(Exception ex) {
      showResult(null, String.format(exceptionMessageString, ex.getMessage()), null);
    }

    @Override
    public void onFinally() {
      mCreatingTask = null;
    }
  }

  private class LoadMerchantInfoTask extends ApiTask<Merchant> {
    private Bitmap logo;

    public LoadMerchantInfoTask(Context context) {
      super(context);
    }

    @Override
    public Merchant call() throws Exception {
      Merchant result = getClient().getUser().getMerchant();

      if (result != null) {
        if (result.getLogo() != null) {
          String logoUrlString = result.getLogo().getSmall();
          try {
            URL logoUrl = logoUrlString.startsWith("/") ? new URL(new URL(mLoginManager.getClientBaseUrl()), logoUrlString) : new URL(logoUrlString);
            logo = BitmapFactory.decodeStream(logoUrl.openStream());
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      }

      return result;
    }

    @Override
    public void onSuccess(Merchant merchant) {
      for (View header : mHeaders) {
        header.setVisibility(View.VISIBLE);
      }

      String title = merchant.getCompanyName();
      for (TextView titleView : mHeaderTitles) {
        titleView.setText(title);
        titleView.setGravity(logo != null ? Gravity.RIGHT : Gravity.CENTER);
      }

      for (ImageView logoView : mHeaderLogos) {
        if (logo != null) {
          logoView.setVisibility(View.VISIBLE);
          logoView.setImageBitmap(logo);
        } else {
          logoView.setVisibility(View.GONE);
        }
      }
    }

    @Override
    public void onException(Exception ex) {
      // Data could not be loaded.
      for (View header : mHeaders) {
        header.setVisibility(View.GONE);
      }
    }
  }

  private class CheckStatusTask extends TimerTask {
    private Order mOrder;
    private int mTimesExecuted = 0;

    @Inject
    private LoginManager mLoginManager;

    public CheckStatusTask(Order order) {
      mOrder = order;
      RoboGuice.getInjector(mParent).injectMembers(this);
    }

    @Override
    public void run() {
      mTimesExecuted++;

      try {
        Order updatedOrder = mLoginManager.getClient().getOrder(mOrder.getId());

        if (updatedOrder == null || updatedOrder.getStatus() == null) {
          onOrderCheckError();
        }

        if (updatedOrder.getStatus() != Order.Status.NEW) {
          onPaymentAccepted(updatedOrder);
        } else {
          onOrderCheckCompleted(mTimesExecuted);
        }
      } catch (Exception ex) {
        // TODO remove this ugly kludge when we remove new order hotfix
        if (!ex.getMessage().contains("not found")) {
          ex.printStackTrace();
          onOrderCheckError();
        }
      }
    }
  }

  private static final int INDEX_MAIN = 0;
  private static final int INDEX_LOADING = 1;
  private static final int INDEX_ACCEPT = 2;
  private static final int INDEX_RESULT = 3;
  private static final int INDEX_ADD_TIP = 4;
  private static final int CHECK_PERIOD = 2000;

  @InjectView(R.id.pos_accept_desc)            private TextView mAcceptDesc;
  @InjectView(R.id.pos_accept_waiting)         private TextView mAcceptStatus;
  @InjectView(R.id.pos_result_status)          private TextView mResultStatus;
  @InjectView(R.id.pos_result_msg)             private TextView mResultMessage;
  @InjectView(R.id.pos_add_tip_title)          private TextView mTipTitle;
  @InjectView(R.id.pos_add_tip_custom_text)    private TextView mTipCustomText;
  @InjectView(R.id.pos_result_ok)              private Button mResultOK;
  @InjectView(R.id.pos_accept_cancel)          private Button mAcceptCancel;
  @InjectView(R.id.pos_loading_cancel)         private Button mLoadingCancel;
  @InjectView(R.id.pos_submit)                 private Button mSubmit;
  @InjectView(R.id.pos_add_tip_custom)         private Button mTipCustom;
  @InjectView(R.id.pos_add_tip_custom_confirm) private Button mTipCustomConfirm;
  @InjectView(R.id.pos_add_tip_custom_field)   private EditText mTipCustomField;
  @InjectView(R.id.pos_notes)                  private EditText mNotes;
  @InjectView(R.id.pos_amt)                    private EditText mAmount;
  @InjectView(R.id.pos_currency)               private Spinner mCurrency;
  @InjectView(R.id.pos_accept_qr)              private ImageView mAcceptQr;
  @InjectView(R.id.pos_menu)                   private ImageView mMenuButton;
  @InjectView(R.id.pos_flipper)                private ViewFlipper mFlipper;

  @InjectResource(R.string.title_point_of_sale)
  private String mTitle;

  @InjectResource(R.string.pos_accept_waiting_error)
  private String mOrderCheckErrorMessage;

  @InjectResource(R.string.pos_accept_waiting)
  private String mOrderCheckWaitingMessage;

  @InjectResource(R.string.pos_result_completed)
  private String mOrderCompletedMessage;

  @InjectResource(R.color.pos_waiting_bad)
  private int mOrderCheckErrorColor;

  @InjectResource(R.color.pos_waiting_good)
  private int mOrderCheckWaitingColor;

  @Inject
  private PINManager mPinManager;

  @Inject
  private LoginManager mLoginManager;

  @Inject
  private PreferencesManager mPreferencesManager;

  private String[] mCurrenciesArray;
  private View[] mHeaders;
  private TextView[] mHeaderTitles;
  private ImageView[] mHeaderLogos;
  private Activity mParent;

  private Timer mCheckStatusTimer;
  private CreateOrderTask mCreatingTask;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mParent = activity;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_point_of_sale, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mSubmit.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    mAcceptCancel.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    mLoadingCancel.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    mResultOK.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));

    DisplayMetrics metrics = getResources().getDisplayMetrics();
    int smallestWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
    int qrSize = smallestWidth - (int) (100 * metrics.density);
    mAcceptQr.getLayoutParams().height = mAcceptQr.getLayoutParams().width = qrSize;

    mMenuButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        if(!mPinManager.checkForEditAccess(getActivity())) {
          return;
        }
        mParent.openOptionsMenu();
      }
    });

    mLoadingCancel.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (mFlipper.getDisplayedChild() != INDEX_LOADING) return;
        if (mCreatingTask != null) {
          mCreatingTask.cancel(true);
        }
        switchToMain();
      }
    });

    mSubmit.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (mFlipper.getDisplayedChild() != INDEX_MAIN) return;

        Money amount = getAmount();

        if (amount.isZero()) {
          Toast.makeText(mParent, R.string.pos_empty_amount, Toast.LENGTH_SHORT).show();
          return;
        }

        if (mPreferencesManager.isTippingEnabled()) {
          goToAddTip();
        } else {
          startLoading(amount, getNotes());
        }
      }
    });

    mAcceptCancel.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (mFlipper.getDisplayedChild() != INDEX_ACCEPT) return;
        stopAccepting();
      }
    });

    mResultOK.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (mFlipper.getDisplayedChild() != INDEX_RESULT) return;
        switchToMain();
      }
    });

    initializeCurrencySpinner();
    mCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        updateCustomTipText();
        updateAmountHint();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Ignore
      }
    });

    // Headers
    int[] headers = { R.id.pos_accept_header, R.id.pos_main_header, R.id.pos_result_header,
            R.id.pos_add_tip_header };

    mHeaders = new View[headers.length];
    mHeaderTitles = new TextView[headers.length];
    mHeaderLogos = new ImageView[headers.length];
    for (int i = 0; i < headers.length; i++) {

      mHeaders[i] = view.findViewById(headers[i]);
      mHeaderTitles[i] = (TextView) mHeaders[i].findViewById(R.id.pos_header_name);
      mHeaderLogos[i] = (ImageView) mHeaders[i].findViewById(R.id.pos_header_logo);

      mHeaderTitles[i].setText(null);
      mHeaderLogos[i].setImageDrawable(null);
    }

    mTipTitle.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    mTipCustomConfirm.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    mTipCustomField.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    mTipCustomText.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    List<Button> tipButtons = new ArrayList<Button>();
    ViewGroup buttons1 = (ViewGroup) view.findViewById(R.id.pos_add_tip_buttons_1);
    for (int i = 0; i < buttons1.getChildCount(); i++) {
      tipButtons.add((Button) buttons1.getChildAt(i));
    }
    ViewGroup buttons2 = (ViewGroup) view.findViewById(R.id.pos_add_tip_buttons_2);
    for (int i = 0; i < buttons2.getChildCount(); i++) {
      tipButtons.add((Button) buttons2.getChildAt(i));
    }

    String btnText = getString(R.string.pos_tip_button);
    for (Button tipButton : tipButtons) {
      String percent = (String) tipButton.getTag();
      tipButton.setText(Html.fromHtml(String.format(btnText, percent)));
      tipButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          startLoading(
                  getAmount().plus(calculateTipAmount(getAmount(), new BigDecimal((String) view.getTag()))),
                  getNotes()
          );
        }
      });
    }
    mTipCustom.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    mTipCustom.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        mTipCustomConfirm.setVisibility(View.VISIBLE);
        mTipCustomField.setVisibility(View.VISIBLE);
        mTipCustomText.setVisibility(View.VISIBLE);
        view.setVisibility(View.GONE);
        mTipCustomField.requestFocus();
        setKeyboardVisible(true);
      }
    });
    mTipCustomField.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        updateCustomTipText();
      }

      @Override
      public void afterTextChanged(Editable editable) {
      }
    });
    mTipCustomConfirm.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        startLoading(
                getAmount().plus(calculateTipAmount(getAmount(), getCustomTipPercentage())),
                getNotes()
        );
      }
    });

    updateCustomTipText();
  }

  private Money calculateTipAmount(Money amount, BigDecimal percent) {
    BigDecimal p = percent.multiply(new BigDecimal("0.01"));
    return amount.multipliedBy(p, RoundingMode.HALF_EVEN);
  }

  private void updateCustomTipText() {
    Money customTip = calculateTipAmount(getAmount(), getCustomTipPercentage());
    mTipCustomText.setText(getString(R.string.pos_tip_custom_text, Utils.formatMoney(customTip)));
  }

  private void startLoading(Money total, String notes) {
    com.coinbase.api.entity.Button button = new com.coinbase.api.entity.Button();
    button.setDescription(notes);
    button.setName(notes);
    button.setPrice(total);


    mCreatingTask = new CreateOrderTask(mParent, button);
    mCreatingTask.execute();

    mTipCustomConfirm.setVisibility(View.GONE);
    mTipCustomField.setVisibility(View.GONE);
    mTipCustomText.setVisibility(View.GONE);
    mTipCustom.setVisibility(View.VISIBLE);
    mTipCustomField.setText(null);
    mFlipper.setDisplayedChild(INDEX_LOADING);

    mAmount.setText(null);
  }

  private void initializeCurrencySpinner() {
    CurrencyUnit nativeCurrency = mPreferencesManager.getNativeCurrency();

    mCurrenciesArray = new String[] {
            "BTC",
            nativeCurrency.getCurrencyCode(),
    };

    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
            mParent, R.layout.fragment_transfer_currency, Arrays.asList(mCurrenciesArray)) {

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {

        TextView view = (TextView) super.getView(position, convertView, parent);
        view.setText(mCurrenciesArray[position]);
        return view;
      }

      @Override
      public View getDropDownView(int position, View convertView, ViewGroup parent) {

        TextView view = (TextView) super.getDropDownView(position, convertView, parent);
        view.setText(mCurrenciesArray[position]);
        return view;
      }
    };
    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mCurrency.setAdapter(arrayAdapter);
  }

  private void goToAddTip() {
    CharSequence title = getString(R.string.pos_tip_title);
    title = Html.fromHtml(String.format((String)title, Utils.formatMoney(getAmount())));
    mTipTitle.setText(title);
    mFlipper.setDisplayedChild(INDEX_ADD_TIP);
    setKeyboardVisible(false);
  }

  private void onOrderCheckError() {
    if (mCheckStatusTimer != null) {
      mCheckStatusTimer.cancel();
      mCheckStatusTimer = null;
    }

    mParent.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mAcceptStatus.setTextColor(Color.WHITE);
        mAcceptStatus.setBackgroundColor(mOrderCheckErrorColor);
        mAcceptStatus.setText(mOrderCheckErrorMessage);
      }
    });
  }

  private void onOrderCheckCompleted(int numTries) {
    int numElipses = ((numTries - 1) % 3) + 1;
    String elipses = StringUtils.repeat('.', numElipses) + StringUtils.repeat(' ', 3 - numElipses);

    final String text = mOrderCheckWaitingMessage.replace("...", "") + elipses;

    mParent.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mAcceptStatus.setTextColor(Color.BLACK);
        mAcceptStatus.setBackgroundColor(mOrderCheckWaitingColor);
        mAcceptStatus.setText(text);
      }
    });
  }

  private void onPaymentAccepted(final Order order) {
    if (mCheckStatusTimer != null) {
      mCheckStatusTimer.cancel();
      mCheckStatusTimer = null;
    }

    mParent.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        showResult(order.getStatus(), null, order);
      }
    });
  }

  private void showResult(Order.Status status, String message, Order order) {
    CharSequence text;
    int color;

    if (status == null) {
      color = R.color.pos_result_error;
      text = message;
    } else {
      switch (status) {
        case COMPLETED:
          text = Html.fromHtml(String.format("<b>Order %1$s</b><br>%2$s",
                  order.getId(),
                  String.format(mOrderCompletedMessage, Utils.formatMoney(order.getTotalNative()))
          ));
          color = R.color.pos_result_completed;
          break;
        case MISPAID:
          text = getString(R.string.pos_result_mispaid);
          color = R.color.pos_result_mispaid;
          break;
        default:
          color = R.color.pos_result_error;
          text = message;
      }
    }

    float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, mParent.getResources().getDisplayMetrics());
    ShapeDrawable background = new ShapeDrawable(new RoundRectShape(
            new float[] { radius, radius, radius, radius, radius, radius, radius, radius }, null, null));
    background.getPaint().setColor(mParent.getResources().getColor(color));
    mResultStatus.setBackgroundDrawable(background);

    mResultStatus.setText(status.toString().toUpperCase());
    mResultMessage.setText(text);
    mFlipper.setDisplayedChild(INDEX_RESULT);
    setKeyboardVisible(false);
    ((Vibrator) mParent.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(500);
  }

  @Override
  public void onSwitchedTo() {
    if (mFlipper.getDisplayedChild() == INDEX_MAIN) {
      mAmount.requestFocus();
    }

    getSherlockActivity().getSupportActionBar().hide();
  }

  @Override
  public void onPINPromptSuccessfulReturn() {
    // PIN menu is only opened when trying to access settings
    new Handler().postDelayed(new Runnable() {
      public void run() {
        mParent.openOptionsMenu();
      }
    }, 1000);
  }

  @Override
  public String getTitle() {
    return mTitle;
  }

  private void startAccepting(Order order) {
    Money btcAmount = order.getTotalBtc();
    Money nativeAmount = order.getTotalNative();

    BitcoinUri uri = new BitcoinUri();
    uri.setAmount(btcAmount.getAmount());
    uri.setAddress(order.getReceiveAddress());

    String uriString = uri.toString();

    String amountString;

    if (!nativeAmount.getCurrencyUnit().getCurrencyCode().equalsIgnoreCase("BTC")) {
      amountString = String.format(
              "%1$s (%2$s)",
              Utils.formatMoney(btcAmount),
              Utils.formatMoney(nativeAmount)
      );
    } else {
      amountString = Utils.formatMoney(btcAmount);
    }

    Bitmap bitmap;
    try {
      bitmap = Utils.createBarcode(uriString, BarcodeFormat.QR_CODE, 512, 512);
    } catch (WriterException e) {
      e.printStackTrace();
      bitmap = null;
    }
    mAcceptQr.setImageBitmap(bitmap);
    mAcceptDesc.setText(getString(R.string.pos_accept_message, amountString));

    mCheckStatusTimer = new Timer();
    mCheckStatusTimer.schedule(new CheckStatusTask(order), CHECK_PERIOD, CHECK_PERIOD);

    mFlipper.setDisplayedChild(INDEX_ACCEPT);
    setKeyboardVisible(false);
  }

  private void setKeyboardVisible(boolean visible) {
    InputMethodManager inputMethodManager = (InputMethodManager) mParent.getSystemService(Context.INPUT_METHOD_SERVICE);

    if(visible) {
      inputMethodManager.showSoftInput(mAmount, InputMethodManager.SHOW_FORCED);
    } else {
      inputMethodManager.hideSoftInputFromWindow(mParent.findViewById(android.R.id.content).getWindowToken(), 0);
    }
  }

  private void stopAccepting() {
    if (mCheckStatusTimer != null) {
      mCheckStatusTimer.cancel();
      mCheckStatusTimer = null;
    }
    showResult(Order.Status.CANCELED, getString(R.string.pos_result_failure_cancel), null);
  }

  private Money getAmount() {
    Money quantity = Money.of(getCurrency(), 0);
    try {
      quantity = Money.of(
              getCurrency(),
              new BigDecimal(mAmount.getText().toString())
      );
      // Only positive quantities are valid
      if (quantity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        quantity = null;
      }
    } catch (Exception ex) {}
    return quantity;
  }

  private String getNotes() {
    return mNotes.getText().toString();
  }

  private BigDecimal getCustomTipPercentage() {
    BigDecimal result;
    try {
      result = new BigDecimal(mTipCustomField.getText().toString());
    } catch (Exception ex) {
      result = BigDecimal.ZERO;
    }
    return result;
  }

  private CurrencyUnit getCurrency() {
    return CurrencyUnit.of((String) mCurrency.getSelectedItem());
  }

  private void switchToMain() {
    mFlipper.setDisplayedChild(INDEX_MAIN);
    mAmount.requestFocus();
    setKeyboardVisible(true);
  }

  private void updateAmountHint() {
    // Update text hint
    String currency = mCurrenciesArray[mCurrency.getSelectedItemPosition()];
    mAmount.setHint(String.format(
            getString(R.string.pos_amt),
            getCurrency().getCurrencyCode().toUpperCase()
    ));
  }

  public void refresh() {
    new LoadMerchantInfoTask(mParent).execute();
  }

  @Override
  public void onStart() {
    super.onStart();

    mNotes.setText(mPreferencesManager.getSavedMerchantNotes());
    mCurrency.setSelection(mPreferencesManager.posUsesBtc() ? 0 : 1);

    refresh();
  }

  @Override
  public void onStop() {
    mPreferencesManager.saveMerchantNotes(getNotes());
    mPreferencesManager.setPosUsesBtc(mCurrency.getSelectedItemPosition() == 0);

    super.onStop();
  }

}
