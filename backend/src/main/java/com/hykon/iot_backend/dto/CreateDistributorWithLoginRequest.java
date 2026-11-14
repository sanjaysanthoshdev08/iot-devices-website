package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Creates a distributor organization together with its own login (email + password). */
public class CreateDistributorWithLoginRequest {

    @NotBlank(message = "Distributor code is required")
    @Size(max = 100, message = "Distributor code must not exceed 100 characters")
    private String distributorCode;

    @NotBlank(message = "Distributor name is required")
    @Size(min = 2, max = 100, message = "Distributor name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    @NotBlank(message = "District is required")
    @Size(max = 100, message = "District must not exceed 100 characters")
    private String district;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S+$",
            message = "Password must contain an uppercase letter, a lowercase letter, a digit, and a special character, with no whitespace"
    )
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    public String getDistributorCode() { return distributorCode; }
    public void setDistributorCode(String v) { this.distributorCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getDistrict() { return district; }
    public void setDistrict(String v) { this.district = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String v) { this.confirmPassword = v; }
}
