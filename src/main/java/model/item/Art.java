package model.item;
import java.math.BigDecimal;
public class Art extends Item {
    private String artist;
    private int yearPublished;
    public Art(String name, BigDecimal startingPrice) {
        super(name, startingPrice);
    }
    public Art(String id, String name, BigDecimal startingPrice) {
        super(id, name, startingPrice);
    }
    public String getArtist() {
        return artist;
    }
    public void setArtist(String artist) {
        this.artist = artist;
    }
    public int getYearPublished() {
        return yearPublished;
    }
    public void setYearPublished(int yearPublished) {
        this.yearPublished = yearPublished;
    }
    
    @Override
    public String getCategory() {
        return "Art";
    }
}
