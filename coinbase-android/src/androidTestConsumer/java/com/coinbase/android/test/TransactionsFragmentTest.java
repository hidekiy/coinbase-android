package com.coinbase.android.test;

import com.coinbase.android.R;
import com.coinbase.android.TestTransactionsFragmentActivity;
import com.coinbase.android.TestTransferFragmentActivity;
import com.coinbase.api.entity.AccountChangesResponse;
import com.coinbase.api.entity.Transaction;
import com.coinbase.api.entity.TransactionsResponse;
import com.robotium.solo.Solo;

import org.joda.money.Money;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import static com.coinbase.android.test.MockResponses.mockAccountChange;
import static com.coinbase.android.test.MockResponses.mockAccountChanges;
import static com.coinbase.android.test.MockResponses.mockConfirmedReceivedTransaction;
import static com.coinbase.android.test.MockResponses.mockConfirmedSentTransaction;
import static com.coinbase.android.test.MockResponses.mockContacts;
import static com.coinbase.android.test.MockResponses.mockExchangeRates;
import static com.coinbase.android.test.MockResponses.mockPendingReceivedTransaction;
import static com.coinbase.android.test.MockResponses.mockUser;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TransactionsFragmentTest extends MockApiTest {
  public TransactionsFragmentTest() {
    super(TestTransactionsFragmentActivity.class);
  }

  public void testBalance() throws Exception {
    doReturn(mockAccountChanges()).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("฿1.0000"));
  }

  public void testReceivedConfirmed() throws Exception {
    Transaction receivedTransaction = mockConfirmedReceivedTransaction();

    AccountChangesResponse response = mockAccountChanges(mockAccountChange(receivedTransaction));
    doReturn(response).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("Test User sent you money"));
    assertFalse(getSolo().searchText("Pending"));
    assertTrue(getSolo().searchText("฿1.2300"));

    getSolo().clickOnText("You sent money to Test User");

    assertTrue(getSolo().searchText("฿1.2300"));
  }

  public void testReceivedPending() throws Exception {
    Transaction receivedTransaction = mockPendingReceivedTransaction();

    AccountChangesResponse response = mockAccountChanges(mockAccountChange(receivedTransaction));
    doReturn(response).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("Test User sent you money"));
    assertTrue(getSolo().searchText("PENDING"));
    assertTrue(getSolo().searchText("฿1.2300"));

    getSolo().clickOnText("You sent money to Test User");
    assertTrue(getSolo().searchText("฿1.2300"));
  }

  public void testSentConfirmed() throws Exception {
    Transaction sentTransaction = mockConfirmedSentTransaction();

    AccountChangesResponse response = mockAccountChanges(mockAccountChange(sentTransaction));
    doReturn(response).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("You sent money to Test User"));
    assertTrue(getSolo().searchText("฿1.2300"));

    getSolo().clickOnText("You sent money to Test User");
    assertTrue(getSolo().searchText("฿1.2300"));
  }
}
