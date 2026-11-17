package app;

/**
 * Lớp main phụ để chạy client trực tiếp trong IDE.
 * <p>
 * Khi chạy thẳng một lớp kế thừa javafx.application.Application mà JavaFX không
 * nằm trên module path, Java báo "JavaFX runtime components are missing".
 * Gọi qua một lớp main bình thường như lớp này thì không gặp lỗi đó.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        AuctionApp.main(args);
    }
}
