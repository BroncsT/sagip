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
    
    // Senior information fields for emergency cases
    private String seniorName;
    private String seniorPhone;
    private String seniorAddress;
    private String rescuerName;
    private String rescuerPhone;
    private String emergencyId;
    private Long emergencyTimestamp;
    private Double estimatedArrivalMinutes;
    private Boolean hasIncomingEmergency;

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

        // Calculate capacity percentage (occupied beds / total beds)
        int occupiedBeds = totalBeds - availableBeds;
        double capacityPercentage = ((double) occupiedBeds / totalBeds) * 100;

        // Calculate workload per available doctor (occupied beds per doctor)
        double occupiedBedsPerDoctor = (double) occupiedBeds / doctorsAvailable;

        String result;
        if (availableBeds == 0) {
            result = "crowded";
        } else if (capacityPercentage >= 90 || occupiedBedsPerDoctor >= 8) {
            result = "crowded";
        } else if (capacityPercentage >= 70 || occupiedBedsPerDoctor >= 5) {
            result = "busy";
        } else {
            result = "available";
        }

        // Staffing safeguard: 1 available doctor should not show as AVAILABLE
        if (doctorsAvailable == 1 && "available".equalsIgnoreCase(result)) {
            result = "busy";
        }

        return result;
    }
    
    // Getter and setter methods for senior information
    public String getSeniorName() {
        return seniorName;
    }
    
    public void setSeniorName(String seniorName) {
        this.seniorName = seniorName;
    }
    
    public String getSeniorPhone() {
        return seniorPhone;
    }
    
    public void setSeniorPhone(String seniorPhone) {
        this.seniorPhone = seniorPhone;
    }
    
    public String getSeniorAddress() {
        return seniorAddress;
    }
    
    public void setSeniorAddress(String seniorAddress) {
        this.seniorAddress = seniorAddress;
    }
    
    public String getRescuerName() {
        return rescuerName;
    }
    
    public void setRescuerName(String rescuerName) {
        this.rescuerName = rescuerName;
    }
    
    public String getRescuerPhone() {
        return rescuerPhone;
    }
    
    public void setRescuerPhone(String rescuerPhone) {
        this.rescuerPhone = rescuerPhone;
    }
    
    public String getEmergencyId() {
        return emergencyId;
    }
    
    public void setEmergencyId(String emergencyId) {
        this.emergencyId = emergencyId;
    }
    
    public Long getEmergencyTimestamp() {
        return emergencyTimestamp;
    }
    
    public void setEmergencyTimestamp(Long emergencyTimestamp) {
        this.emergencyTimestamp = emergencyTimestamp;
    }
    
    public Double getEstimatedArrivalMinutes() {
        return estimatedArrivalMinutes;
    }
    
    public void setEstimatedArrivalMinutes(Double estimatedArrivalMinutes) {
        this.estimatedArrivalMinutes = estimatedArrivalMinutes;
    }
    
    public Boolean getHasIncomingEmergency() {
        return hasIncomingEmergency;
    }
    
    public void setHasIncomingEmergency(Boolean hasIncomingEmergency) {
        this.hasIncomingEmergency = hasIncomingEmergency;
    }
}

