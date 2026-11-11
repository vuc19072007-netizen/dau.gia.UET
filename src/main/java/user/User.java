package model.user;
import model.Entity;
public abstract class User extends Entity{
    private String name;
    private String phoneNumber;
    private String password;
    private String address;
    private boolean isActive=true;
    private String role;
    //khong truyen id
    public User(String name,String phoneNumber,String password,String address,String role){
        super();
        initializeUser(name,phoneNumber,password,address,role);
    }
    //truyen id
    public User(String id,String name,String phoneNumber,String password,String address,String role){
        super(id);
        initializeUser(name,phoneNumber,password,address,role);
    }
    private void initializeUser(String name,String phoneNumber,String password,String address,String role){
        validatePhoneNumber(phoneNumber);
        validatePassword(password);
        this.name = validateName(name);
        this.phoneNumber=phoneNumber;
        this.password=password;
        this.address=address;
        this.role=validateRole(role);

    }
    //hàm số điện thoại phải 10 số
    private void validatePhoneNumber(String phone){
        if(phone==null|| !phone.matches("\\d{10}")){
            throw new IllegalArgumentException("Số điện thoại phải gồm 10 chữ số");
        }
    }
    // hàm kiểm tra mật khẩu phải từ 8 đến 15 ký tự
    private void validatePassword(String pass){
        if(pass==null||pass.length()<8||pass.length()>15){
            throw new IllegalArgumentException("Mật khẩu phải có 8 đến 15 ký tự");
        }
    }
    // hàm kiểm tra tên không được để trống
    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên không được để trống");
        }
        return name.trim();
    }
    // hàm kiểm tra vai trò phải là BIDDER, SELLER hoặc ADMIN
    private String validateRole(String role) {
        if (role == null) {
            throw new IllegalArgumentException("Vai trò không được null");
        }
        if (!role.equals("BIDDER")
                && !role.equals("SELLER")
                && !role.equals("ADMIN")) {
            throw new IllegalArgumentException(
                "Vai trò phải là BIDDER, SELLER hoặc ADMIN");
        }
        return role;
    }
    public String getName(){
        return name;
    }
    public String getPhoneNumber(){
        return phoneNumber;
    }
    public String getPassword(){
        return password;
    }
    public String getAddress(){
        return address;
    }
    public String getRole(){
        return role;
    }
    public synchronized boolean isActive(){
        return isActive;
    }
    public void setPassword(String newPassword){
        validatePassword(newPassword);
        this.password=newPassword;
        markUpdated();
    }
    public synchronized void setActive(boolean active){
        this.isActive=active;
        markUpdated();
    }
    public void setName(String name) {
        this.name = validateName(name);
        markUpdated();
    }    

    public void setAddress(String address) {
        this.address = address;
        markUpdated();
    }
}
