package com.eeum.eeum.common.util;

public class MaskingUtil {

    private MaskingUtil(){}; // 유틸 클래스 인스턴스화 방지

    public static String maskEmail(String email) {
        if(email == null || email.isBlank()){
            return email;
        }

        int atIndex = email.indexOf("@");

        if(atIndex<=0){
            return email;
        }

        String localPart = email.substring(0,atIndex);
        String domainPart = email.substring(atIndex);

        if(localPart.length()<=2){
            return localPart.charAt(0) + "*" + domainPart;
        }
        return localPart.substring(0,2)+"*".repeat(localPart.length()-2)+domainPart;
    }

    public static String maskName(String name) {
        if(name == null || name.isBlank()){
            return name;
        }

        int length = name.length();

        if(length==1){
            return "*";
        }

        if(length==2){
            return name.charAt(0)+"*";
        }

        return name.charAt(0)+"*".repeat(length-2)+name.charAt(length-1);
    }

    public static String maskPhone(String phone) {
        if(phone == null || phone.isEmpty()){
            return phone;
        }

        String digitsOnly = phone.replaceAll("[^0-9]", "");

        if(digitsOnly.length()<7){
            return phone;
        }

        if(digitsOnly.length() == 10){
            return digitsOnly.substring(0,3)+"-***-"+digitsOnly.substring(6);
        }

        if (digitsOnly.length() == 11) {
            return digitsOnly.substring(0, 3)
                    + "-****-"
                    + digitsOnly.substring(7);
        }

        return digitsOnly.substring(0, 3)
                + "-****-"
                + digitsOnly.substring(digitsOnly.length() - 4);
    }

    public static String maskBusinessNumber(String businessNumber) {
        if (businessNumber == null || businessNumber.isBlank()) {
            return businessNumber;
        }

        String digitsOnly = businessNumber.replaceAll("[^0-9]", "");

        if (digitsOnly.length() != 10) {
            return businessNumber;
        }

        return digitsOnly.substring(0, 3)
                + "-**-"
                + digitsOnly.substring(5);
    }

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return accountNumber;
        }

        String digitsOnly = accountNumber.replaceAll("[^0-9]", "");

        if (digitsOnly.length() <= 4) {
            return "*".repeat(digitsOnly.length());
        }

        int visiblePrefixLength = Math.min(3, digitsOnly.length() - 4);
        String prefix = digitsOnly.substring(0, visiblePrefixLength);
        String suffix = digitsOnly.substring(digitsOnly.length() - 4);

        return prefix + "-" + "*".repeat(Math.max(4, digitsOnly.length() - visiblePrefixLength - 4)) + "-" + suffix;
    }
}
