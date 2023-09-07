package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectWithId;
import org.json.simple.JSONObject;

/**
 * A base class representing objects in a WooCommerce context with an ID.
 * Extends the BecknObjectWithId class.
 */
public class WooCommerceObjectWithId extends BecknObjectWithId {

    /**
     * Constructs a new WooCommerceObjectWithId.
     */
    public WooCommerceObjectWithId() {
        // No additional logic needed in this constructor.
    }

    /**
     * Constructs a new WooCommerceObjectWithId with the given ID.
     * @param id The ID of the object.
     */
    public WooCommerceObjectWithId(String id) {
        super(id);
    }

    /**
     * Constructs a new WooCommerceObjectWithId from a JSON object.
     * @param object The JSON object containing data for the object.
     */
    public WooCommerceObjectWithId(JSONObject object) {
        super(object);
    }

    /**
     * Get the ID of the object.
     * @return The ID of the object.
     */
    @Override
    public String getId() {
        return super.getId();
    }
}
