package in.succinct.bpp.woocommerce.adaptor;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Document;
import in.succinct.beckn.Documents;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Order;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Request;
import in.succinct.beckn.Tags;
import in.succinct.bpp.core.adaptor.CommerceAdaptor;
import in.succinct.bpp.core.adaptor.TimeSensitiveCache;
import in.succinct.bpp.woocommerce.helpers.WooCommerceHelper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

public class WooCommerceAdaptor extends CommerceAdaptor {
    final WooCommerceHelper helper;
    final TimeSensitiveCache cache = new TimeSensitiveCache(Duration.ofSeconds(60));

    public WooCommerceAdaptor(Map<String, String> configuration, Subscriber subscriber) {
        super(configuration, subscriber);
        this.helper = new WooCommerceHelper(this);
        getProviderConfig().getSupportContact().setEmail(getSupportEmail());
        getProviderConfig().getLocation().getAddress().setName(getProviderDescription());
    }

    public WooCommerceHelper getHelper() {
        return helper;
    }

    @Override
    public Locations getProviderLocations() {
        return cache.get(Locations.class, () -> {
            Locations locations = new Locations();
            locations.add(helper.getLocation());
            return locations;
        });
    }

    @Override
    public Items getItems() {
        return cache.get(Items.class, () -> {
            JSONObject search = new JSONObject();
            search.put("status", "publish");
            search.put("stock_status", "instock");
            JSONArray woo_products = helper.woo_post("products", search, new IgnoreCaseMap<String>() {{
                put("X-HTTP-Method-Override", "GET");
            }});
            Items items = new Items();
            for (Object wooProduct : woo_products) {
                JSONObject woo_product = (JSONObject) wooProduct;
                Item item = helper.createItem(items, woo_product);
                item.setLocationId(getProviderLocations().get(0).getId());
                item.setFulfillmentId(getHomeDelivery().getId());
                JSONArray categories = (JSONArray) woo_product.get("categories");
                for (Object ocategory : categories) {
                    JSONObject wooCategory = (JSONObject) ocategory;
                    item.setTags(new Tags());
                    item.getTags().set((String) wooCategory.get("name"), true);
                }

                item.setCategoryId(getProviderConfig().getCategory().getId());
                item.setTags(new Tags());
                JSONArray arr = (JSONArray) woo_product.get("meta_data");
                for (Object o : arr) {
                    JSONObject tag = (JSONObject) o;
                    item.getTags().set((String) tag.get("key"), (String) tag.get("value"));
                }
                items.add(item);
            }
            return items;

        });

    }

    @Override
    public boolean isTaxIncludedInPrice() {
        return helper.isTaxIncludedInPrice();
    }

    @Override
    public in.succinct.beckn.Order initializeDraftOrder(Request request) {
        Order order = request.getMessage().getOrder();

        JSONObject wooOrder = helper.makeWooOrder(order);
        wooOrder.put("transaction_id", "beckn-" + request.getContext().getTransactionId());

        wooOrder.put("meta_data", new JSONArray());
        JSONArray metaArray = (JSONArray) wooOrder.get("meta_data");
        for (String key : new String[]{"bap_id", "bap_uri", "domain", "transaction_id", "city", "country", "core_version"}) {
            JSONObject meta = new JSONObject();
            meta.put("key", String.format("context.%s", key));
            meta.put("value", request.getContext().get(key));
            metaArray.add(meta);
        }

        // FIXME If the Woocommerce call fails, ensure that response is sent back with correct errors to BAP
        JSONObject outOrder = helper.woo_post("/orders", wooOrder);
        return helper.getBecknOrder(outOrder);
    }

    @Override
    public in.succinct.beckn.Order confirmDraftOrder(in.succinct.beckn.Order draftOrder) {
        JSONObject outOrder = null;
        JSONObject wooOrder = helper.makeWooOrder(draftOrder);
        if (wooOrder.containsKey("id")) {
            outOrder = helper.woo_get("/orders/" + wooOrder.get("id"), new JSONObject());
        }
        if (outOrder == null || outOrder.isEmpty()) {
            throw new RuntimeException("Order could not be found to confirm!");
        }

        /* FIXME temporary commented out
        if (!ObjectUtil.equals(outOrder.get("status"),"pending")){
            //throw new RuntimeException("Order already confirmed!");
        }
         */

        JSONObject params = new JSONObject();
        params.put("status", "pending");

        outOrder = helper.woo_put("/orders/" + wooOrder.get("id"), params);
        Order oOrder = helper.getBecknOrder(outOrder);
        oOrder.getProvider().setRateable(true);


        //Set fulfillment.start
        Locations locations = new Locations();
        locations.add(helper.getLocation());

        if (locations.size() > 0) {
            oOrder.getFulfillments().get(0).setStart(new FulfillmentStop());
            oOrder.getFulfillments().get(0).getStart().setLocation(locations.get(0));
        }
        oOrder.getFulfillments().get(0).getStart().setInstructions(new Descriptor());
        oOrder.getFulfillments().get(0).getStart().getInstructions().setName("Status for Pickup");
        oOrder.getFulfillments().get(0).getStart().getInstructions().setName("Pickup confirmation code");

        oOrder.getPayment().setTlMethod("http/get");
        oOrder.getPayment().setUri("https://ondc.transaction.com/payment");
        oOrder.getPayment().setParams(new Payment.Params());
        oOrder.getPayment().getParams().setAmount(oOrder.getQuote().getPrice().getValue());
        oOrder.getPayment().getParams().setCurrency("INR");
        // FIXME Set tx_id correctly
        oOrder.getPayment().getParams().setTransactionId("transaction_1");
        oOrder.getPayment().setStatus(PaymentStatus.PAID);
        oOrder.setDocuments(new Documents());
        oOrder.getDocuments().add(new Document());
        // FIXME Need Invoice url from WooCommerce
        oOrder.getDocuments().get(0).setUrl("https://invoice_url");
        oOrder.getDocuments().get(0).setLabel("Invoice");
        oOrder.setState("Accepted");

        Date now = new Date(System.currentTimeMillis());
        oOrder.setCreatedAt(now);
        oOrder.setUpdatedAt(now);
        return oOrder;
    }

    @Override
    public in.succinct.beckn.Order getStatus(in.succinct.beckn.Order order) {
        JSONObject wooOrder = helper.makeWooOrder(order);
        if (wooOrder.containsKey("id")) {
            JSONObject outOrder = helper.woo_get("/orders/" + wooOrder.get("id"), new JSONObject());
            if (outOrder != null && !outOrder.isEmpty()) {
                return helper.getBecknOrder(outOrder);
            }
        }
        throw new RuntimeException("Unable to locate order to return status!");
    }

    @Override
    public in.succinct.beckn.Order cancel(in.succinct.beckn.Order order) {
        JSONObject wooOrder = helper.makeWooOrder(order);
        if (wooOrder.containsKey("id")) {
            JSONObject params = new JSONObject();
            params.put("status", "cancelled");
            JSONObject outOrder = helper.woo_put("/orders/" + wooOrder.get("id"), params);
            if (outOrder != null && !outOrder.isEmpty()) {
                return helper.getBecknOrder(outOrder);
            }
        }
        throw new RuntimeException("Unable to locate order to cancel!");
    }

    @Override
    public String getTrackingUrl(in.succinct.beckn.Order order) {
        return null;
    }

    private String getSupportEmail() {
        JSONObject admin = helper.woo_get("/customers/1", new JSONObject());
        return (String) admin.get("email");
    }

    private String getProviderDescription() {
        return helper.getSettings("general","woocommerce_store_address");
    }

}
