package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class Countries extends BecknObjectsWithId<Countries.Country> {

    public Countries(JSONArray array) {
        super(array);
    }

    public static class Country extends WooCommerceObjectWithId {

        public Country(JSONObject object) {
            super(object);
        }

        public String getAttribute(AttributeKey attribute){
            return get(attribute.getKey());
        }

        public States getState() {
            return get(States.class, AttributeKey.state.getKey());
        }
    }

    public Country getCountry(String code){
        return get(Country.class, code);
    }

}
