package com.jobseekercopilot.usermanagementgateway.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UnicodeLengthValidator implements ConstraintValidator<UnicodeLength, CharSequence> {
    private int minimum;
    private int maximum;

    @Override
    public void initialize(UnicodeLength constraint) {
        minimum = constraint.min();
        maximum = constraint.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String text = value.toString();
        int codePoints = text.codePointCount(0, text.length());
        return codePoints >= minimum && codePoints <= maximum;
    }
}
