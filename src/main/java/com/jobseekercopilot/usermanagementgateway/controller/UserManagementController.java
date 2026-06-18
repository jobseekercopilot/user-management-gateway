package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.*;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class UserManagementController {

    @Autowired
    private UserManagementService userManagementService;

    @PostMapping("/register")
    public ResponseEntity<GatewayResponse> register(@RequestBody RegisterRequest request) {
        GatewayResponse response = userManagementService.register(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<GatewayResponse> login(@RequestBody LoginRequest request) {
        GatewayResponse response = userManagementService.login(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/profile")
    public ResponseEntity<GatewayResponse> getProfile(
            @RequestParam(name = "email", required = false) String email,
            @RequestHeader(name = "Authorization", required = false) String token) {
        
        GatewayResponse response = userManagementService.getProfile(token);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<GatewayResponse> updateProfile(
            @RequestParam(name = "email", required = false) String email,
            @RequestBody UserProfile profile,
            @RequestHeader(name = "Authorization", required = false) String token) {
        
        GatewayResponse response = userManagementService.updateProfile(profile, token);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}