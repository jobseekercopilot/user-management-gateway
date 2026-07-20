package com.jobseekercopilot.usermanagementgateway.web;

import java.io.IOException;

public class PayloadTooLargeIOException extends IOException {
    public PayloadTooLargeIOException() {
        super("Request body exceeds the configured limit");
    }
}
