package com.hospital.appointment.identity.api;

import com.hospital.appointment.identity.domain.AccountStatus;
import com.hospital.appointment.identity.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

final class IdentityApi {
    private IdentityApi() {
    }

    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    @Size(min = 12, max = 128)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{12,128}$")
    @Schema(pattern = "^(?=.*[A-Za-z])(?=.*[0-9]).{12,128}$",
            description = "Twelve to 128 characters containing at least one letter and one number.")
    @interface NewPassword {
        String message() default "Password must be 12 to 128 characters and contain at least one letter and one number.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @NewPassword String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 40) String phone) {
    }

    record LoginRequest(@NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 128) String password) {
    }

    record CodeRequest(@NotBlank @Email @Size(max = 320) String email,
                       @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    }

    record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {
    }

    record RecoveryRequest(@NotBlank @Email @Size(max = 320) String email,
                           @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
                           @NotBlank @NewPassword String newPassword) {
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                 @NotBlank @NewPassword String newPassword) {
    }

    record ManagedAccountRequest(@NotBlank @Email @Size(max = 320) String email,
                                 @NotBlank @NewPassword String password,
                                 @NotNull @Schema(allowableValues = {"STAFF", "PATIENT"}) Role role,
                                 @NotBlank @Size(max = 100) String firstName,
                                 @NotBlank @Size(max = 100) String lastName, @Size(max = 40) String phone) {
        ManagedAccountRequest {
            if (role != null && role != Role.STAFF && role != Role.PATIENT) {
                throw new IllegalArgumentException("Only staff and patient accounts can be managed through this endpoint.");
            }
        }
    }

    record UpdateAccountRequest(@NotNull @Schema(allowableValues = {"STAFF", "PATIENT"}) Role role,
                                @NotNull AccountStatus status,
                                @NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
                                @Size(max = 40) String phone, @NewPassword String newPassword,
                                @NotNull Long version) {
        UpdateAccountRequest {
            if (role != null && role != Role.STAFF && role != Role.PATIENT) {
                throw new IllegalArgumentException("Only staff and patient accounts can be managed through this endpoint.");
            }
        }
    }

    record AccountResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AccountStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean emailVerified,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean forcePasswordChange,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long authzVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }

    record LoginResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tokenType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresIn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AccountResponse account) {
    }

    record MfaRequiredResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean mfaRequired,
            String developmentCode) {
    }

    record TokenResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tokenType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresIn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AccountResponse account) {
    }

    record MessageResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message, String developmentCode) {
    }

    record CsrfResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String csrfToken) {
    }
}
