package common.network.payload;

import java.io.Serializable;

/**
 * Payload cho REGISTER_REQUEST (Client gửi lên Server để đăng ký tài khoản).
 */
public class RegisterRequestPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String phoneNumber;
    private String password;
    private String address;
    private String role; // "BIDDER", "SELLER", "ADMIN"

    public RegisterRequestPayload() {
    }

    public RegisterRequestPayload(String name, String phoneNumber, String password, String address, String role) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.password = password;
        this.address = address;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "RegisterRequestPayload{" +
                "name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", address='" + address + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}

