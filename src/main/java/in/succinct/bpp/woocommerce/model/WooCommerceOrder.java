package in.succinct.bpp.woocommerce.model;

import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.State;
import in.succinct.beckn.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Date;

public class WooCommerceOrder extends WooCommerceObjectWithId {

    public WooCommerceOrder() {

    }

    public WooCommerceOrder(JSONObject object) {

        super(object);
    }

    public boolean isOrderPaid() {
        String dateOfPayment = getPaidDateGmt();
        return dateOfPayment != null && !dateOfPayment.isEmpty();
    }

    public String getTotal() {
        return get(AttributeKey.total.getKey());
    }

    public String getStatus() {
        return get(AttributeKey.status.getKey());
    }

    public String getNumber() {
        return get(AttributeKey.number.getKey());
    }

    public Order.Status getBecknOrderStatus() {
        String status = getStatus();

        switch (status) {
            case "completed":
                return Order.Status.Completed;
            case "cancelled":
                return Order.Status.Cancelled;
            case "pending":
                // Handle pending status if needed
                return Order.Status.Awaiting_Agent_Acceptance;
            case "processing":
                return Order.Status.In_progress;
            // Add more cases for other WooCommerce statuses if necessary
            default:
                // Handle any other statuses or provide a default mapping
                return Order.Status.Created;
        }
    }

    public String getPaidDateGmt() {
        return get(AttributeKey.datePaidGmt.getKey());
    }

    public Date getCreateDateGmt() {
        return getTimestamp(AttributeKey.dateCreatedGmt.getKey());
    }

    public Date getUpdatedDateGmt() {
        return getTimestamp(AttributeKey.dateUpdatedGmt.getKey());
    }

    public void setCurrency(String currency) {
        set(AttributeKey.currency.getKey(), currency);
    }

    public void setSourceName(String source_name) {
        set(AttributeKey.sourceName.getKey(), source_name);
    }

    public void setName(String name) {
        set(AttributeKey.name.getKey(), name);
    }

    public String getPhone() {
        return get(AttributeKey.phone.getKey());
    }

    public void setPhone(String phone) {
        set(AttributeKey.phone.getKey(), phone);
    }

    public String getEmail() {
        return get(AttributeKey.phone.getKey());
    }

    public void setEmail(String email) {
        set(AttributeKey.email.getKey(), email);
    }

    public Long getLocationId() {
        return get(AttributeKey.locationId.getKey());
    }

    public void setId(Long location_id) {
        set(AttributeKey.id.getKey(), location_id);
    }

    public OrderShipping getOrderShipping() {
        return get(OrderShipping.class, AttributeKey.shipping.getKey());
    }

    public void setOrderShipping(OrderShipping orderShipping) {
        set(AttributeKey.shipping.getKey(), orderShipping);
    }

    public void setOrderBilling(OrderBilling orderBilling) {
        set(AttributeKey.billing.getKey(), orderBilling);
    }

    public OrderBilling getOrderBilling() {
        return get(OrderBilling.class, AttributeKey.billing.getKey());
    }

    public void setLineItems(LineItems lineItems) {
        set(AttributeKey.lineItems.getKey(), lineItems);
    }

    public LineItems getLineItems() {
        return get(LineItems.class, AttributeKey.lineItems.getKey());
    }

    public void setMetaDataArray(MetaDataArray metaDataArray) {
        set(AttributeKey.metaDataArray.getKey(), metaDataArray);
    }

    public MetaDataArray getMetaDataArray() {
        return get(MetaDataArray.class, AttributeKey.metaDataArray.getKey());
    }

    public static class OrderBilling extends BecknObject {

        public OrderBilling() {

        }

        public OrderBilling(JSONObject object) {
            super(object);
        }

        public void setFirstName(String firstName) {
            set(AttributeKey.firstName.getKey(), firstName);
        }

        public String getFirstName() {
            return get(AttributeKey.firstName.getKey());
        }

        public void setLastName(String lastName) {
            set(AttributeKey.lastName.getKey(), lastName);
        }

        public String getLastName() {
            return get(AttributeKey.lastName.getKey());
        }

        public void setAddress1(String address1) {
            set(AttributeKey.address1.getKey(), address1);
        }

        public String getAddress1() {
            return get(AttributeKey.address1.getKey());
        }

        public void setAddress2(String address2) {
            set(AttributeKey.address2.getKey(), address2);
        }

        public String getAddress2() {
            return get(AttributeKey.address2.getKey());
        }

        public void setCity(String city) {
            set(AttributeKey.city.getKey(), city);
        }

        public String getCity() {
            return get(AttributeKey.city.getKey());
        }

        public void setStateCode(String stateCode) {
            set(AttributeKey.state.getKey(), stateCode);
        }

        public String getStateCode() {
            return get(AttributeKey.state.getKey());
        }

        public void setPostcode(String postcode) {
            set(AttributeKey.postcode.getKey(), postcode);
        }

        public String getPostcode() {
            return get(AttributeKey.postcode.getKey());
        }

        public void setCountryCode(String country) {
            set(AttributeKey.country.getKey(), country);
        }

        public String getCountryCode() {
            return get(AttributeKey.country.getKey());
        }

        public void setEmail(String email) {
            set(AttributeKey.email.getKey(), email);
        }

        public String getEmail() {
            return get(AttributeKey.email.getKey());
        }

        public void setPhone(String phone) {
            set(AttributeKey.phone.getKey(), phone);
        }

        public String getPhone() {
            return get(AttributeKey.phone.getKey());
        }

