package com.iron.gatewayserver.service;

import com.iron.gatewayserver.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountRepository accountRepository;

	public boolean existsByAccountIdAndToken(String accountId, String token) {
		return accountRepository.existsByAccountIdAndToken(accountId, token);
	}
}
