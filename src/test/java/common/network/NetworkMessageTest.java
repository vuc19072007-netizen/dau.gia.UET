package common.network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import common.network.payload.*;

class NetworkMessageTest {

    @Test
    @DisplayName("Kiểm tra phân loại MessageType đúng nhóm Request, Response, Broadcast")
    void testMessageTypeCategories() {
        // Request group
        assertTrue(MessageType.LOGIN_REQUEST.isRequest());
        assertTrue(MessageType.REGISTER_REQUEST.isRequest());
        assertTrue(MessageType.GET_AUCTIONS_REQUEST.isRequest());
        assertTrue(MessageType.CREATE_AUCTION_REQUEST.isRequest());
        assertTrue(MessageType.PLACE_BID_REQUEST.isRequest());
        assertFalse(MessageType.LOGIN_REQUEST.isResponse());
        assertFalse(MessageType.LOGIN_REQUEST.isBroadcast());

        // Response group
        assertTrue(MessageType.LOGIN_RESPONSE.isResponse());
        assertTrue(MessageType.ACTION_SUCCESS.isResponse());
        assertTrue(MessageType.ACTION_FAILED.isResponse());
        assertFalse(MessageType.ACTION_SUCCESS.isRequest());
        assertFalse(MessageType.ACTION_SUCCESS.isBroadcast());

        // Broadcast group
        assertTrue(MessageType.NEW_BID_NOTIFICATION.isBroadcast());
        assertTrue(MessageType.AUCTION_CLOSED_NOTIFICATION.isBroadcast());
        assertFalse(MessageType.NEW_BID_NOTIFICATION.isRequest());
        assertFalse(MessageType.NEW_BID_NOTIFICATION.isResponse());
    }

    @Test
    @DisplayName("Kiểm tra tuần tự hóa (Serialization) và giải tuần tự hóa (Deserialization) của NetworkMessage")
    void testSerializationRoundTrip() throws Exception {
        LoginRequestPayload payload = new LoginRequestPayload("0987654321", "secretPass");
        NetworkMessage original = NetworkMessage.createRequest(MessageType.LOGIN_REQUEST, payload);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
            oos.flush();
        }

        byte[] serializedData = baos.toByteArray();
        assertTrue(serializedData.length > 0);

        NetworkMessage deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData))) {
            deserialized = (NetworkMessage) ois.readObject();
        }

        assertNotNull(deserialized);
        assertEquals(MessageType.LOGIN_REQUEST, deserialized.getType());
        assertTrue(deserialized.getPayload() instanceof LoginRequestPayload);
        LoginRequestPayload deserializedPayload = (LoginRequestPayload) deserialized.getPayload();
        assertEquals("0987654321", deserializedPayload.getPhoneNumber());
        assertEquals("secretPass", deserializedPayload.getPassword());
    }

    @Test
    @DisplayName("Kiểm tra các Factory Method tiện ích của NetworkMessage")
    void testFactoryMethods() {
        // createRequest
        NetworkMessage req = NetworkMessage.createRequest(MessageType.PLACE_BID_REQUEST, "payload");
        assertEquals(MessageType.PLACE_BID_REQUEST, req.getType());
        assertEquals("payload", req.getPayload());

        // createSuccessResponse
        NetworkMessage resSuccess = NetworkMessage.createSuccessResponse(MessageType.ACTION_SUCCESS, "Thành công");
        assertEquals(NetworkMessage.STATUS_SUCCESS, resSuccess.getStatus());
        assertTrue(resSuccess.isSuccess());
        assertNull(resSuccess.getErrorMessage());

        // createErrorResponse
        NetworkMessage resError = NetworkMessage.createErrorResponse(MessageType.ACTION_FAILED, "Lỗi cú pháp");
        assertEquals(NetworkMessage.STATUS_FAILED, resError.getStatus());
        assertFalse(resError.isSuccess());
        assertEquals("Lỗi cú pháp", resError.getErrorMessage());

        // createBroadcast
        NetworkMessage broadcast = NetworkMessage.createBroadcast(MessageType.NEW_BID_NOTIFICATION, "bid_info");
        assertEquals(MessageType.NEW_BID_NOTIFICATION, broadcast.getType());
        assertTrue(broadcast.getType().isBroadcast());
    }

    @Test
    @DisplayName("Kiểm tra Serializable của tất cả các Payload DTO")
    void testAllPayloadSerialization() throws Exception {
        assertCanSerialize(new LoginRequestPayload("0123456789", "pass123"));
        assertCanSerialize(new RegisterRequestPayload("Nguyen A", "0123456789", "pass123", "HN", "BIDDER"));
        assertCanSerialize(new CreateAuctionRequestPayload("Item", "ART", "Desc", BigDecimal.TEN, BigDecimal.ONE, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "seller1"));
        assertCanSerialize(new PlaceBidRequestPayload("auc-1", "bidder-1", BigDecimal.valueOf(500)));
        assertCanSerialize(new BidNotificationPayload("auc-1", "bid-1", "u-1", "Nguyen A", BigDecimal.valueOf(500), BigDecimal.valueOf(500), LocalDateTime.now()));
        assertCanSerialize(new AuctionClosedNotificationPayload("auc-1", "u-1", "Nguyen A", BigDecimal.valueOf(500), LocalDateTime.now(), "FINISHED"));
        assertCanSerialize(new AuctionDTO());
    }

    private void assertCanSerialize(Object object) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            oos.flush();
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            Object read = ois.readObject();
            assertNotNull(read);
            assertEquals(object.getClass(), read.getClass());
        }
    }
}
