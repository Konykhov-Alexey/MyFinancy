package org.example.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyFormatter {

    private static final Locale RU = new Locale("ru", "RU");

    private CurrencyFormatter() {}

    public static String format(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(RU);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount) + " ₽";
    }
}
