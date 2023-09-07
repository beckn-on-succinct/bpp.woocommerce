package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;
import in.succinct.beckn.BecknStrings;
import in.succinct.bpp.woocommerce.model.Attributes.Attribute;
import org.json.simple.JSONArray;

public class Attributes extends BecknObjectsWithId<Attribute> {

    public Attributes() {
    }

    public Attributes(JSONArray array) {
        super(array);
    }

    public static  class Attribute extends WooCommerceObjectWithId{

        public String getName(){
            return get("name");
        }

        public int getPosition(){
            return getInteger("position");
        }

        public BecknStrings getOptions(){
            return get(BecknStrings.class, "options");
        }

    }
}
