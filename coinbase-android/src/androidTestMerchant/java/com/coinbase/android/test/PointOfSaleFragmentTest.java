package com.coinbase.android.test;

import com.coinbase.android.TestPointOfSaleFragmentActivity;
import com.coinbase.api.entity.Button;
import com.coinbase.api.entity.Order;

import org.joda.money.Money;

import static com.coinbase.android.test.MockResponses.mockCurrentUser;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PointOfSaleFragmentTest extends MockApiTest {
  public PointOfSaleFragmentTest() {
    super(TestPointOfSaleFragmentActivity.class);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    doReturn(false).when(mockPreferences).isTippingEnabled();
    doReturn(mockCurrentUser()).when(mockCoinbase).getUser();
  }

  public void testAccept() throws Exception {
    Order mockSnickersOrder = new Order();
    mockSnickersOrder.setId("MockOrderID");
    mockSnickersOrder.setTotalNative(Money.parse("USD 0.99"));
    mockSnickersOrder.setTotalBtc(Money.parse("BTC 0.00165"));
    mockSnickersOrder.setStatus(Order.Status.NEW);

    doReturn(mockSnickersOrder).when(mockCoinbase).createOrder(any(Button.class));
    doReturn(mockSnickersOrder).when(mockCoinbase).getOrder(mockSnickersOrder.getId());

    startTestActivity();

    assertTrue(getSolo().searchText(mockCurrentUser().getMerchant().getCompanyName()));

    getSolo().pressSpinnerItem(0, 1); // Choose USD
    getSolo().enterText(0, "0.99");
    getSolo().enterText(1, "Snickers Bar");
    getSolo().clickOnText("Request");

    getSolo().waitForText("Waiting for payment");
    getSolo().sleep(4000);
    assertTrue(getSolo().searchText("0.99"));
    assertTrue(getSolo().searchText("0.00165"));

    mockSnickersOrder.setStatus(Order.Status.COMPLETED);

    getSolo().waitForText("COMPLETED");
    assertTrue(getSolo().searchText("Received payment"));
    assertTrue(getSolo().searchText("0.99"));

    getSolo().clickOnText("OK");

    verify(mockCoinbase, times(1)).createOrder(any(Button.class));
  }

  public void testFixedTipping() throws Exception {
    doReturn(true).when(mockPreferences).isTippingEnabled();

    Order mockRestaurantOrder = new Order();
    mockRestaurantOrder.setId("MockOrderID");
    mockRestaurantOrder.setTotalNative(Money.parse("USD 115.00"));
    mockRestaurantOrder.setTotalBtc(Money.parse("BTC 0.191667"));
    mockRestaurantOrder.setStatus(Order.Status.NEW);

    doReturn(mockRestaurantOrder).when(mockCoinbase).createOrder(any(Button.class));
    doReturn(mockRestaurantOrder).when(mockCoinbase).getOrder(mockRestaurantOrder.getId());

    startTestActivity();

    getSolo().pressSpinnerItem(0, 1); // Choose USD
    getSolo().enterText(0, "100");
    getSolo().enterText(1, "Fancy Steak");
    getSolo().clickOnText("Request");

    getSolo().clickOnText("Tip 15%");

    getSolo().waitForText("Waiting for payment");
    assertTrue(getSolo().searchText("115.00"));
    assertTrue(getSolo().searchText("0.191667"));

    mockRestaurantOrder.setStatus(Order.Status.COMPLETED);

    getSolo().waitForText("COMPLETED");
    assertTrue(getSolo().searchText("Received payment"));
    assertTrue(getSolo().searchText("115.00"));

    getSolo().clickOnText("OK");

    verify(mockCoinbase, times(1)).createOrder(any(Button.class));
  }

  public void testCustomTipping() throws Exception {
    doReturn(true).when(mockPreferences).isTippingEnabled();

    Order mockRestaurantOrder = new Order();
    mockRestaurantOrder.setId("MockOrderID");
    mockRestaurantOrder.setTotalNative(Money.parse("USD 111.00"));
    mockRestaurantOrder.setTotalBtc(Money.parse("BTC 0.185"));
    mockRestaurantOrder.setStatus(Order.Status.NEW);

    doReturn(mockRestaurantOrder).when(mockCoinbase).createOrder(any(Button.class));
    doReturn(mockRestaurantOrder).when(mockCoinbase).getOrder(mockRestaurantOrder.getId());

    startTestActivity();

    getSolo().pressSpinnerItem(0, 1); // Choose USD
    getSolo().enterText(0, "100");
    getSolo().enterText(1, "Fancy Steak");
    getSolo().clickOnText("Request");

    getSolo().clickOnText("Tip custom amount");

    getSolo().enterText(0, "11");
    assertTrue(getSolo().searchText("\\$11.00"));

    getSolo().clickOnText("OK");
    getSolo().waitForText("Waiting for payment");
    assertTrue(getSolo().searchText("111.00"));
    assertTrue(getSolo().searchText("0.185"));

    mockRestaurantOrder.setStatus(Order.Status.COMPLETED);

    getSolo().waitForText("COMPLETED");
    assertTrue(getSolo().searchText("Received payment"));
    assertTrue(getSolo().searchText("111.00"));

    getSolo().clickOnText("OK");

    verify(mockCoinbase, times(1)).createOrder(any(Button.class));
  }

  public void testMispaid() throws Exception {
    Order mockSnickersOrder = new Order();
    mockSnickersOrder.setId("MockOrderID");
    mockSnickersOrder.setTotalNative(Money.parse("USD 0.99"));
    mockSnickersOrder.setTotalBtc(Money.parse("BTC 0.00165"));
    mockSnickersOrder.setStatus(Order.Status.NEW);

    doReturn(mockSnickersOrder).when(mockCoinbase).createOrder(any(Button.class));
    doReturn(mockSnickersOrder).when(mockCoinbase).getOrder(mockSnickersOrder.getId());

    startTestActivity();

    getSolo().pressSpinnerItem(0, 1); // Choose USD
    getSolo().enterText(0, "0.99");
    getSolo().enterText(1, "Snickers Bar");
    getSolo().clickOnText("Request");

    getSolo().waitForText("Waiting for payment");

    mockSnickersOrder.setStatus(Order.Status.MISPAID);

    getSolo().waitForText("MISPAID");
    assertTrue(getSolo().searchText("Manual refund may be required"));

    verify(mockCoinbase, times(1)).createOrder(any(Button.class));
  }

  public void testError() throws Exception {
    Order mockSnickersOrder = new Order();
    mockSnickersOrder.setId("MockOrderID");
    mockSnickersOrder.setTotalNative(Money.parse("USD 0.99"));
    mockSnickersOrder.setTotalBtc(Money.parse("BTC 0.00165"));
    mockSnickersOrder.setStatus(Order.Status.NEW);

    doReturn(mockSnickersOrder).when(mockCoinbase).createOrder(any(Button.class));
    doReturn(mockSnickersOrder).when(mockCoinbase).getOrder(mockSnickersOrder.getId());

    startTestActivity();

    getSolo().pressSpinnerItem(0, 1); // Choose USD
    getSolo().enterText(0, "0.99");
    getSolo().enterText(1, "Snickers Bar");
    getSolo().clickOnText("Request");

    getSolo().waitForText("Waiting for payment");

    mockSnickersOrder.setStatus(Order.Status.EXPIRED);

    getSolo().waitForText("EXPIRED");
    verify(mockCoinbase, times(1)).createOrder(any(Button.class));
  }

  public void testDefaultTipInCorrectCurrency() throws Exception {
    doReturn(true).when(mockPreferences).isTippingEnabled();

    startTestActivity();

    getSolo().pressSpinnerItem(0, 1); // Choose USD
    getSolo().enterText(0, "100");
    getSolo().enterText(1, "Fancy Steak");
    getSolo().clickOnText("Request");

    getSolo().clickOnText("Tip custom amount");

    assertTrue(getSolo().searchText("\\$0.00"));
  }
}
