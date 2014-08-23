package com.coinbase.android.test;

import com.coinbase.api.entity.Account;
import com.coinbase.api.entity.AccountChange;
import com.coinbase.api.entity.AccountChangesResponse;
import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressResponse;
import com.coinbase.api.entity.AddressesResponse;
import com.coinbase.api.entity.Contact;
import com.coinbase.api.entity.ContactsResponse;
import com.coinbase.api.entity.Merchant;
import com.coinbase.api.entity.Quote;
import com.coinbase.api.entity.Response;
import com.coinbase.api.entity.Transaction;
import com.coinbase.api.entity.TransactionsResponse;
import com.coinbase.api.entity.Transfer;
import com.coinbase.api.entity.User;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.joda.money.CurrencyUnit.USD;

public class MockResponses {

  public static AddressesResponse mockAddressesResponse() {
    AddressesResponse response = new AddressesResponse();
    List<Address> addressList = new ArrayList<Address>();

    Address address1 = new Address();
    address1.setAddress("1234asdfbasdf");
    address1.setLabel("Mock Label 1");
    addressList.add(address1);

    Address address2 = new Address();
    address2.setAddress("2234asdfbasdf");
    address2.setLabel("Mock Label 2");
    addressList.add(address2);

    response.setAddresses(addressList);
    response.setCurrentPage(1);
    response.setNumPages(1);
    response.setTotalCount(2);

    return response;
  }

  public static User mockUser() {
    User user = new User();
    user.setId("MockUserId");
    user.setName("Test User");
    user.setEmail("user@example.com");
    user.setTimeZone("Pacific Time (US & Canada)");
    user.setNativeCurrency(USD);
    user.setBalance(Money.parse("BTC 1"));
    user.setBuyLevel(1);
    user.setSellLevel(1);
    user.setBuyLimit(Money.parse("USD 3000"));
    user.setSellLimit(Money.parse("USD 3000"));
    return user;
  }

  public static Account mockAccount() {
    Account result = new Account();
    result.setName("Test Account");
    result.setId("TestAccountId");
    result.setActive(true);
    result.setPrimary(true);
    result.setBalance(Money.parse("BTC 1"));
    result.setNativeBalance(Money.parse("USD 600"));

    return result;
  }

  public static User mockCurrentUser() {
    Merchant merchant = new Merchant();
    merchant.setCompanyName("Android Test Inc");

    User user = new User();
    user.setMerchant(merchant);
    user.setId("MockCurrentUserId");
    user.setName("Test Current User");
    user.setEmail("currentuser@example.com");
    user.setTimeZone("Pacific Time (US & Canada)");
    user.setNativeCurrency(USD);
    user.setBalance(Money.parse("BTC 1.00"));
    user.setBuyLevel(1);
    user.setSellLevel(1);
    user.setBuyLimit(Money.parse("USD 3000"));
    user.setSellLimit(Money.parse("USD 3000"));
    return user;
  }

  public static List<CurrencyUnit> supportedCurrencies() {
    return CurrencyUnit.registeredCurrencies();
  }

  public static AddressResponse mockGeneratedAddress() {
    AddressResponse response = new AddressResponse();
    response.setAddress("1NewlyGeneratedAddress");

    return response;
  }

  public static Quote mockBuyQuote(Money btcAmount) {
    BigDecimal priceOfBtc   = new BigDecimal("590.23");
    BigDecimal subTotal     = btcAmount.getAmount().multiply(priceOfBtc);
    BigDecimal coinbaseFee  = subTotal.multiply(new BigDecimal("0.01"));
    BigDecimal bankFee      = new BigDecimal("0.15");
    BigDecimal total        = subTotal.add(coinbaseFee).add(bankFee);

    Quote result = new Quote();
    result.setSubtotal(Money.of(USD, subTotal, RoundingMode.HALF_EVEN));
    result.setTotal(Money.of(USD, total, RoundingMode.HALF_EVEN));
    result.setFees(new HashMap<String, Money>());
    result.getFees().put("coinbase", Money.of(USD, coinbaseFee, RoundingMode.HALF_EVEN));
    result.getFees().put("bank", Money.of(USD, bankFee, RoundingMode.HALF_EVEN));

    return result;
  }

