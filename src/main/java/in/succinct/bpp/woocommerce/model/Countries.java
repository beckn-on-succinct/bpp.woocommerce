package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class Countries extends BecknObjectsWithId<Countries.Country> {

    public Countries(JSONArray array) {
        super(array);
    }

    public enum CountryAttribute {
        CODE("code"),
        NAME("name"),
        CURRENCY_CODE("currency_code"),
        WEIGHT_UNIT("weight_unit"),
        DIMENSION_UNIT("dimension_unit"),
        STATE("state"),;

        private final String key;

        CountryAttribute(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    public static class Country extends WooCommerceObjectWithId {

        public Country(JSONObject object) {
            super(object);
        }

        public String getAttribute(CountryAttribute attribute){
            return get(attribute.getKey());
        }



        public States getState() {
            return get(States.class, CountryAttribute.STATE.getKey());
        }
    }

    public Country getCountry(String code){
        return get(Country.class, code);
    }


}
