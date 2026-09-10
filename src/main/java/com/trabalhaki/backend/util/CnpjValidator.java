package com.trabalhaki.backend.util;

public final class CnpjValidator {

    private CnpjValidator() {}

    public static boolean isValid(String cnpj) {
        if (cnpj == null) return false;

        String numbers = cnpj.replaceAll("\\D", "");
        if (numbers.length() != 14) return false;

        // Reject repeated digits sequence (e.g. 00000000000000, 11111111111111, etc.)
        if (numbers.chars().distinct().count() == 1) return false;

        try {
            int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            int sum1 = 0;
            for (int i = 0; i < 12; i++) {
                sum1 += (numbers.charAt(i) - '0') * weights1[i];
            }
            int remainder1 = sum1 % 11;
            int digit1 = (remainder1 < 2) ? 0 : (11 - remainder1);

            if (digit1 != (numbers.charAt(12) - '0')) return false;

            int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            int sum2 = 0;
            for (int i = 0; i < 13; i++) {
                sum2 += (numbers.charAt(i) - '0') * weights2[i];
            }
            int remainder2 = sum2 % 11;
            int digit2 = (remainder2 < 2) ? 0 : (11 - remainder2);

            return digit2 == (numbers.charAt(13) - '0');
        } catch (Exception e) {
            return false;
        }
    }
}
