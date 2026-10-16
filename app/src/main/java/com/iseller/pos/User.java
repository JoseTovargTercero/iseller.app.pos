// User.java
package com.iseller.pos;

public class User {

    private String name;
    private String status; // This will hold the phone number
    private String jefe;

    public User(String name, String status, String jefe) {
        this.name = name;
        this.status = status;
        this.jefe = jefe;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getJefe() {
        return jefe;
    }


}
