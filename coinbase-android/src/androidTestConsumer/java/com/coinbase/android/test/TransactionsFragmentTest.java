package com.coinbase.android.test;

import com.coinbase.android.TestTransactionsFragmentActivity;
import com.coinbase.api.entity.AccountChangesResponse;
import com.coinbase.api.entity.Transaction;
import com.coinbase.api.entity.TransactionsResponse;

import static com.coinbase.android.test.MockResponses.mockAccountChange;
import static com.coinbase.android.test.MockResponses.mockAccountChanges;
import static com.coinbase.android.test.MockResponses.mockConfirmedReceivedTransaction;
import static com.coinbase.android.test.MockResponses.mockConfirmedSentTransaction;
import static com.coinbase.android.test.MockResponses.mockEmptyTransactionsResponse;
import static com.coinbase.android.test.MockResponses.mockPendingReceivedTransaction;
import static com.coinbase.android.test.MockResponses.mockReceivedPendingRequestTransaction;
import static com.coinbase.android.test.MockResponses.mockSentPendingRequestTransaction;
import static com.coinbase.android.test.MockResponses.mockTransactionsResponse;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TransactionsFragmentTest extends MockApiTest {
  public TransactionsFragmentTest() {
    super(TestTransactionsFragmentActivity.class);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testBalance() throws Exception {
    doReturn(mockAccountChanges()).when(mockCoinbase).getAccountChanges(anyInt());
    doReturn(mockEmptyTransactionsResponse()).when(mockCoinbase).getTransactions();

    startTestActivity();

    assertTrue(getSolo().searchText("฿1.0000"));
  }

  public void testReceivedConfirmed() throws Exception {
    Transaction receivedTransaction = mockConfirmedReceivedTransaction();
    TransactionsResponse transactionsResponse = mockTransactionsResponse(receivedTransaction);
    doReturn(transactionsResponse).when(mockCoinbase).getTransactions();
    doReturn(receivedTransaction).when(mockCoinbase).getTransaction(receivedTransaction.getId());

    AccountChangesResponse response = mockAccountChanges(mockAccountChange(receivedTransaction));
    doReturn(response).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("Test User sent you money"));
    assertFalse(getSolo().searchText("Pending"));
    assertTrue(getSolo().searchText("฿1.2300"));

    getSolo().clickOnText("Test User sent you money");

    assertTrue(getSolo().searchText("฿1.2300"));
    assertTrue(getSolo().searchText("COMPLETE"));
  }

  public void testReceivedPending() throws Exception {
    Transaction receivedTransaction = mockPendingReceivedTransaction();
    TransactionsResponse transactionsResponse = mockTransactionsResponse(receivedTransaction);
    doReturn(transactionsResponse).when(mockCoinbase).getTransactions();
    doReturn(receivedTransaction).when(mockCoinbase).getTransaction(receivedTransaction.getId());

    AccountChangesResponse response = mockAccountChanges(mockAccountChange(receivedTransaction));
    doReturn(response).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("Test User sent you money"));
    assertTrue(getSolo().searchText("PENDING"));
    assertTrue(getSolo().searchText("฿1.2300"));

    getSolo().clickOnText("Test User sent you money");
    assertTrue(getSolo().searchText("฿1.2300"));
    assertTrue(getSolo().searchText("PENDING"));
  }

  public void testSentConfirmed() throws Exception {
    Transaction sentTransaction = mockConfirmedSentTransaction();
    TransactionsResponse transactionsResponse = mockTransactionsResponse(sentTransaction);
    doReturn(transactionsResponse).when(mockCoinbase).getTransactions();
    doReturn(sentTransaction).when(mockCoinbase).getTransaction(sentTransaction.getId());

    AccountChangesResponse response = mockAccountChanges(mockAccountChange(sentTransaction));
    doReturn(response).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("You sent money to Test User"));
    assertTrue(getSolo().searchText("฿1.2300"));

    getSolo().clickOnText("You sent money to Test User");
    assertTrue(getSolo().searchText("฿1.2300"));
    assertTrue(getSolo().searchText("COMPLETE"));
  }

  public void testSentPendingRequest() throws Exception {
    Transaction pendingRequest = mockSentPendingRequestTransaction();
    TransactionsResponse transactionsResponse = mockTransactionsResponse(pendingRequest);
    doReturn(transactionsResponse).when(mockCoinbase).getTransactions();
    doReturn(pendingRequest).when(mockCoinbase).getTransaction(pendingRequest.getId());

    AccountChangesResponse response = mockAccountChanges(mockAccountChange(pendingRequest));
    doReturn(response).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("PENDING"));
    assertTrue(getSolo().searchText("฿1.2300"));
    assertTrue(getSolo().searchText("You requested money from Test User"));

    getSolo().clickOnText("You requested money from Test User");
    assertTrue(getSolo().searchText("฿1.2300"));
    assertTrue(getSolo().searchText("PENDING"));

    getSolo().clickOnText("Re-Send");
    getSolo().waitForText("Request re-sent.");
    verify(mockCoinbase, times(1)).resendRequest(pendingRequest.getId());

    getSolo().clickOnText("Cancel");
    getSolo().waitForText("Request cancelled.");
    verify(mockCoinbase, times(1)).deleteRequest(pendingRequest.getId());
  }

  public void testReceivedPendingRequest() throws Exception {
    Transaction pendingRequest = mockReceivedPendingRequestTransaction();
    TransactionsResponse transactionsResponse = mockTransactionsResponse(pendingRequest);
    doReturn(transactionsResponse).when(mockCoinbase).getTransactions();
    doReturn(pendingRequest).when(mockCoinbase).getTransaction(pendingRequest.getId());

    AccountChangesResponse response = mockAccountChanges(mockAccountChange(pendingRequest));
    doReturn(response).when(mockCoinbase).getAccountChanges(anyInt());

    startTestActivity();

    assertTrue(getSolo().searchText("PENDING"));
    assertTrue(getSolo().searchText("฿1.2300"));
    assertTrue(getSolo().searchText("Test User requested money from you"));

    getSolo().clickOnText("Test User requested money from you");
    assertTrue(getSolo().searchText("฿1.2300"));
    assertTrue(getSolo().searchText("PENDING"));

    getSolo().clickOnText("Send");
    getSolo().waitForText("Request completed.");
    verify(mockCoinbase, times(1)).completeRequest(pendingRequest.getId());
  }
}
