package com.hykon.iot_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateBranchWithAdminRequest {

    @NotBlank(message = "Branch code is required")
    @Size(max = 100, message = "Branch code must not exceed 100 characters")
    private String branchCode;

    @NotBlank(message = "Branch name is required")
    @Size(max = 150, message = "Branch name must not exceed 150 characters")
    private String branchName;

    @NotBlank(message = "Location is required")
    @Size(max = 150, message = "Location must not exceed 150 characters")
    private String location;

    @NotBlank(message = "Branch admin name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String adminName;

    @NotBlank(message = "Branch admin email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String adminEmail;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S+$",
            message = "Password must contain an uppercase letter, a lowercase letter, a digit, and a special character, with no whitespace"
    )
    private String adminPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    public CreateBranchWithAdminRequest() {
    }

    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String v) { this.branchCode = v; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String v) { this.branchName = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }
    public String getAdminName() { return adminName; }
    public void setAdminName(String v) { this.adminName = v; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String v) { this.adminEmail = v; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String v) { this.adminPassword = v; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String v) { this.confirmPassword = v; }
}
