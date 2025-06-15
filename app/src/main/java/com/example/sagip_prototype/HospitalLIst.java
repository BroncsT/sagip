package com.example.sagip_prototype;

public class HospitalLIst {
    String hospitalName ;
    Geopoint currentLocation;

    public HospitalLIst() {
    }

    public HospitalLIst(String hospitalName, Geopoint currentLocation) {
        this.hospitalName = hospitalName;
        this,
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

}



