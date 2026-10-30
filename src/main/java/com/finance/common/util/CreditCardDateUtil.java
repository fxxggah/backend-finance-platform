package com.finance.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class CreditCardDateUtil {

    private static final DateTimeFormatter REF_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    public LocalDate closingDate(LocalDate reference, int closingDay) {
        return reference.withDayOfMonth(closingDay);
    }

    public LocalDate dueDate(LocalDate reference, int closingDay, int dueDay) {
        LocalDate base = dueDay > closingDay ? reference : reference.plusMonths(1);
        return base.withDayOfMonth(dueDay);
    }

    public String formatReference(LocalDate reference) {
        return reference.format(REF_FORMAT);
    }

    public LocalDate parseReference(String referenceStr) {
        return LocalDate.parse(referenceStr + "-01");
    }
}