package com.sentinelai.auth.security;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated principal carrying the identity SentinelAI needs on every request
 * (user id, org id, role) in addition to the Spring Security contract.
 */
@Getter
public class AppUserPrincipal implements UserDetails {

    private final Long userId;
    private final Long orgId;
    private final String username;
    private final Role role;
    private final String passwordHash;
    private final boolean enabled;

    public AppUserPrincipal(User user) {
        this.userId = user.getId();
        this.orgId = user.getOrg().getId();
        this.username = user.getUsername();
        this.role = user.getRole();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
