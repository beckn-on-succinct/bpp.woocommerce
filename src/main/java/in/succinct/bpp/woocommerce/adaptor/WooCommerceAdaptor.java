package in.succinct.bpp.woocommerce.adaptor;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.BecknStrings;
import in.succinct.beckn.Catalog;
import in.succinct.beckn.Categories;
import in.succinct.beckn.Category;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentType;
import in.succinct.beckn.Fulfillments;
import in.succinct.beckn.Intent;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Message;
import in.succinct.beckn.Order;
import in.succinct.beckn.ondc.Payment;
import in.succinct.beckn.ondc.Payments;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.beckn.Tags;
import in.succinct.bpp.core.adaptor.CommerceAdaptor;
import in.succinct.bpp.core.registry.BecknRegistry;
import in.succinct.bpp.search.adaptor.SearchAdaptor;
import in.succinct.bpp.woocommerce.db.model.BecknOrderMeta;
import in.succinct.bpp.woocommerce.helpers.WooCommerceHelper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.Map;

public class WooCommerceAdaptor extends CommerceAdaptor {
    final SearchAdaptor searchAdaptor;
    final WooCommerceHelper helper ;
    final Map<String,String> configuration ;
    public WooCommerceAdaptor(Map<String,String> configuration, Subscriber subscriber, BecknRegistry registry){
        super(subscriber,registry);
        this.searchAdaptor = new SearchAdaptor(this);
        this.helper = new WooCommerceHelper(this);
        this.configuration = configuration;
    }

    public Map<String, String> getConfiguration() {
        return configuration;
    }

    public WooCommerceHelper getHelper() {
        return helper;
    }

    @Override
    public void search(Request request,Request reply){
        this.searchAdaptor.search(request,reply);
    }
    /* Don't remove Used from AppInstaller */
    /* Search is fulfilled from the plugin */
    public void _search(Request request, Request reply) {
        Intent intent = request.getMessage().getIntent();
        JSONObject search = new JSONObject();
        search.put("status","publish");
        search.put("stock_status","instock");
        if (intent.getDescriptor() != null){
            search.put("search",intent.getDescriptor().getName());
        }else if (intent.getItem() != null && intent.getItem().getDescriptor() != null){
            search.put("search",intent.getItem().getDescriptor().getName());
        }

        Message message = helper.createMessage(reply);
        Catalog catalog = helper.createCatalog(message);
        Providers providers = helper.createProviders(catalog);
        Provider provider = helper.createProvider(providers);
        provider.setLocations(new Locations());
        provider.setPayments(new Payments());
        provider.setCategories(new Categories());
        provider.setFulfillments(new Fulfillments());
        catalog.setDescriptor(provider.getDescriptor());
        Location location = helper.createLocation(provider.getLocations());

        Items items = helper.createItems(provider);

        JSONArray woo_products = helper.woo_post("products",search, new IgnoreCaseMap<String>(){{
            put("X-HTTP-Method-Override","GET");
        }});
        for (int i = 0 ; i < woo_products.size() ; i ++){
            JSONObject woo_product = (JSONObject) woo_products.get(i);
            Item item = helper.createItem(items,woo_product);
            item.setLocationId(location.getId());
            item.setLocationIds(new BecknStrings());
            item.getLocationIds().add(location.getId());
            if (provider.getLocations().get(location.getId()) == null) {
                provider.getLocations().add(location);
            }

            Fulfillment fulfillment =new Fulfillment();
            fulfillment.setId(FulfillmentType.store_pickup.toString());
            fulfillment.setType(FulfillmentType.store_pickup);
            item.setFulfillmentIds(new BecknStrings());
            item.getFulfillmentIds().add(fulfillment.getId());
            if (provider.getFulfillments().get(fulfillment.getId()) == null) {
                provider.getFulfillments().add(fulfillment);
            }
            item.setFulfillmentId(fulfillment.getId());

            JSONArray categories = (JSONArray) woo_product.get("categories");
            for (Object ocategory : categories){
                JSONObject wooCategory = (JSONObject) ocategory;
                Category category= new Category();
                category.setId(StringUtil.valueOf(wooCategory.get("id")));
                category.setDescriptor(new Descriptor());
                category.getDescriptor().setName((String)wooCategory.get("name"));
                category.getDescriptor().setCode((String)wooCategory.get("slug"));

                if (provider.getCategories().get(category.getId()) == null) {
                    provider.getCategories().add(category);
                }
                if (item.getCategoryIds() == null) {
                    item.setCategoryIds(new BecknStrings());
                }
                item.getCategoryIds().add(category.getId());
            }
            item.setPaymentIds(new BecknStrings());
            Payment payment = new Payment();
            payment.setType("POST-FULFILLMENT");
            payment.setId(payment.getType());
            item.setPaymentIds(new BecknStrings());
            item.getPaymentIds().add(payment.getId());
            if (provider.getPayments().get(payment.getId()) == null) {
                provider.getPayments().add(payment);
            }
            item.setTags(new Tags());
            JSONArray arr = (JSONArray) woo_product.get("meta_data");
            for (int j = 0 ; j < arr.size() ; j ++){
                JSONObject tag =  (JSONObject) arr.get(j);
                item.getTags().set((String)tag.get("key"),(String)tag.get("value"));
            }
        }
    }

