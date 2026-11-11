package model.user;

public class Admin extends User{
    public Admin(String id,String name,String phoneNumber,String password,String address){
        super(id,name,phoneNumber,password,address,"ADMIN");
    }
    public Admin(String name,String phoneNumber,String password,String address){
        super(name,phoneNumber,password,address,"ADMIN");
    }
    public void banUser(User user) {
        if(user==null){
            throw new IllegalArgumentException("Người dùng không tồn tại");
        }
        if(!user.isActive()){
            throw new IllegalArgumentException("Tài khoản đã bị chặn trước đó");
        }if(user instanceof Admin){
            throw new IllegalArgumentException("Không thể khóa admin khác");
        }
        user.setActive(false);
    }
    public void unBanUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Người dùng không tồn tại");
        }
        if (user.isActive()) {
            throw new IllegalArgumentException("Tài khoản này đang hoạt động bình thường");
        }
        user.setActive(true);
    }
}