  public static Quote mockSellQuote(Money btcAmount) {
    BigDecimal priceOfBtc   = new BigDecimal("590.23");
    BigDecimal subTotal     = btcAmount.getAmount().multiply(priceOfBtc);
    BigDecimal coinbaseFee  = subTotal.multiply(new BigDecimal("0.01"));
    BigDecimal bankFee      = new BigDecimal("0.15");
    BigDecimal total        = subTotal.subtract(coinbaseFee).subtract(bankFee);

    Quote result = new Quote();
    result.setSubtotal(Money.of(USD, subTotal, RoundingMode.HALF_EVEN));
    result.setTotal(Money.of(USD, total, RoundingMode.HALF_EVEN));
    result.setFees(new HashMap<String, Money>());
    result.getFees().put("coinbase", Money.of(USD, coinbaseFee, RoundingMode.HALF_EVEN));
    result.getFees().put("bank", Money.of(USD, bankFee, RoundingMode.HALF_EVEN));

    return result;
  }

  public static Transfer mockBuyTransfer(Money amount) {
    Quote buyQuote = mockBuyQuote(amount);
    Transfer result = new Transfer();
    result.setBtc(amount);
    result.setFees(buyQuote.getFees());
    result.setSubtotal(buyQuote.getSubtotal());
    result.setTotal(buyQuote.getTotal());
    result.setType(Transfer.Type.BUY);
    return result;
  }

  public static Transfer mockSellTransfer(Money amount) {
    Quote sellQuote = mockSellQuote(amount);
    Transfer result = new Transfer();
    result.setBtc(amount);
    result.setFees(sellQuote.getFees());
    result.setSubtotal(sellQuote.getSubtotal());
    result.setTotal(sellQuote.getTotal());
    result.setType(Transfer.Type.SELL);
    return result;
  }

  public static Map<String, BigDecimal> mockExchangeRates() {
    HashMap<String, BigDecimal> result = new HashMap<String, BigDecimal>();

    BigDecimal BTC_USD = new BigDecimal("590.23");

    result.put("btc_to_usd", BTC_USD);
    result.put("usd_to_btc", BigDecimal.ONE.divide(BTC_USD, 8, RoundingMode.HALF_EVEN));

    return result;
  }

  public static ContactsResponse mockContacts() {
    ContactsResponse result = newResponse(ContactsResponse.class, 1, 25, 1);

    Contact contact = new Contact();
    contact.setEmail("user@example.com");

    List<Contact> contacts = new ArrayList<Contact>();
    contacts.add(contact);

    result.setContacts(contacts);
    return result;
  }

  public static TransactionsResponse mockEmptyTransactionsResponse() {
    TransactionsResponse result = newResponse(TransactionsResponse.class, 0, 25, 1);
    result.setTransactions(new ArrayList<Transaction>());
    return result;
  }

  public static TransactionsResponse mockTransactionsResponse(Transaction transaction) {
    TransactionsResponse result = newResponse(TransactionsResponse.class, 1, 25, 1);
    List<Transaction> transactions = new ArrayList<Transaction>();
    transactions.add(transaction);
    result.setTransactions(transactions);

    return result;
  }

  public static Transaction mockConfirmedReceivedTransaction() {
    Transaction result = new Transaction();
    result.setAmount(Money.parse("BTC 1.23"));
    result.setSender(mockUser());
    result.setRecipient(mockCurrentUser());
    result.setRequest(false);
    result.setCreatedAt(DateTime.now());
    result.setId("transId123");
    result.setConfirmations(6);
    result.setStatus(Transaction.Status.COMPLETE);

    return result;
  }

