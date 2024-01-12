package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;
import in.succinct.bpp.woocommerce.model.States.State;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public  class States extends BecknObjectsWithId<State> {

    public States(JSONArray array) {
        super(array);
    }

    public States() {
    }

    public static class State extends WooCommerceObjectWithId{

        public State(JSONObject object) {
            super(object);
        }

        public State() {
        }

        public String getCode(){
            return get(AttributeKey.code.getKey());
        }

        public String getId(){
            return getCode();
        }

        public String getName(){
            return get(AttributeKey.name.getKey());
        }

    }

    public State getState(String code){
        return get(code);
    }

}
