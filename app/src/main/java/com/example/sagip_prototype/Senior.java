package com.example.sagip_prototype;

public class Senior {
    private String documentId;
    private String firstName;
    private String lastName;
    private String middleName;
    private String birthday;
    private String barangay;
    private String mobileNumber;
    private String profileImageUrl;
    private String selfieVerificationUrl;
    private String status;
    private String userType;
    private String email;
    private String address;

    // Default constructor
    public Senior() {}

    // Constructor with all fields
    public Senior(String documentId, String firstName, String lastName, String middleName, 
                  String birthday, String barangay, String mobileNumber, String profileImageUrl, 
                  String selfieVerificationUrl, String status, String userType, String email, String address) {
        this.documentId = documentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.middleName = middleName;
        this.birthday = birthday;
        this.barangay = barangay;
        this.mobileNumber = mobileNumber;
        this.profileImageUrl = profileImageUrl;
        this.selfieVerificationUrl = selfieVerificationUrl;
        this.status = status;
        this.userType = userType;
        this.email = email;
        this.address = address;
    }

    // Getters and Setters
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getBarangay() {
        return barangay;
    }

    public void setBarangay(String barangay) {
        this.barangay = barangay;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getSelfieVerificationUrl() {
        return selfieVerificationUrl;
    }

    public void setSelfieVerificationUrl(String selfieVerificationUrl) {
        this.selfieVerificationUrl = selfieVerificationUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    // Helper method to get full name
    public String getFullName() {
        if (middleName != null && !middleName.isEmpty()) {
            return firstName + " " + middleName + (lastName != null && !lastName.isEmpty() ? " " + lastName : "");
        } else {
            return firstName + (lastName != null && !lastName.isEmpty() ? " " + lastName : "");
        }
    }

    // Helper method to calculate age
    public int getAge() {
        if (birthday == null || birthday.isEmpty()) {
            return 0;
        }
        
        try {
            // Assuming birthday format is "MM/DD/YYYY" or "MM - DD - YYYY"
            String cleanBirthday = birthday.replace(" - ", "/");
            String[] parts = cleanBirthday.split("/");
            if (parts.length == 3) {
                int birthYear = Integer.parseInt(parts[2]);
                int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                return currentYear - birthYear;
            }
        } catch (Exception e) {
            // Log error if needed
        }
        return 0;
    }
}
