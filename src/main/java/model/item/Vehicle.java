package model.item;
import java.math.BigDecimal;
public class Vehicle extends Item {
    private String manufacturer;
    private int yearMade;
    public Vehicle(String name, BigDecimal startingPrice) {
        super(name, startingPrice);
    }
    public Vehicle(String id, String name, BigDecimal startingPrice) {
        super(id, name, startingPrice);
    }
    public String getManufacturer() {
        return manufacturer;
    }
    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }
    public int getYearMade() {
        return yearMade;
    }
    public void setYearMade(int yearMade) {
        this.yearMade = yearMade;
    }
    
    @Override
    public String getCategory() {
        return "Vehicle";
    }
}
