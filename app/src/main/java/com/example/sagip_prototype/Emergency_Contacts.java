package com.example.sagip_prototype;

public class Emergency_Contacts {

    String name, number, address, relationship;

    public Emergency_Contacts() {
    }

    public Emergency_Contacts(String name, String number, String address, String relationship) {
        this.name = name;
        this.number = number;
        this.address = address;
        this.relationship = relationship;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }
}
