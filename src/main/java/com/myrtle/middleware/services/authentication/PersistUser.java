package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.request.NextOfKinRequest;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.nextofkin.NextOfKinService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PersistUser {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final NextOfKinService nextOfKinService;

    @Transactional
    public User persistUser(User user, List<NextOfKinRequest> nextOfKins, String clientId, String investmentAcc, String branchId, Role role) {
        user.setLast_balance(0.0);
        user.setAccountNumber("0000000000");
        user.setTier("1");
        user.setClientId(clientId);
        user.setRole(role);
        user.setInvestmentAccountId(investmentAcc);
        user.setBranchId(branchId);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);

        if (nextOfKins != null && !nextOfKins.isEmpty()) {
            nextOfKinService.createNokForUser(savedUser, nextOfKins);
        }
        return savedUser;
    }
}

