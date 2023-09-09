package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Taxes extends BecknObjectsWithId<Taxes.Tax> {
    public Taxes() {

    }

    public Taxes(JSONArray object) {
        super(object);
    }

    public static class Tax extends WooCommerceObjectWithId {
        public Tax(JSONObject object) {
            super(object);
        }

        public String getTaxClass() {
            return  get(AttributeKey.tax_class.getKey());
        }

        public String getRate() {
            return  get(AttributeKey.rate.getKey());
        }
    }


    public static enum AttributeKey {
        taxes("taxes"),
        tax_class("class"),
        rate("rate");

        private final String key;

        AttributeKey(String key){
            this.key = key;
        }

        public String getKey(){
            return key;
        }
    }
}
