package org.tc.mtracker.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tc.mtracker.user.User;
import org.tc.mtracker.user.UserRepository;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAccountProvisioningService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    public User provisionDefaultAccount(User user) {
        Account defaultAccount = Account.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();
        Account savedDefaultAccount = accountRepository.save(defaultAccount);

        user.addAccount(savedDefaultAccount);
        user.setDefaultAccount(savedDefaultAccount);

        User savedUser = userRepository.save(user);
        log.info("Default account provisioned for userId={} accountId={}", savedUser.getId(), savedDefaultAccount.getId());
        return savedUser;
    }
}
