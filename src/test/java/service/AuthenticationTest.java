package service;
import model.user.Bidder;
import org.junit.jupiter.api.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationTest {
    static final AtomicInteger phone = new AtomicInteger(1000000000);
    AuctionManager manager;
    Bidder bidder;
    @BeforeEach void setup() {
        manager = AuctionManager.getInstance();
        bidder = new Bidder("Account", Integer.toString(phone.getAndIncrement()), "password1", "HN");
        manager.addUser(bidder);
    }
    @Test void validLoginReturnsRegisteredObject() {
        assertSame(bidder, manager.login(bidder.getPhoneNumber(), "password1"));
    }
    @Test void rejectsWrongPassword() {
        assertThrows(IllegalArgumentException.class, () -> manager.login(bidder.getPhoneNumber(), "wrong"));
    }
    @Test void rejectsMissingInput() {
        assertThrows(IllegalArgumentException.class, () -> manager.login(null, "password1"));
    }
    @Test void blockedAccountCannotLogin() {
        bidder.setActive(false);
        assertThrows(IllegalStateException.class, () -> manager.login(bidder.getPhoneNumber(), "password1"));
    }
}
