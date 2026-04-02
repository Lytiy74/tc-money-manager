package org.tc.mtracker.account;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tc.mtracker.account.dto.AccountResponseDTO;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/default")
    public ResponseEntity<AccountResponseDTO> getDefaultAccount(Authentication auth) {
        return ResponseEntity.ok(accountService.getDefaultAccount(auth));
    }
}
