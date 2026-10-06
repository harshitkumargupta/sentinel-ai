package com.sentinelai.auth.service;

import com.sentinelai.common.exception.BadRequestException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Config-driven password policy enforced in the domain when a password is set. Kept simple and
 * deterministic (length + character classes); all thresholds come from config and are overridable
 * per profile / env. Registered via {@code @ConfigurationPropertiesScan}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.security.password")
public class PasswordPolicy {

    private int minLength = 12;
    private int maxLength = 128;
    private boolean requireUpper = true;
    private boolean requireLower = true;
    private boolean requireDigit = true;
    private boolean requireSpecial = true;

    /** @throws BadRequestException if the password violates the policy. */
    public void validate(String password) {
        if (password == null || password.length() < minLength || password.length() > maxLength) {
            throw new BadRequestException(
                    "Password must be between " + minLength + " and " + maxLength + " characters");
        }
        if (requireUpper && password.chars().noneMatch(Character::isUpperCase)) {
            throw new BadRequestException("Password must contain an uppercase letter");
        }
        if (requireLower && password.chars().noneMatch(Character::isLowerCase)) {
            throw new BadRequestException("Password must contain a lowercase letter");
        }
        if (requireDigit && password.chars().noneMatch(Character::isDigit)) {
            throw new BadRequestException("Password must contain a digit");
        }
        if (requireSpecial && password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new BadRequestException("Password must contain a special character");
        }
    }
}
