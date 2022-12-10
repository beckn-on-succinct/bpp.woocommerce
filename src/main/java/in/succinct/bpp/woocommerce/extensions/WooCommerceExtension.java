package in.succinct.bpp.woocommerce.extensions;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.Catalog;
import in.succinct.beckn.Intent;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Message;
import in.succinct.beckn.Order;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.bpp.shell.extensions.BppActionExtension;
import in.succinct.bpp.woocommerce.db.model.BecknOrderMeta;
import in.succinct.bpp.woocommerce.helpers.WooCommerceHelper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.List;

public class WooCommerceExtension extends BppActionExtension {
    static {
        Registry.instance().registerExtension("in.succinct.bpp.shell.extension",new WooCommerceExtension());
    }
    private final WooCommerceHelper helper = new WooCommerceHelper();
    @Override
    public void search(Request request, Request reply) {
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
        Provider provider = helper.createProvider(providers,reply.getContext());
        provider.setLocations(new Locations());
        catalog.setDescriptor(provider.getDescriptor());
        Location location = helper.createLocation(provider.getLocations());

        Items items = helper.createItems(provider);

        JSONArray woo_products = helper.woo_post("products",search, new IgnoreCaseMap<String>(){{
            put("X-HTTP-Method-Override","GET");
        }});
        for (int i = 0 ; i < woo_products.size() ; i ++){
            Item item = helper.createItem(items,(JSONObject) woo_products.get(i));
            item.setLocationId(location.getId());
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
        params.put("status","processing");

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
        throw new RuntimeException("Service rating not yet implemented");
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
}
