package com.eeum.eeum.common.validation;

import com.eeum.eeum.domain.store.enums.Bank;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SupportedBankValidator implements ConstraintValidator<SupportedBank, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return Bank.find(value).isPresent();
    }
}