    @Override
    public void select(Request request, Request response) {
        Message message = helper.createMessage(response);
        Order outOrder = helper.createOrder(message);
        Quote quote = helper.createQuote(outOrder);
        Items outItems = helper.createItems(outOrder,request.getMessage().getOrder().getItems());


    }

    @Override
    public void init(Request request, Request reply) {
        Order order = request.getMessage().getOrder();
        if (order == null){
            throw new RuntimeException("No Order passed");
        }
        JSONObject wooOrder = helper.makeWooOrder(order);
        wooOrder.put("transaction_id","beckn-"+request.getContext().getTransactionId());

        wooOrder.put("meta_data",new JSONArray());
        JSONArray metaArray = (JSONArray) wooOrder.get("meta_data");
        for (String key : new String[]{"bap_id","bap_uri","domain","transaction_id","city","country","core_version"}){
            JSONObject meta = new JSONObject();
            meta.put("key",String.format("context.%s",key));
            meta.put("value",request.getContext().get(key));
            metaArray.add(meta);
        }

        JSONObject outOrder = helper.woo_post("/orders", wooOrder);

        Message message = helper.createMessage(reply);
        message.setOrder(helper.getBecknOrder(outOrder));
        BecknOrderMeta becknOrderMeta = Database.getTable(BecknOrderMeta.class).newRecord();
        becknOrderMeta.setBecknTransactionId(request.getContext().getTransactionId());
        becknOrderMeta.setWooCommerceOrderId(StringUtil.valueOf(outOrder.get("id")));
        becknOrderMeta.save();

    }

    @Override
    public void confirm(Request request, Request reply) {
        Order order = request.getMessage().getOrder();
        if (order == null){
            throw new RuntimeException("No Order passed");
        }
        JSONObject wooOrder = helper.makeWooOrder(order);
        if (!wooOrder.containsKey("id")) {
            String txnId = request.getContext().getTransactionId();
            Select select = new Select().from(BecknOrderMeta.class);
            List<BecknOrderMeta> list = select.where(new Expression(select.getPool(),"BECKN_TRANSACTION_ID", Operator.EQ,txnId)).execute(1);
            if (!list.isEmpty()){
                wooOrder.put("id",list.get(0).getWooCommerceOrderId());
            }
        }
        JSONObject outOrder = null;
        if (wooOrder.containsKey("id")) {
            outOrder = helper.woo_get("/orders/" + wooOrder.get("id"), new JSONObject());
        }

        if (outOrder == null && outOrder.isEmpty()) {
            throw new RuntimeException("Order could not be found to confirm!");
        }
        if (!ObjectUtil.equals(outOrder.get("status"),"pending")){
            throw new RuntimeException("Order already confirmed!");
        }

        JSONObject params = new JSONObject();
        params.put("status","on_hold");

        outOrder = helper.woo_put("/orders/" + wooOrder.get("id"), params);
        if (outOrder != null && !outOrder.isEmpty()){
            Message message = helper.createMessage(reply);
            message.setOrder(helper.getBecknOrder(outOrder));
            return;
        }


        throw new RuntimeException("Insufficient information to confirm order!");
    }

    @Override
    public void track(Request request, Request reply) {

    }

    @Override
    public void cancel(Request request, Request reply) {
        Order order = request.getMessage().getOrder();
        if (order == null){
            throw new RuntimeException("No Order passed");
        }
        JSONObject wooOrder = helper.makeWooOrder(order);
        if (wooOrder.containsKey("id")) {
            JSONObject params = new JSONObject();
            params.put("status","cancelled");
            JSONObject outOrder = helper.woo_put("/orders/" + wooOrder.get("id"), params);
            if (outOrder != null && !outOrder.isEmpty()){
                Message message = helper.createMessage(reply);
                message.setOrder(helper.getBecknOrder(outOrder));
                return;
            }
        }
        throw new RuntimeException("Unable to locate order to cancel!");
    }

    @Override
    public void update(Request request, Request reply) {
        throw new RuntimeException("Orders cannot be updated. Please cancel and rebook your orders!");
    }

    @Override
    public void status(Request request, Request reply) {
        Order order = request.getMessage().getOrder();
        if (order == null){
            order = new Order();
            order.setId(request.getMessage().get("order_id"));
            request.getMessage().setOrder(order);
        }
        JSONObject wooOrder = helper.makeWooOrder(order);
        if (wooOrder.containsKey("id")) {
            JSONObject outOrder = helper.woo_get("/orders/" + wooOrder.get("id"),new JSONObject());
            if (outOrder != null && !outOrder.isEmpty()){
                Message message = helper.createMessage(reply);
                message.setOrder(helper.getBecknOrder(outOrder));
                return;
            }
        }
        throw new RuntimeException("Unable to locate order to cancel!");
    }

    @Override
    public void rating(Request request, Request reply) {

    }


    @Override
    public void support(Request request, Request reply) {
        Message message = helper.createMessage(reply);
        JSONObject admin = helper.woo_get("/customers/1",new JSONObject());
        message.setEmail((String)admin.get("email"));
    }

    @Override
    public void get_cancellation_reasons(Request request, Request reply) {

    }

    @Override
    public void get_return_reasons(Request request, Request reply) {

    }

    @Override
    public void get_rating_categories(Request request, Request reply) {

    }

    @Override
    public void get_feedback_categories(Request request, Request reply) {

    }

    @Override
    public void get_feedback_form(Request request, Request reply) {

    }


}
