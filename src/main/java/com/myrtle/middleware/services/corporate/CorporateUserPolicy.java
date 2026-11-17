package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CorporateUserPolicy {

    public static final String SUPPORT_EMAIL = "wecare@myrtleng.com";

    public static final String MONEY_OUT_BLOCKED_MESSAGE =
            "Withdrawals on corporate accounts are handled by our team. "
                    + "Please email " + SUPPORT_EMAIL + " to request a withdrawal.";

    public static final String WITHDRAWAL_ACCOUNT_BLOCKED_MESSAGE =
            "Withdrawal accounts on corporate accounts are managed by our team. "
                    + "Please email " + SUPPORT_EMAIL + " for assistance.";

    private final UserRepository userRepository;

    public boolean isCorporateUser(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return userRepository.isCorporateUser(email, Role.CORPORATE_USER);
    }
}
