package in.succinct.bpp.plugin.woocommerce.helpers;

import com.venky.cache.Cache;
import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Count;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.State;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Address;
import in.succinct.beckn.Billing;
import in.succinct.beckn.BreakUp;
import in.succinct.beckn.BreakUp.BreakUpElement;
import in.succinct.beckn.Catalog;
import in.succinct.beckn.Contact;
import in.succinct.beckn.Context;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Images;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Message;
import in.succinct.beckn.Order;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.Params;
import in.succinct.beckn.Person;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.QuantitySummary;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.beckn.User;
import in.succinct.bpp.plugin.woocommerce.helpers.BecknIdHelper.Entity;
import in.succinct.bpp.shell.util.BecknUtil;
import org.jose4j.base64url.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WooCommerceHelper {
    public String getConfigPrefix(){
        return "in.succinct.bpp.plugin.woocommerce";
    }


    public String getStoreUrl(){
        return Config.instance().getProperty(String.format("%s.storeUrl",getConfigPrefix()));
    }
    public String getClientId(){
        return Config.instance().getProperty(String.format("%s.clientId",getConfigPrefix()));
    }
    public String getSecret(){
        return Config.instance().getProperty(String.format("%s.secret",getConfigPrefix()));
    }

    public <T extends JSONAware> T woo_post(String relativeUrl, JSONObject parameter ){
        return woo_post(relativeUrl,parameter,new IgnoreCaseMap<>());
    }
    public <T extends JSONAware> T woo_put(String relativeUrl, JSONObject parameter ){
        return woo_post(relativeUrl,parameter,new IgnoreCaseMap<String>(){{
            put("X-HTTP-Method-Override","PUT");
        }});
    }

    public <T extends JSONAware> T woo_post(String relativeUrl, JSONObject parameter , Map<String,String> addnlHeaders){
        return new Call<JSONObject>().url(getStoreUrl(),relativeUrl).header("content-type", MimeType.APPLICATION_JSON.toString()).header("Authorization",getAuth()).headers(addnlHeaders)
                .inputFormat(InputFormat.JSON).input(parameter).method(HttpMethod.POST).getResponseAsJson();
    }
    public <T extends JSONAware> T woo_get(String relativeUrl, JSONObject parameter){
        return woo_get(relativeUrl,parameter,new IgnoreCaseMap<>());
    }
    public <T extends JSONAware> T woo_get(String relativeUrl, JSONObject parameter, Map<String,String> addnlHeaders){
        return new Call<JSONObject>().url(getStoreUrl(),relativeUrl).header("content-type", MimeType.APPLICATION_JSON.toString()).header("Authorization",getAuth()).headers(addnlHeaders)
                .input(parameter)
                .method(HttpMethod.GET).getResponseAsJson();
    }

    public String getAuth(){
        return String.format("Basic %s" ,
                Base64.encode(String.format("%s:%s",getClientId() , getSecret()).getBytes(StandardCharsets.UTF_8)));
    }


    public Message createMessage(Request request){
        Message message = new Message();
        request.setMessage(message);
        return message;
    }

    public Catalog createCatalog(Message message) {
        Catalog catalog = new Catalog();
        message.setCatalog(catalog);
        return catalog;
    }

    public Providers createProviders(Catalog catalog) {
        Providers providers = new Providers();
        catalog.setProviders(providers);
        return providers;
    }

    public Provider createProvider(Providers providers, Context context) {
        Provider provider = new Provider();
        provider.setDescriptor(new Descriptor());
        provider.getDescriptor().setName(BecknUtil.getSubscriberId());
        provider.setId(BecknIdHelper.getBecknId(BecknUtil.getSubscriberId(), Entity.provider));
        providers.add(provider);
        return provider;
    }

    public Order createOrder(Message message){
        Order order = new Order();
        message.setOrder(order);
        return order;
    }

    public Quote createQuote(Order order){
        Quote quote = new Quote();
        order.setQuote(quote);
        return quote;
    }


    public Items createItems(Provider provider) {
        Items items = new Items();
        provider.setItems(items);
        return items;
    }

    public Items createItems(Order order, Items inItems) {
        Items items = new Items();
        order.setItems(items);

        Quote quote = order.getQuote();
        Price orderPrice = new Price();
        BreakUp breakUp = new BreakUp();
        quote.setPrice(orderPrice);
        quote.setBreakUp(breakUp);

        BreakUpElement product_total = breakUp.createElement("item","Product Total", new Price());
        BreakUpElement product_tax_total = breakUp.createElement("item","Product Tax", new Price());
        BreakUpElement shipping_total = breakUp.createElement("fulfillment","Shipping Total", new Price());
        BreakUpElement shipping_tax_total = breakUp.createElement("fulfillment","Shipping Tax", new Price());
        breakUp.add(product_total);
        breakUp.add(product_tax_total);
        breakUp.add(shipping_total);
        breakUp.add(shipping_tax_total);

        Map<String,Double> taxRateMap = new Cache<String, Double>() {
            @Override
            protected Double getValue(String taxClass) {
                if (!ObjectUtil.isVoid(taxClass)){
                    JSONObject search_params = new JSONObject();
                    search_params.put("class",taxClass);
                    JSONArray taxRates = woo_get("/taxes",search_params);
                    if (taxRates.size() > 0 ){
                        JSONObject taxRate = (JSONObject) taxRates.get(0);
                        return getDoubleTypeConverter().valueOf(taxRate.get("rate"));
                    }
                }
                return 0.0D;
            }
        };



        for (int i = 0 ; i < inItems.size() ; i ++ ){
            Item inItem = inItems.get(i);
            Item outItem = new Item();
            outItem.setId(inItem.getId());

            String localId = BecknIdHelper.getLocalUniqueId(inItem.getId(), Entity.item);

            Quantity quantity = inItem.get(Quantity.class,"quantity");


            QuantitySummary outQuantity = new QuantitySummary();
            outItem.set("quantity",outQuantity);
            outQuantity.setSelected(quantity);

            JSONObject inventory = woo_get("/products/"+localId,new JSONObject());

            if (inventory == null ){
                throw new RuntimeException("No inventory with provider.");
            }

            String taxClass = (String)inventory.get("tax_class");
            double taxRate = taxRateMap.get(taxClass);



            double configured_price  = Double.parseDouble((String)inventory.get("price")) ;
            double tax = isTaxIncludedInPrice() ? configured_price / (1 + taxRate/100.0) * taxRate : configured_price * taxRate;
            double current_price = isTaxIncludedInPrice() ? configured_price - tax : configured_price;
            double regular_price = Double.parseDouble((String)inventory.get("regular_price"));


            Price price = new Price();
            outItem.setPrice(price);
            price.setCurrency("INR");
            price.setListedValue(regular_price * quantity.getCount());
            price.setOfferedValue(current_price * quantity.getCount());
            price.setValue(current_price * quantity.getCount());

            Price p = product_total.get(Price.class,"price");
            p.setCurrency("INR");
            p.setListedValue(p.getListedValue() + price.getListedValue());
            p.setOfferedValue(p.getOfferedValue() + price.getOfferedValue());
            p.setValue(p.getValue() + price.getValue());

            Price t = product_tax_total.get(Price.class,"price");
            t.setCurrency("INR");
            t.setValue(t.getValue() + tax * quantity.getCount());


            items.add(outItem);
        }


        orderPrice.setListedValue(product_total.get(Price.class,"price").getListedValue() );
        orderPrice.setOfferedValue(product_total.get(Price.class,"price").getOfferedValue() );
        orderPrice.setValue(product_total.get(Price.class,"price").getValue()  + product_tax_total.get(Price.class,"price").getValue() );
        orderPrice.setCurrency("INR");
        quote.setTtl(15L*60L); //15 minutes.

        return items;
    }

    public void createItems(Order order, JSONArray line_items) {
        Items items = new Items();
        order.setItems(items);
        for (int i = 0 ;i < line_items.size() ; i ++ ){
            JSONObject lineItem = (JSONObject) line_items.get(i);
            createItemFromWooLineItem(items,lineItem);
        }

    }


    Cache<String,Map<String,JSONObject>> settingGroup = new Cache<String, Map<String, JSONObject>>() {
        @Override
        protected Map<String, JSONObject> getValue(String group) {
            Map<String,JSONObject> settings =  new HashMap<>();
            JSONArray array = woo_get(String.format("/settings/%s/",group), new JSONObject());
            for (int i = 0; i < array.size(); i++) {
                JSONObject o = (JSONObject) array.get(i);
                settings.put((String)o.get("id"), o);
            }
            return settings;
        }
    };

    private String getSettings(String  group, String id){
        return String.valueOf(settingGroup.get(group).get(id).get("value"));
    }

    private String getSettingsDescription(String group, String id ,String value){
        return (String)((JSONObject)(settingGroup.get(group).get(id).get("options"))).get(value);
    }
    public boolean isTaxIncludedInPrice(){
        return booleanTypeConverter.valueOf(getSettings("tax","woocommerce_prices_include_tax"));
    }


    public TypeConverter<Double> getDoubleTypeConverter() {
        return doubleTypeConverter;
    }

    private TypeConverter<Double> doubleTypeConverter = Database.getJdbcTypeHelper("").getTypeRef(double.class).getTypeConverter();
    private TypeConverter<Boolean> booleanTypeConverter  = Database.getJdbcTypeHelper("").getTypeRef(boolean.class).getTypeConverter();




    public Item createItem(Items items, JSONObject product){
        Item item  = new Item();
        item.setId(BecknIdHelper.getBecknId(String.valueOf(product.get("id")),Entity.item));
        items.add(item);
        item.setDescriptor(new Descriptor());
        Descriptor descriptor = item.getDescriptor();
        descriptor.setName((String)product.get("name"));
        descriptor.setCode((String)product.get("sku"));


        Price price = new Price();
        item.setPrice(price);
        price.setCurrency("INR");
        price.setValue(Double.parseDouble((String)product.get("price")));
        price.setListedValue(Double.parseDouble((String)product.get("regular_price")));

        descriptor.setImages(new Images());
        JSONArray images = (JSONArray) product.get("images");
        for (int i = 0 ; i< images.size() ;i ++){
            descriptor.getImages().add((String) ((JSONObject)images.get(i)).get("src"));
        }
        return item;

    }

    private void createItemFromWooLineItem(Items items, JSONObject wooLineItem) {
        Item item = new Item();
        item.setDescriptor(new Descriptor());
        item.setId(BecknIdHelper.getBecknId(String.valueOf(wooLineItem.get("product_id")),Entity.item));
        item.getDescriptor().setName((String)wooLineItem.get("name"));
        item.setQuantity(new Quantity());
        item.getQuantity().setCount(doubleTypeConverter.valueOf(wooLineItem.get("quantity")).intValue());

        Price price = new Price();
        item.setPrice(price);

        price.setListedValue(doubleTypeConverter.valueOf(wooLineItem.get("subtotal")));
        price.setValue(doubleTypeConverter.valueOf(wooLineItem.get("total")));
        price.setCurrency("INR");
        items.add(item);


    }



    public JSONObject makeWooOrder(Order bo) {

        JSONObject order = new JSONObject();
        JSONObject billing = new JSONObject();
        JSONObject shipping = new JSONObject();

        if (!ObjectUtil.isVoid(bo.getId())){
            order.put("id",BecknIdHelper.getLocalUniqueId(bo.getId(),Entity.order));
        }else {
            order.put("set_paid",false);
        }
        setWooBilling(billing,bo.getBilling());
        if (!billing.isEmpty()){
            order.put("billing",billing);
        }
        setWooShipping(shipping,bo.getFulfillment());
        if (!shipping.isEmpty()){
            order.put("shipping",shipping);
        }

        if (bo.getItems() != null ){
            JSONArray line_items = new JSONArray();
            order.put("line_items",line_items);
            bo.getItems().forEach(boItem->{
                JSONObject item = new JSONObject();
                item.put("product_id",BecknIdHelper.getLocalUniqueId(boItem.getId(),Entity.item));
                item.put("quantity",boItem.getQuantity().getCount());
                line_items.add(item);
            });
        }


        return order;
    }

    private void setWooShipping(JSONObject shipping , Fulfillment fulfillment){
        if (fulfillment == null){
            return;
        }
        User user = fulfillment.getCustomer();

        String[] parts = user.getPerson().getName().split(" ");
        shipping.put("first_name",parts[0]);
        shipping.put("last_name", user.getPerson().getName().substring(parts[0].length()));

        Address address = fulfillment.getEnd().getLocation().getAddress();
        Contact contact = fulfillment.getEnd().getContact();
        City city = City.findByCode(address.getCity());
        shipping.put("address_1",address.getDoor()+"," +address.getBuilding());
        shipping.put("address_2",address.getStreet()+","+address.getLocality());
        shipping.put("city", city.getName());
        shipping.put("state",city.getState().getCode());
        shipping.put("postcode",address.getAreaCode());
        shipping.put("country",city.getState().getCountry().getIsoCode2());
        shipping.put("email",contact.getEmail());
        shipping.put("phone",contact.getPhone());

    }
    private void setWooBilling(JSONObject billing, Billing boBilling) {
        if (boBilling == null){
            return;
        }
        String[] parts = boBilling.getName().split(" ");
        billing.put("first_name",parts[0]);
        billing.put("last_name", boBilling.getName().substring(parts[0].length()));
        billing.put("address_1",boBilling.getAddress().getDoor()+"," +boBilling.getAddress().getBuilding());
        billing.put("address_2",boBilling.getAddress().getStreet()+","+boBilling.getAddress().getLocality());
        City city = City.findByCode(boBilling.getAddress().getCity());

        billing.put("city", city.getName());
        billing.put("state",city.getState().getCode());
        billing.put("postcode",boBilling.getAddress().getAreaCode());
        billing.put("country",city.getState().getCountry().getIsoCode2());
        billing.put("email",boBilling.getEmail());
        billing.put("phone",boBilling.getPhone());

    }
    public Order getBecknOrder(JSONObject wooOrder) {
        Order order = new Order();
        order.setPayment(new Payment());
        order.setId(BecknIdHelper.getBecknId(String.valueOf(wooOrder.get("id")),Entity.order));
        setPayment(order.getPayment(),wooOrder);
        setBoBilling(order,wooOrder);
        order.setState((String) wooOrder.get("status"));
        createItems(order,(JSONArray)wooOrder.get("line_items"));

        order.setFulfillment(new Fulfillment());
        order.getFulfillment().setEnd(new FulfillmentStop());
        order.getFulfillment().getEnd().setLocation(new Location());
        order.getFulfillment().getEnd().getLocation().setAddress(new Address());
        order.getFulfillment().getEnd().setContact(new Contact());
        order.getFulfillment().setCustomer(new User());


        JSONObject shipping = (JSONObject) wooOrder.get("shipping");
        if (ObjectUtil.isVoid(shipping.get("address_1"))){
            shipping = (JSONObject) wooOrder.get("billing");
        }
        String[] address1_parts = ((String)shipping.get("address_1")).split(",");
        String[] address2_parts = ((String)shipping.get("address_2")).split(",");


        User user = order.getFulfillment().getCustomer();
        user.setPerson(new Person());
        Person person = user.getPerson();
        person.setName(shipping.get("first_name") + " " + shipping.get("last_name"));


        Address address = order.getFulfillment().getEnd().getLocation().getAddress();
        address.setDoor(address1_parts[0]);
        if (address1_parts.length > 1) {
            address.setBuilding(address1_parts[1]);
        }
        address.setStreet(address2_parts[0]);
        if (address2_parts.length > 1) {
            address.setLocality(address2_parts[1]);
        }
        Country country = Country.findByISO((String) shipping.get("country"));
        address.setCountry(country.getIsoCode());
        address.setState((String)shipping.get("state"));
        State state = State.findByCountryAndCode(country.getId(),address.getState());

        address.setPinCode((String)shipping.get("postcode"));
        address.setCity(City.findByStateAndName(state.getId(),(String) shipping.get("city")).getCode());

        order.getFulfillment().getEnd().getContact().setPhone((String)shipping.get("phone"));
        order.getFulfillment().getEnd().getContact().setEmail((String)shipping.get("email"));


        order.setProvider(new Provider());
        order.getProvider().setId(BecknIdHelper.getBecknId(BecknUtil.getSubscriberId(), Entity.provider));
        order.setProviderLocation(new Location());
        order.getProviderLocation().setId(BecknIdHelper.getBecknId(BecknUtil.getSubscriberId(), Entity.provider_location));



        return order;
    }
    private void setPayment(Payment payment, JSONObject wooOrder) {

        if (!booleanTypeConverter.valueOf(wooOrder.get("set_paid"))) {
            payment.setStatus("NOT-PAID");
        }else {
            payment.setStatus("PAID");
        }
        payment.setParams(new Params());
        payment.getParams().setCurrency(getSettings("general","woocommerce_currency"));
        payment.getParams().setAmount(doubleTypeConverter.valueOf(wooOrder.get("total")));
    }

    private void setBoBilling(Order order, JSONObject wooOrder) {
        Billing billing = new Billing();
        order.setBilling(billing);
        JSONObject wooBilling = (JSONObject) wooOrder.get("billing");
        billing.setName(wooBilling.get("first_name") + " " + wooBilling.get("last_name"));
        billing.setPhone((String) wooBilling.get("phone"));
        billing.setEmail((String) wooBilling.get("email"));
        Address address = new Address();
        billing.setAddress(address);
        address.setName(billing.getName());
        address.setStreet((String)wooBilling.get("address_1"));
        address.setLocality((String)wooBilling.get("address_2"));
        address.setPinCode((String)wooBilling.get("postcode"));

        Country country= Country.findByISO((String)wooBilling.get("country"));
        address.setCountry(country.getIsoCode());

        address.setState((String) wooBilling.get("state"));
        State state = State.findByCountryAndCode(country.getId(),address.getState());

        address.setCity(City.findByStateAndName(state.getId(),(String)wooBilling.get("city")).getCode());

    }

    public Location createLocation(Locations locations) {
        Location location = new Location();
        Address address = new Address();
        location.setAddress(address);
        address.setName(getSettings("general","woocommerce_store_address"));


        String dc = getSettings("general","woocommerce_default_country");
        String[] cs = dc.split(":");
        String  cityName = getSettings("general","woocommerce_store_city");
        if (cs.length == 2){
            if (!ObjectUtil.equals(cs[0],BecknUtil.getCountry(2))){
                throw new RuntimeException("Cannot take orders for country code of " + BecknUtil.getCountry(2));
            }
            String[] descriptions = getSettingsDescription("general","woocommerce_default_country",dc).split(" - ");

            Country country = Database.getTable(Country.class).newRecord();
            country.setIsoCode(BecknUtil.getCountry(3));
            country.setIsoCode2(BecknUtil.getCountry(2));
            country.setName(descriptions[0]);
            country = Database.getTable(Country.class).getRefreshed(country);
            country.save();
            address.setCountry(country.getIsoCode());

            State state = Database.getTable(State.class).newRecord();
            state.setCode(cs[1]);
            state.setCountryId(country.getId());
            state.setName(descriptions[1]);
            state = Database.getTable(State.class).getRefreshed(state);
            state.save();
            address.setState(state.getCode());



            City city = Database.getTable(City.class).newRecord();
            city.setCode(Config.instance().getProperty("in.succinct.bpp.plugin.woocommerce.city"));
            city.setStateId(state.getId());
            city.setName(cityName);

            city = Database.getTable(City.class).getRefreshed(city);
            city.save();

            address.setCity(city.getCode());
        }
        address.setPinCode(getSettings("general","woocommerce_store_postcode"));
        address.setStreet(getSettings("general","woocommerce_store_address"));
        location.setId(BecknIdHelper.getBecknId(BecknUtil.getSubscriberId(), Entity.provider_location));
        locations.add(location);
        return location;
    }
}
