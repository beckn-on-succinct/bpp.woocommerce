package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class Countries extends BecknObjectsWithId<Countries.Country> {

    public Countries() {
    }

    public Countries(JSONArray array) {
        super(array);
    }

    public static class Country extends WooCommerceObjectWithId {

        public Country(JSONObject object) {
            super(object);
        }

        public Country() {
        }

        public String getId(){
            return get("code");
        }
        public void setId(String id){
            set("code",id);
        }

        public String getAttribute(AttributeKey attribute){
            return get(attribute.getKey());
        }

        public States getStates() {
            return get(States.class, AttributeKey.states.getKey());
        }
    }


}
