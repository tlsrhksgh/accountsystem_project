package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.dto.AccountDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountUserRepository;
import com.example.account.type.AccountStatus;
import com.example.account.repository.AccountRepository;
import com.example.account.type.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.account.type.AccountStatus.*;
import static com.example.account.type.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("계좌 생성 성공")
    void createAccountSuccess() {
        //given
        AccountUser accountUser = AccountUser.builder()
                .id(1L)
                .name("hong")
                .build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(accountUser));
        given(accountRepository.findFirstByOrderByIdDesc())
                .willReturn(Optional.of(Account.builder()
                        .accountNumber("1000000011").build()));
        given(accountRepository.save(any()))
                .willReturn(Account.builder()
                        .accountUser(accountUser)
                        .accountNumber("1000000013").build());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        //when
        AccountDto accountDto = accountService.createAccount(1L, 1000L);

        //then
        verify(accountRepository, times(1)).save(captor.capture());
        assertEquals(1L, accountDto.getUserId());
        assertEquals("1000000012", captor.getValue().getAccountNumber());
    }

    @Test
    @DisplayName("해당 유저 없음 - 계좌 생성 실패")
    void userNotFound() {
        //given
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.createAccount(1L, 1000L));

        //then
        assertEquals(USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("유저 당 생성할 수 있는 계좌는 최대 10개")
    void createAccount_maxAccountsIs10() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("hong")
                .build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.countByAccountUser(any()))
                .willReturn(10);

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.createAccount(1L, 1000L));

        //then
        assertEquals(MAX_ACCOUNT_PER_USER_10, exception.getErrorCode());
    }

    @Test
    @DisplayName("해당 유저 없음 - 계좌 해지 실패")
    void deleteAccount_UserNotFound() {
        //given
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1234567890"));

        //then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());

    }

    @Test
    @DisplayName("해당 계좌 없음 - 계좌 해지 실패")
    void deleteAccount_AccountNotFound() {
        //given
        AccountUser user = AccountUser.builder()
                .name("kim").build();
        user.setId(12L);
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1234567890"));

        //then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("소유주 다름 - 계좌 해지 실패")
    void deleteAccountFailed_UserUnMatch() {
        //given
        AccountUser kim = AccountUser.builder()
                .id(1L).name("kim").build();
        AccountUser hong = AccountUser.builder()
                .id(2L).name("hong").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(kim));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(hong)
                        .balance(0L)
                        .accountNumber("1234567890")
                        .build()));

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1000000000"));

        //then
        assertEquals(USER_ACCOUNT_UNMATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌를 해지할 때 잔액이 없어야 함.")
    void deleteAccountFailed_BalanceIsNotEmpty() {
        //given
        AccountUser kim = AccountUser.builder()
                .id(1L).name("kim").build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(kim));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountNumber("1234567890")
                        .balance(1000L)
                        .accountUser(kim)
                        .build()));

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1234567890"));

        //then
        assertEquals(BALANCE_NOT_EMPTY, exception.getErrorCode());
    }

    @Test
    @DisplayName("해지된 계좌는 해지할 수 없음")
    void deleteAccountFailed_alreadyUnregistered() {
        //given
        AccountUser kim = AccountUser.builder()
                .id(1L).name("kim").build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(kim));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(kim)
                        .accountStatus(UNREGISTERED)
                        .balance(0L)
                        .accountNumber("1234567890")
                        .build()));

        //when
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1235567890"));

        //then
        assertEquals(ACCOUNT_ALREADY_UNREGISTERED, exception.getErrorCode());
    }

//    @Test
//    @DisplayName("사용되고 있는 계좌번호가 있음 - 계좌 생성 실패")
//    void createAccountFailed_alreadyUseAccountNumber() {
//        //given
//        AccountUser kim = AccountUser.builder()
//                .id(1L).name("kim").build();
//        given(accountUserRepository.findById(anyLong()))
//                .willReturn(Optional.of(kim));
//        given(accountRepository.findByAccountNumber(anyString()))
//                .willReturn(Optional.of(Account.builder()
//                        .accountUser(kim)
//                        .accountStatus(IN_USE)
//                        .balance(0L)
//                        .accountNumber("1234567890")
//                        .build()));
//
//        //when
//        AccountException exception = assertThrows(AccountException.class,
//                () -> accountService.createAccount(1L, 10000L, "1234567890"));
//
//        //then
//        assertEquals(DUPLICATED_ACCOUNTNUMBER, exception.getErrorCode());
//    }

}