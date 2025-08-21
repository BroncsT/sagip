package com.example.sagip_prototype;

public class HospitalLIst {
    private String hospitalName;
    private String hospitalAddress;
    private Integer totalBeds;
    private Integer availableBeds;
    private Integer doctorsAvailable;
    private String erStatus;
    private Double capacityPercentage;
    private String lastUpdated;

    public HospitalLIst() {
    }

    public HospitalLIst(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public HospitalLIst(String hospitalName, String hospitalAddress, Integer totalBeds, 
                       Integer availableBeds, Integer doctorsAvailable, String erStatus, 
                       Double capacityPercentage, String lastUpdated) {
        this.hospitalName = hospitalName;
        this.hospitalAddress = hospitalAddress;
        this.totalBeds = totalBeds;
        this.availableBeds = availableBeds;
        this.doctorsAvailable = doctorsAvailable;
        this.erStatus = erStatus;
        this.capacityPercentage = capacityPercentage;
        this.lastUpdated = lastUpdated;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getHospitalAddress() {
        return hospitalAddress;
    }

    public void setHospitalAddress(String hospitalAddress) {
        this.hospitalAddress = hospitalAddress;
    }

    public Integer getTotalBeds() {
        return totalBeds;
    }

    public void setTotalBeds(Integer totalBeds) {
        this.totalBeds = totalBeds;
    }

    public Integer getAvailableBeds() {
        return availableBeds;
    }

    public void setAvailableBeds(Integer availableBeds) {
        this.availableBeds = availableBeds;
    }

    public Integer getDoctorsAvailable() {
        return doctorsAvailable;
    }

    public void setDoctorsAvailable(Integer doctorsAvailable) {
        this.doctorsAvailable = doctorsAvailable;
    }

    public String getErStatus() {
        return erStatus;
    }

    public void setErStatus(String erStatus) {
        this.erStatus = erStatus;
    }

    public Double getCapacityPercentage() {
        return capacityPercentage;
    }

    public void setCapacityPercentage(Double capacityPercentage) {
        this.capacityPercentage = capacityPercentage;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Helper method to get status color
    public int getStatusColor() {
        String status = getCalculatedStatus();
        switch (status.toLowerCase()) {
            case "available":
                return 0xFF4CAF50; // Green
            case "busy":
                return 0xFFFF9800; // Orange
            case "crowded":
                return 0xFFF44336; // Red
            default:
                return 0xFF9E9E9E; // Gray
        }
    }

    // Helper method to get status emoji
    public String getStatusEmoji() {
        String status = getCalculatedStatus();
        switch (status.toLowerCase()) {
            case "available":
                return "🟢";
            case "busy":
                return "🟡";
            case "crowded":
                return "🔴";
            default:
                return "⚪";
        }
    }

    // Helper method to get calculated status
    public String getCalculatedStatus() {
        if (totalBeds == null || availableBeds == null || doctorsAvailable == null) {
            return "unknown";
        }

        // Validate input
        if (totalBeds <= 0 || availableBeds < 0 || doctorsAvailable <= 0) {
            return "unknown";
        }
        
        if (availableBeds > totalBeds) {
            return "unknown";
        }

        // Calculate capacity percentage
        double capacityPercentage = ((double) (totalBeds - availableBeds) / totalBeds) * 100;
        
        // Calculate beds per doctor ratio
        double bedsPerDoctor = (double) totalBeds / doctorsAvailable;
        
        // Automatic status logic based on multiple factors
        if (capacityPercentage >= 90 || availableBeds == 0) {
            return "crowded"; // At or near capacity
        } else if (capacityPercentage >= 70 || bedsPerDoctor > 8 || doctorsAvailable < 2) {
            return "busy"; // High capacity or insufficient staff
        } else if (capacityPercentage >= 50 || bedsPerDoctor > 6) {
            return "busy"; // Moderate capacity
        } else {
            return "available"; // Good capacity and staff ratio
        }
    }
}

