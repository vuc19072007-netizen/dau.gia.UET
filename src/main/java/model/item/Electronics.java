package model.item;
import java.math.BigDecimal;
public class Electronics extends Item {
    private String brand;
    private String model;
    public Electronics(String name, BigDecimal startingPrice) {
        super(name, startingPrice);
    }
    public Electronics(String id, String name, BigDecimal startingPrice) {
        super(id, name, startingPrice);
    }
    public String getBrand() {
        return brand;
    }
    public void setBrand(String brand) {
        this.brand = brand;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    
    @Override
    public String getCategory() {
        return "Electronics";
    }
}
