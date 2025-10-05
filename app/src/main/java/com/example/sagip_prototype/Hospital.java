package com.example.sagip_prototype;

public class Hospital {
    private String documentId;
    private String hospitalName;
    private String contactNumber;
    private String email;
    private String address;
    private String status;
    private String userType;
    private String profileImageUrl;
    private String emergencyContact;
    private String specialization;
    private int bedCapacity;
    private int availableBeds;
    private boolean isEmergencyReady;
    private double latitude;
    private double longitude;

    // Constructors
    public Hospital() {}

    public Hospital(String hospitalName, String contactNumber, String email, String address) {
        this.hospitalName = hospitalName;
        this.contactNumber = contactNumber;
        this.email = email;
        this.address = address;
    }

    // Getters and Setters
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public int getBedCapacity() { return bedCapacity; }
    public void setBedCapacity(int bedCapacity) { this.bedCapacity = bedCapacity; }

    public int getAvailableBeds() { return availableBeds; }
    public void setAvailableBeds(int availableBeds) { this.availableBeds = availableBeds; }

    public boolean isEmergencyReady() { return isEmergencyReady; }
    public void setEmergencyReady(boolean emergencyReady) { isEmergencyReady = emergencyReady; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    // Helper methods
    public String getBedStatus() {
        if (bedCapacity > 0) {
            return availableBeds + "/" + bedCapacity + " beds available";
        }
        return "Capacity not specified";
    }

    public String getStatusDisplay() {
        if (status != null) {
            switch (status.toLowerCase()) {
                case "active":
                    return "Open";
                case "busy":
                    return "Busy";
                case "closed":
                    return "Closed";
                default:
                    return status;
            }
        }
        return "Unknown";
    }
}
