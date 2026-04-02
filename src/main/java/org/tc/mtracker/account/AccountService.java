package org.tc.mtracker.account;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tc.mtracker.account.dto.AccountResponseDTO;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserService;
import org.tc.mtracker.utils.exceptions.AccountNotFoundException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserService userService;

    @Transactional(readOnly = true)
    public AccountResponseDTO getDefaultAccount(Authentication auth) {
        User currentUser = userService.getCurrentAuthenticatedUser(auth);
        Account account = currentUser.getDefaultAccount();

        if (account == null) {
            throw new AccountNotFoundException("Current user does not have default account");
        }

        return new AccountResponseDTO(
                account.getId(),
                account.getBalance() == null ? BigDecimal.ZERO : account.getBalance()
        );
    }
}