        public Billing toBeckn() {
            Billing billing = new Billing();
            billing.setName(getFirstName() + " " + getLastName());
            billing.setPhone(getPhone());
            billing.setEmail(getEmail());

            Address address = new Address();
            address.setName(billing.getName());
            address.setStreet(getAddress1());
            address.setLocality(getAddress2());
            address.setPinCode(getPostcode());
            Country country = Country.findByISO(getCountryCode());
            State state = State.findByCountryAndCode(country.getId(), getStateCode());
            City city = City.findByStateAndName(state.getId(), getCity());
            address.setCountry(country.getName());
            address.setState(state.getName());
            address.setCity(city.getName());

            billing.setAddress(address);

            return billing;
        }

    }

    public static class OrderShipping extends BecknObject {

        public OrderShipping() {

        }

        public OrderShipping(JSONObject object) {
            super(object);
        }

        public void setFirstName(String firstName) {
            set(AttributeKey.firstName.getKey(), firstName);
        }

        public String getFirstName() {
            return get(AttributeKey.firstName.getKey());
        }

        public void setLastName(String lastName) {
            set(AttributeKey.lastName.getKey(), lastName);
        }

        public String getLastName() {
            return get(AttributeKey.lastName.getKey());
        }

        public void setAddress1(String address1) {
            set(AttributeKey.address1.getKey(), address1);
        }

        public String getAddress1() {
            return get(AttributeKey.address1.getKey());
        }

        public void setAddress2(String address2) {
            set(AttributeKey.address2.getKey(), address2);
        }

        public String getAddress2() {
            return get(AttributeKey.address2.getKey());
        }

        public void setCity(String city) {
            set(AttributeKey.city.getKey(), city);
        }

        public String getCity() {
            return get(AttributeKey.city.getKey());
        }

        public void setStateCode(String stateCode) {
            set(AttributeKey.state.getKey(), stateCode);
        }

        public String getStateCode() {
            return get(AttributeKey.state.getKey());
        }

        public void setPostcode(String postcode) {
            set(AttributeKey.postcode.getKey(), postcode);
        }

        public String getPostcode() {
            return get(AttributeKey.postcode.getKey());
        }

        public void setCountryCode(String country) {
            set(AttributeKey.country.getKey(), country);
        }

        public String getCountryCode() {
            return get(AttributeKey.country.getKey());
        }

        public void setEmail(String email) {
            set(AttributeKey.email.getKey(), email);
        }

        public String getEmail() {
            return get(AttributeKey.email.getKey());
        }

        public void setPhone(String phone) {
            set(AttributeKey.phone.getKey(), phone);
        }

        public String getPhone() {
            return get(AttributeKey.phone.getKey());
        }

    }

    public static class MetaDataArray extends BecknObjects<MetaData> {
        public MetaDataArray() {}

        public MetaDataArray(JSONArray object) {
            super(object);
        }
    }

    public static class MetaData extends BecknObject {

    public MetaData() {}

        public MetaData(JSONObject object) {
            super(object);
        }

        public String getKey() {
            return get(AttributeKey.metaKey.getKey());
        }

        public void setKey( String key) {
            set(AttributeKey.metaKey.getKey(), key);
        }

        public String getValue() {
            return get(AttributeKey.metaValue.getKey());
        }

        public void setValue(String value) {
             set(AttributeKey.metaValue.getKey(), value);
        }
    }

    public static class LineItems extends BecknObjects<LineItem> {
        public LineItems() {
        }

        public LineItems(JSONArray object) {
            super(object);
        }

    }

    public static class LineItem extends BecknObject {

        public LineItem() {

        }

        public LineItem(JSONObject object) {
            super(object);
        }

        public void setQuantity(int quantity) {
            set(AttributeKey.quantity.getKey(), quantity);
        }

        public int getQuantity() {
            return getInteger(AttributeKey.quantity.getKey());
        }

        public void setProductId(String productId) {
            set(AttributeKey.productId.getKey(), productId);
        }

        public String getProductId() {
            return get(AttributeKey.productId.getKey());
        }

        public String getSubtotal() {
            return get(AttributeKey.subTotal.getKey());
        }

        public String getTotal() {
            return get(AttributeKey.total.getKey());
        }

        public String getName() {
            return get(AttributeKey.name.getKey());
        }

        public String getSku() {
            return get(AttributeKey.sku.getKey());
        }

    }

    public static class TaxLines extends BecknObjects<TaxLine> {

        public TaxLines(JSONArray object) {
            super(object);
        }
    }

    public static class TaxLine extends BecknObject {

        public TaxLine(JSONObject object) {
            super(object);
        }

    }

    public static class ShippingLines extends BecknObjects<ShippingLine> {
        public ShippingLines() {
        }

        public ShippingLines(JSONArray object) {
            super(object);
        }

    }

    public static class ShippingLine extends BecknObject {
        public ShippingLine() {
        }

        public ShippingLine(JSONObject object) {
            super(object);
        }

    }

    public static class FeeLines extends BecknObjectsWithId<FeeLine> {

        public FeeLines(JSONArray object) {
            super(object);
        }

    }

    public static class FeeLine extends WooCommerceObjectWithId {

        public FeeLine(JSONObject object) {
            super(object);
        }

    }

    public static class CouponLines extends BecknObjectsWithId<CouponLine> {

        public CouponLines(JSONArray object) {
            super(object);
        }

    }

    public static class CouponLine extends WooCommerceObjectWithId {

        public CouponLine(JSONObject object) {
            super(object);
        }

    }

    public static class Refunds extends BecknObjectsWithId<Refund> {

        public Refunds(JSONArray object) {
            super(object);
        }
    }

    public static class Refund extends WooCommerceObjectWithId {

        public Refund(JSONObject object) {
            super(object);
        }
    }

}