  public static Transaction mockPendingReceivedTransaction() {
    Transaction result = mockConfirmedReceivedTransaction();
    result.setConfirmations(0);
    result.setStatus(Transaction.Status.PENDING);

    return result;
  }

  public static Transaction mockConfirmedSentTransaction() {
    Transaction result = mockConfirmedReceivedTransaction();
    result.setAmount(result.getAmount().negated());

    User tmp;
    tmp = result.getSender();
    result.setSender(result.getRecipient());
    result.setRecipient(tmp);

    return result;
  }

  public static Transaction mockSentPendingRequestTransaction() {
    Transaction result = mockConfirmedReceivedTransaction();
    result.setRequest(true);
    result.setCreatedAt(DateTime.now());
    result.setConfirmations(0);
    result.setStatus(Transaction.Status.PENDING);

    return result;
  }

  public static Transaction mockReceivedPendingRequestTransaction() {
    Transaction result = mockConfirmedSentTransaction();
    result.setRequest(true);
    result.setCreatedAt(DateTime.now());
    result.setConfirmations(0);
    result.setStatus(Transaction.Status.PENDING);

    return result;
  }

  public static AccountChange mockAccountChange(Transaction transaction) {
    AccountChange change = new AccountChange();
    AccountChange.Cache cache = new AccountChange.Cache();
    change.setCache(cache);
    if (transaction.isRequest()) {
      cache.setCategory(AccountChange.Cache.Category.REQUEST);
    } else {
      cache.setCategory(AccountChange.Cache.Category.TRANSACTION);
    }
    if (transaction.getAmount().isPositive()) {
      cache.setOtherUser(transaction.getSender());
    } else if (transaction.getAmount().isNegative()) {
      cache.setOtherUser(transaction.getRecipient());
    }
    change.setAmount(transaction.getAmount());
    change.setConfirmed(transaction.getStatus() == Transaction.Status.COMPLETE);
    change.setTransactionId(transaction.getId());
    change.setCreatedAt(transaction.getCreatedAt());

    return change;
  }

  public static AccountChangesResponse mockAccountChanges(List<AccountChange> changes) {
    AccountChangesResponse result = newResponse(AccountChangesResponse.class, changes.size(), 25, changes.size() / 25);

    result.setAccountChanges(changes);
    result.setCurrentUser(mockCurrentUser());
    result.setBalance(Money.parse("BTC 1.00"));
    result.setNativeBalance(Money.parse("USD 600.00"));

    return result;
  }

  public static AccountChangesResponse mockAccountChanges(AccountChange change) {
    List<AccountChange> changes = new ArrayList<AccountChange>();
    changes.add(change);
    return mockAccountChanges(changes);
  }

  public static AccountChangesResponse mockEmptyAccountChangesResponse() {
    AccountChangesResponse result = newResponse(AccountChangesResponse.class, 0, 25, 1);
    result.setAccountChanges(new ArrayList<AccountChange>());
    result.setCurrentUser(mockCurrentUser());
    result.setBalance(Money.parse("BTC 1.00"));
    result.setNativeBalance(Money.parse("USD 600.00"));
    return result;
  }

  public static AccountChangesResponse mockAccountChanges() {
    List<AccountChange> changes = new ArrayList<AccountChange>();
    changes.add(mockAccountChange(mockConfirmedReceivedTransaction()));
    changes.add(mockAccountChange(mockPendingReceivedTransaction()));
    changes.add(mockAccountChange(mockConfirmedSentTransaction()));
    return mockAccountChanges(changes);
  }

  public static <T extends Response> T newResponse(Class<T> clazz, int count, int limit, int page) {
    T result;
    try {
      result = clazz.newInstance();
    } catch (Exception ex) {
      throw new AssertionError();
    }

    result.setNumPages(count / limit);
    result.setCurrentPage(page);
    result.setTotalCount(count);

    return result;
  }
}
