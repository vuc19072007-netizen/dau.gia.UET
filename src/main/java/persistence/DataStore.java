package persistence;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import model.auction.Auction;
import model.user.User;

/**
 * Lưu và tải toàn bộ trạng thái hệ thống bằng Java Serialization.
 * <p>
 * Dữ liệu được lưu gồm: người dùng, phiên đấu giá (kèm lịch sử bid và
 * khoản tiền giữ trong ví của từng bidder). Sau khi khởi động lại, giá
 * dẫn đầu và khoản giữ trong ví vẫn khớp với trước khi tắt.
 * <p>
 * Lớp này dùng một {@link SystemState} trung gian (implements Serializable)
 * để gom toàn bộ dữ liệu vào một file duy nhất.
 */
public final class DataStore {

    /** Tên file mặc định lưu trong thư mục làm việc. */
    public static final String DEFAULT_FILE = "auction-data.ser";

    private final Path file;

    public DataStore() {
        this(DEFAULT_FILE);
    }

    public DataStore(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tên file không được để trống");
        }
        this.file = Path.of(fileName);
    }

    /**
     * Lưu toàn bộ trạng thái vào file.
     *
     * @param users    danh sách người dùng
     * @param auctions danh sách phiên đấu giá
     */
    public void save(List<User> users, List<Auction> auctions) {
        SystemState state = new SystemState(users, auctions);
        try (ObjectOutputStream out = new ObjectOutputStream(
                Files.newOutputStream(file))) {
            out.writeObject(state);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Không thể lưu dữ liệu vào " + file, error);
        }
    }

    /**
     * Tải trạng thái từ file.
     *
     * @return trạng thái đã lưu, hoặc {@code null} nếu file chưa tồn tại.
     */
    public SystemState load() {
        if (!Files.exists(file)) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(
                Files.newInputStream(file))) {
            Object value = in.readObject();
            if (!(value instanceof SystemState state)) {
                throw new IllegalStateException(
                        "File dữ liệu không đúng định dạng: " + file);
            }
            return state;
        } catch (IOException | ClassNotFoundException error) {
            throw new IllegalStateException(
                    "Không thể tải dữ liệu từ " + file, error);
        }
    }

    /** Kiểm tra file dữ liệu đã tồn tại chưa. */
    public boolean exists() {
        return Files.exists(file);
    }

    /**
     * Trạng thái toàn bộ hệ thống, đóng gói để serialize một lần.
     */
    public static final class SystemState implements Serializable {

        private static final long serialVersionUID = 1L;

        private final List<User> users;
        private final List<Auction> auctions;

        public SystemState(List<User> users, List<Auction> auctions) {
            this.users = new ArrayList<>(
                    users == null ? List.of() : users);
            this.auctions = new ArrayList<>(
                    auctions == null ? List.of() : auctions);
        }

        public List<User> getUsers() {
            return users;
        }

        public List<Auction> getAuctions() {
            return auctions;
        }
    }
}
