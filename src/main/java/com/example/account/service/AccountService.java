package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.dto.AccountDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountUserRepository;
import com.example.account.type.AccountStatus;
import com.example.account.repository.AccountRepository;
import com.example.account.type.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import static com.example.account.type.AccountStatus.*;
import static com.example.account.type.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final AccountUserRepository accountUserRepository;

    @Transactional
    public AccountDto createAccount(Long userId, Long initialBalance) {
        AccountUser accountUser = accountUserRepository.findById(userId)
                .orElseThrow(() -> new AccountException(USER_NOT_FOUND));

        validateCreateAccount(accountUser);

        String newAccountNumber = accountRepository.findFirstByOrderByIdDesc()
                .map(account -> (Long.parseLong(account.getAccountNumber())) + 1 + "")
                .orElse(createAccountNumber());

        return AccountDto.fromEntity(
            accountRepository.save(Account.builder()
                    .accountUser(accountUser)
                    .accountStatus(IN_USE)
                    .accountNumber(newAccountNumber)
                    .balance(initialBalance)
                    .registeredAt(LocalDateTime.now())
                    .build())
        );
    }

    private String createAccountNumber() {
        Random random = new Random();
        String accountNumber = "";

        for (int i = 0; i < 10; i++) {
            int randomNum = random.nextInt(9);

            // 계좌번호 첫 자리 숫자가 0일 경우 재생성
            if(i == 0 && randomNum == 0) {
                continue;
            }

            accountNumber += randomNum;
        }

        return accountNumber;
    }

    private void validateCreateAccount(AccountUser accountUser) {
        if (accountRepository.countByAccountUser(accountUser) == 10) {
            throw new AccountException(MAX_ACCOUNT_PER_USER_10);
        }
    }

    @Transactional
    public Account getAccount(Long id) {
        if(id < 0){
            throw new RuntimeException("Minus");
        }

        return accountRepository.findById(id).get();
    }

    @Transactional
    public AccountDto deleteAccount(long userId, String accountNumber) {
        AccountUser accountUser = accountUserRepository.findById(userId)
                .orElseThrow(() -> new AccountException(USER_NOT_FOUND));

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountException(ACCOUNT_NOT_FOUND));

        validateDeleteAccount(accountUser, account);

        account.setAccountStatus(UNREGISTERED);
        account.setUnRegisteredAt(LocalDateTime.now());

        accountRepository.save(account);

        return AccountDto.fromEntity(account);
    }

    private void validateDeleteAccount(AccountUser accountUser, Account account) {
        if(!Objects.equals(accountUser.getId(), account.getAccountUser().getId())) {
            throw new AccountException(USER_ACCOUNT_UNMATCH);
        }

        if(account.getAccountStatus() == UNREGISTERED) {
            throw new AccountException(ACCOUNT_ALREADY_UNREGISTERED);
        }

        if(account.getBalance() > 0) {
            throw new AccountException(BALANCE_NOT_EMPTY);
        }
    }

    @Transactional
    public List<AccountDto> getAccountsByUserId(Long userId) {
        AccountUser accountUser = accountUserRepository.findById(userId)
                .orElseThrow(() -> new AccountException(USER_NOT_FOUND));

        List<Account> accounts = accountRepository.findByAccountUser(accountUser);

        return accounts.stream()
                .map(AccountDto::fromEntity)
                .collect(Collectors.toList());
    }


}
