package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;
import in.succinct.bpp.woocommerce.model.States.State;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public  class States extends BecknObjectsWithId<State> {

    public States(JSONArray array) {
        super(array);
    }

    public static class State extends WooCommerceObjectWithId{

        public State(JSONObject object) {
            super(object);
        }

        public String getCode(){
            return get("code");
        }


        public String getName(){
            return get("name");
        }

    }

    public State getState(String code){
        return get(State.class, code);
    }

}
