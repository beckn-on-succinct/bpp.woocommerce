package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;



public class Continents extends BecknObjectsWithId<Continents.Continent> {

    public Continents() {
    }

    public Continents(JSONArray array) {
        super(array);
    }

    public enum ContinentAttribute {
        CODE("code"),
        NAME("name"),
        COUNTRIES("countries");

        private final String key;

        ContinentAttribute(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    public String getApiEndpoint() {
        return String.format("/settings/%s/%s", SettingGroup.DATA.getKey(), SettingAttribute.AttributeKey.CONTINENTS.getKey());
    }


    public static class Continent extends WooCommerceObjectWithId {
        public Continent(JSONObject object) {
            super(object);
        }

        public Countries getCountries() {
            return get(Countries.class, ContinentAttribute.COUNTRIES.getKey());
        }
    }



}
