package common.network.payload;

import java.io.Serializable;

/**
 * Payload cho LOGIN_REQUEST (Client gửi lên Server để đăng nhập).
 */
public class LoginRequestPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private String phoneNumber;
    private String password;

    public LoginRequestPayload() {
    }

    public LoginRequestPayload(String phoneNumber, String password) {
        this.phoneNumber = phoneNumber;
        this.password = password;
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

    @Override
    public String toString() {
        return "LoginRequestPayload{" +
                "phoneNumber='" + phoneNumber + '\'' +
                ", password='***'" +
                '}';
    }
}

