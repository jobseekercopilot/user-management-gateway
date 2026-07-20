package com.jobseekercopilot.usermanagementgateway.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.jobseekercopilot.usermanagementgateway.validation.UnicodeLength;

@Getter
@NoArgsConstructor
public class RegisterRequest {
    @NotNull
    @UnicodeLength(min = 1, max = 100)
    private String name;
    @NotBlank
    @Email
    @UnicodeLength(max = 254)
    private String email;
    @NotNull
    @UnicodeLength(min = 15, max = 128)
    private String password;
    @Valid
    private UserProfile profile;

    public void setName(String name) { this.name = name == null ? null : name.trim(); }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
    public void setPassword(String password) { this.password = password; }
    public void setProfile(UserProfile profile) { this.profile = profile; }
}
