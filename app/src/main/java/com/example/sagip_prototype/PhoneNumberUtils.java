package com.example.sagip_prototype;

public class PhoneNumberUtils {
    
    /**
     * Formats a phone number for display purposes
     * @param phoneNumber The raw phone number from database
     * @return Formatted phone number for display
     */
    public static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "N/A";
        }
        
        // Remove any non-digit characters except +
        String cleanNumber = phoneNumber.replaceAll("[^+0-9]", "");
        
        // Handle incomplete numbers like "+63090"
        if (cleanNumber.equals("+63090") || cleanNumber.length() < 10) {
            return "Invalid Phone Number";
        }
        
        // Format Philippine mobile numbers
        if (cleanNumber.startsWith("+63")) {
            String number = cleanNumber.substring(3);
            if (number.length() == 10) {
                // Format as +63 XXX XXX XXXX
                return "+63 " + number.substring(0, 3) + " " + 
                       number.substring(3, 6) + " " + 
                       number.substring(6);
            } else if (number.length() == 11 && number.startsWith("0")) {
                // Format as +63 XXX XXX XXXX (remove leading 0)
                number = number.substring(1);
                return "+63 " + number.substring(0, 3) + " " + 
                       number.substring(3, 6) + " " + 
                       number.substring(6);
            }
        } else if (cleanNumber.startsWith("0") && cleanNumber.length() == 11) {
            // Format as +63 XXX XXX XXXX
            String number = cleanNumber.substring(1);
            return "+63 " + number.substring(0, 3) + " " + 
                   number.substring(3, 6) + " " + 
                   number.substring(6);
        } else if (cleanNumber.length() == 10) {
            // Format as +63 XXX XXX XXXX
            return "+63 " + cleanNumber.substring(0, 3) + " " + 
                   cleanNumber.substring(3, 6) + " " + 
                   cleanNumber.substring(6);
        }
        
        // Return original if can't format
        return phoneNumber;
    }
    
    /**
     * Gets a callable phone number for making calls
     * @param phoneNumber The raw phone number from database
     * @return Callable phone number or null if invalid
     */
    public static String getCallablePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }
        
        // Remove any non-digit characters except +
        String cleanNumber = phoneNumber.replaceAll("[^+0-9]", "");
        
        // Handle incomplete numbers
        if (cleanNumber.equals("+63090") || cleanNumber.length() < 10) {
            return null;
        }
        
        // Ensure it starts with +63
        if (cleanNumber.startsWith("+63")) {
            return cleanNumber;
        } else if (cleanNumber.startsWith("0") && cleanNumber.length() == 11) {
            return "+63" + cleanNumber.substring(1);
        } else if (cleanNumber.length() == 10) {
            return "+63" + cleanNumber;
        }
        
        return null;
    }
    
    /**
     * Checks if a phone number is valid and callable
     * @param phoneNumber The phone number to validate
     * @return true if the phone number is valid and callable
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        return getCallablePhoneNumber(phoneNumber) != null;
    }
    
    /**
     * Gets a short display format for phone numbers (for lists)
     * @param phoneNumber The raw phone number from database
     * @return Short formatted phone number
     */
    public static String getShortPhoneDisplay(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "N/A";
        }
        
        String cleanNumber = phoneNumber.replaceAll("[^+0-9]", "");
        
        // Handle incomplete numbers
        if (cleanNumber.equals("+63090") || cleanNumber.length() < 10) {
            return "Invalid";
        }
        
        // For short display, show last 4 digits
        if (cleanNumber.startsWith("+63")) {
            String number = cleanNumber.substring(3);
            if (number.length() >= 4) {
                return "+63***" + number.substring(number.length() - 4);
            }
        } else if (cleanNumber.startsWith("0") && cleanNumber.length() == 11) {
            String number = cleanNumber.substring(1);
            if (number.length() >= 4) {
                return "+63***" + number.substring(number.length() - 4);
            }
        } else if (cleanNumber.length() == 10) {
            if (cleanNumber.length() >= 4) {
                return "+63***" + cleanNumber.substring(cleanNumber.length() - 4);
            }
        }
        
        return "Invalid";
    }
}
