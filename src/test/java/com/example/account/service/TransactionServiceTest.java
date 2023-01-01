package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.domain.Transaction;
import com.example.account.dto.TransactionDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.repository.TransactionRepository;
import com.example.account.type.AccountStatus;
import com.example.account.type.ErrorCode;
import com.example.account.type.TransactionResultType;
import com.example.account.type.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.account.type.AccountStatus.*;
import static com.example.account.type.ErrorCode.*;
import static com.example.account.type.TransactionResultType.*;
import static com.example.account.type.TransactionType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("잔액 사용 성공")
    void successUseBalance() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L)
                .name("kim").build();

        Account account = Account.builder()
                .accountUser(accountUser)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000000")
                .build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(accountUser));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionResultType(SUCCESS)
                        .transactionType(USE)
                        .transactionId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        TransactionDto transactionDto = transactionService.useBalance(1L,
               "1000000000", 500L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(500, captor.getValue().getAmount());
        assertEquals(9500, captor.getValue().getBalanceSnapshot());
        assertEquals(SUCCESS, transactionDto.getTransactionResult());
        assertEquals(USE, transactionDto.getTransactionType());
        assertEquals(9000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 실패")
    void failedUseBalance_AccountNotFound() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L).name("kim").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(accountUser));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        //then
        assertEquals(ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 금액이 잔액보다 큰 경우")
    void exceedAmount_UseBalance() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L).name("kim").build();

        Account account = Account.builder()
                .accountNumber("1234567890")
                .accountStatus(IN_USE)
                .accountUser(accountUser)
                .balance(10000L).build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(accountUser));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));


        //when
        //then
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 10001L));

        assertEquals(AMOUNT_EXCEED_BALANCE, exception.getErrorCode());
        verify(transactionRepository, times(0)).save(any());

    }

    @Test
    @DisplayName("해당 유저 없음 - 잔액 사용 실패")
    void failedUseBalance_UserNotFound() {
        //given
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        //then
        assertEquals(USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌 소유주 다름 - 잔액 사용 실패")
    void failedUseBalance_userUnmatch() {
        //given
        AccountUser kim = AccountUser.builder()
                .id(12L).name("kim").build();
        AccountUser hong = AccountUser.builder()
                .id(13L).name("hong").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(kim));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                                .accountUser(hong)
                                .balance(0L)
                                .accountNumber("1000000000").build()));

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 1000L));

        //then
        assertEquals(USER_ACCOUNT_UNMATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("해지된 계좌 사용 불가")
    void deleteAccountNotUsed_alreadyUnregistered() {
        //given
        AccountUser kim = AccountUser.builder()
                .id(12L).name("kim").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(kim));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                                .accountUser(kim)
                                .accountStatus(UNREGISTERED)
                                .balance(0L)
                                .accountNumber("1000000000")
                                .build()));

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 1000L));

        //then
        assertEquals(ACCOUNT_ALREADY_UNREGISTERED, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패된 트랜잭션 저장 성공")
    void save_FailedTransaction() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L).name("kim").build();

        Account account = Account.builder()
                .accountUser(accountUser)
                .balance(10000L)
                .accountStatus(IN_USE)
                .accountNumber("1000000000").build();

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(SUCCESS)
                        .transactedAt(LocalDateTime.now())
                        .transactionId("transactionId")
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        transactionService.saveFailedTransaction("1000000000", 2000L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(2000L, captor.getValue().getAmount());
        assertEquals(FAIL, captor.getValue().getTransactionResultType());
        assertEquals(10000L, captor.getValue().getBalanceSnapshot());
    }

    @Test
    @DisplayName("결제 취소 성공")
    void successCancelBalance() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L).name("kim").build();

        Account account = Account.builder()
                .accountUser(accountUser)
                .accountNumber("1000000000")
                .accountStatus(IN_USE)
                .balance(10000L)
                .build();

        Transaction transaction = Transaction.builder()
                .transactionType(USE)
                .transactionResultType(SUCCESS)
                .account(account)
                .transactionId("transactionId")
                .amount(1000L)
                .balanceSnapshot(9000L)
                .transactedAt(LocalDateTime.now())
                .build();

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .transactionType(CANCEL)
                        .transactionResultType(SUCCESS)
                        .account(account)
                        .amount(1000L)
                        .balanceSnapshot(10000L)
                        .transactedAt(LocalDateTime.now())
                        .transactionId("transactionId")
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        TransactionDto transactionDto = transactionService.cancelBalance(
                "transactionId", "1234567890", 1000L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(1000L, captor.getValue().getAmount());
        assertEquals(10000L + 1000L, captor.getValue().getBalanceSnapshot());
        assertEquals(SUCCESS, transactionDto.getTransactionResult());
        assertEquals(CANCEL, transactionDto.getTransactionType());
        assertEquals(10000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 취소 실패")
    void cancelTransaction_AccountNotFound() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L).name("kim").build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(Transaction.builder().build()));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1000000000", 1000L));

        //then
        assertEquals(ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("사용된 거래 내역 없음 - 잔액 사용 취소 실패")
    void cancelTransaction_transactionNotFound() {
        //given
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1234567890", 1000L));

        //then
        assertEquals(TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 금액과 취소 금액이 다름 - 잔액 사용 취소 실패")
    void cancelTransaction_CancelMustFully() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L).name("kim").build();

        Account account = Account.builder()
                .accountUser(accountUser)
                .accountNumber("1234567890")
                .accountStatus(IN_USE)
                .balance(10000L)
                .build();

        Transaction transaction = Transaction.builder()
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .transactionType(USE)
                .transactionResultType(SUCCESS)
                .account(account)
                .amount(1000L + 1000L)
                .balanceSnapshot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance(
                   "transactionId", "1000000000", 1000L));

        //then
        assertEquals(CANCEL_MUST_FULLY, exception.getErrorCode());
    }

    @Test
    @DisplayName("취소는 1년까지만 가능 - 잔액 사용 취소 실패")
    void cancelTransaction_allowBeforeOneYear() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L).name("kim").build();

        Account account = Account.builder()
                .accountStatus(IN_USE)
                .accountNumber("1234567890")
                .accountUser(accountUser)
                .balance(10000L)
                .build();

        Transaction transaction = Transaction.builder()
                .transactionId("transactionId")
                .transactionType(USE)
                .transactionResultType(SUCCESS)
                .balanceSnapshot(9000L)
                .amount(1000L)
                .account(account)
                .transactedAt(LocalDateTime.now().minusYears(1).minusDays(1))
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));


        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance(
                        "transactionId", "1000000000", 1000L));
        //then
        assertEquals(TOO_OLD_ORDER_TO_CANCEL, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 내역 없음 - 거래 조회 실패")
    void queryTransaction_NotFound() {
        //given
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.queryTransaction("transactionId"));

        //then
        assertEquals(TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }
}