package com.iron.gatewayserver.repository;

import com.iron.gatewayserver.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

	boolean existsByAccountIdAndToken(String accountId, String token);
}
