package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObject;
import org.json.simple.JSONObject;

import java.util.Date;
import java.util.Objects;


public class Products extends BecknObject {

    public Products() {
    }


    public  static  class  Product extends WooCommerceObjectWithId{
        public Product() {
        }

        public Product(JSONObject object) {
            super(object);
        }

        public ProductImages getImages(){
            return get(ProductImages.class, "images");
        }

        public Attributes getAttributes(){
            return get(Attributes.class, "attributes");
        }

        public Date getCreatedAtGmt(){
            return getTimestamp("date_created_gmt");
        }

        public String getStatus(){
            return get("status");
        }

        public boolean isPurchasable(){
            return Objects.equals(getStatus(),"purchasable");
        }

        public String getTags(){
            return get("tags");
        }

        public String getName(){
            return get("name");
        }


    }

    public static class ProductVariant extends WooCommerceObjectWithId {

        public ProductVariant() {
        }

        public ProductVariant(JSONObject object) {
            super(object);
        }

        public double getWeight(){
            return getDouble("weight");
        }


    }


}
