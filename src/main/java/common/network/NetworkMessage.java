package common.network;

import java.io.Serializable;

/**
 * Gói tin chuẩn (NetworkMessage) dùng chung giữa Client và Server.
 * Lớp này được serialize qua ObjectOutputStream và deserialize qua ObjectInputStream.
 *
 * Các trường bắt buộc theo đặc tả:
 * - MessageType type: Loại thông điệp (Request / Response / Broadcast)
 * - Object payload: Dữ liệu thực tế đi kèm thông điệp (DTO, Model, Collection,...)
 * - String status: Trạng thái xử lý ("SUCCESS", "FAILED",...)
 * - String errorMessage: Thông điệp lỗi chi tiết khi xử lý thất bại
 */
public class NetworkMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_INFO = "INFO";

    private MessageType type;
    private Object payload;
    private String status;
    private String errorMessage;

    /**
     * Khởi tạo gói tin rỗng (no-argument constructor).
     */
    public NetworkMessage() {
    }

    /**
     * Khởi tạo gói tin chỉ với loại thông điệp.
     *
     * @param type loại thông điệp
     */
    public NetworkMessage(MessageType type) {
        this(type, null, null, null);
    }

    /**
     * Khởi tạo gói tin với loại thông điệp và payload.
     *
     * @param type loại thông điệp
     * @param payload dữ liệu đi kèm
     */
    public NetworkMessage(MessageType type, Object payload) {
        this(type, payload, null, null);
    }

    /**
     * Khởi tạo đầy đủ các trường của gói tin.
     *
     * @param type loại thông điệp
     * @param payload dữ liệu đi kèm
     * @param status trạng thái xử lý
     * @param errorMessage thông báo lỗi (nếu có)
     */
    public NetworkMessage(MessageType type, Object payload, String status, String errorMessage) {
        this.type = type;
        this.payload = payload;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    // ==========================================
    // Static Factory Methods tiện lợi
    // ==========================================

    /**
     * Tạo thông điệp Request gửi từ Client lên Server.
     */
    public static NetworkMessage createRequest(MessageType type, Object payload) {
        if (type == null || !type.isRequest()) {
            throw new IllegalArgumentException("MessageType phải thuộc nhóm Request");
        }
        return new NetworkMessage(type, payload, null, null);
    }

    /**
     * Tạo thông điệp phản hồi thành công (Response Success).
     */
    public static NetworkMessage createSuccessResponse(MessageType type, Object payload) {
        return new NetworkMessage(type, payload, STATUS_SUCCESS, null);
    }

    /**
     * Tạo thông điệp phản hồi thất bại (Response Failed).
     */
    public static NetworkMessage createErrorResponse(MessageType type, String errorMessage) {
        return new NetworkMessage(type, null, STATUS_FAILED, errorMessage);
    }

    /**
     * Tạo thông điệp phát sóng (Broadcast Notification).
     */
    public static NetworkMessage createBroadcast(MessageType type, Object payload) {
        if (type == null || !type.isBroadcast()) {
            throw new IllegalArgumentException("MessageType phải thuộc nhóm Broadcast");
        }
        return new NetworkMessage(type, payload, STATUS_INFO, null);
    }

    // ==========================================
    // Getters and Setters
    // ==========================================

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     * Ép kiểu an toàn (Safe cast) cho Payload sang kiểu mong muốn.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadAs(Class<T> clazz) {
        if (payload == null) {
            return null;
        }
        if (clazz.isInstance(payload)) {
            return (T) payload;
        }
        throw new ClassCastException("Payload thuộc kiểu " + payload.getClass().getName()
                + ", không thể ép sang " + clazz.getName());
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return STATUS_SUCCESS.equalsIgnoreCase(this.status);
    }

    @Override
    public String toString() {
        return "NetworkMessage{" +
                "type=" + type +
                ", status='" + status + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", payload=" + (payload != null ? payload.getClass().getSimpleName() : "null") +
                '}';
    }
}

