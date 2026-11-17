package com.myrtle.middleware.security;

import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AuthenticatedAccountPrincipal implements UserDetails {

    private final String id;
    private final String email;
    private final Role role;
    private final AccountType accountType;
    private final Integer tier;
    private final String corporateClientId;
    private final boolean enabled;

    public AuthenticatedAccountPrincipal(
            String id,
            String email,
            Role role,
            AccountType accountType,
            Integer tier,
            String corporateClientId,
            boolean enabled
    ) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.accountType = accountType;
        this.tier = tier;
        this.corporateClientId = corporateClientId;
        this.enabled = enabled;
    }

    public static AuthenticatedAccountPrincipal fromUser(User user) {
        return new AuthenticatedAccountPrincipal(
                user.getId(),
                user.getEmailAddress(),
                user.getRole() == null ? Role.USER : user.getRole(),
                AccountType.USER,
                parseTier(user.getTier()),
                null,
                !user.isDeleted() && !user.isDisabled()
        );
    }

    public static AuthenticatedAccountPrincipal fromCorporateClient(CorporateClient corporateClient) {
        return new AuthenticatedAccountPrincipal(
                corporateClient.getId(),
                corporateClient.getEmail(),
                corporateClient.getRole() == null ? Role.CORPORATE_ACCOUNT : corporateClient.getRole(),
                AccountType.CORPORATE_ACCOUNT,
                corporateClient.getTier() == null ? 3 : corporateClient.getTier(),
                corporateClient.getId(),
                !Boolean.FALSE.equals(corporateClient.getGrantLoginAccess())
        );
    }

    public static AuthenticatedAccountPrincipal fromPendingCorporateData(PendingCorporateData pendingCorporateData) {
        return new AuthenticatedAccountPrincipal(
                pendingCorporateData.getId(),
                pendingCorporateData.getEmail(),
                Role.USER,
                AccountType.PENDING_CORPORATE_ONBOARDING,
                null,
                null,
                pendingCorporateData.isEmailVerified()
        );
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public Integer getTier() {
        return tier;
    }

    public String getCorporateClientId() {
        return corporateClientId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> "ROLE_" + role.name());
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private static Integer parseTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(tier.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
