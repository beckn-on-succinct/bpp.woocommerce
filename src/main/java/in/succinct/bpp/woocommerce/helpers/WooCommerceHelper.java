package in.succinct.bpp.woocommerce.helpers;

import com.venky.cache.Cache;
import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.State;
import com.venky.swf.routing.Config;
import in.succinct.beckn.*;
import in.succinct.beckn.Fulfillments;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Person;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Tags;
import in.succinct.beckn.Time.Range;

import in.succinct.beckn.ondc.retail.BreakupElement;
import in.succinct.beckn.ondc.retail.*;
import in.succinct.beckn.ondc.retail.Catalog;
import in.succinct.beckn.ondc.retail.Circle;
import in.succinct.beckn.ondc.retail.Contact;
import in.succinct.beckn.ondc.retail.Descriptor;
import in.succinct.beckn.ondc.retail.Fulfillment;
import in.succinct.beckn.ondc.retail.FulfillmentStop;
import in.succinct.beckn.ondc.retail.Item;
import in.succinct.beckn.ondc.retail.Location;
import in.succinct.beckn.ondc.retail.Order;
import in.succinct.beckn.ondc.retail.Payment;
import in.succinct.beckn.ondc.retail.Payment.Params;
import in.succinct.beckn.ondc.retail.Provider;
import in.succinct.beckn.ondc.retail.Time;
import in.succinct.bpp.woocommerce.adaptor.WooCommerceAdaptor;
import in.succinct.bpp.woocommerce.helpers.BecknIdHelper.Entity;
import org.jose4j.base64url.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class WooCommerceHelper {
    final WooCommerceAdaptor adaptor;
    public WooCommerceHelper(WooCommerceAdaptor adaptor){
        this.adaptor = adaptor;
    }
    public WooCommerceHelper(){
        this(null);
    }
    public String getConfigPrefix(){
        return "in.succinct.bpp.woocommerce";
    }


    public String getStoreUrl(){
        return adaptor.getConfiguration().get(String.format("%s.storeUrl",getConfigPrefix()));
    }
    public String getClientId(){
        return adaptor.getConfiguration().get(String.format("%s.clientId",getConfigPrefix()));
    }
    public String getSecret(){
        return adaptor.getConfiguration().get(String.format("%s.secret",getConfigPrefix()));
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
        // FIXME Need to handle error conditions like order already exists
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

    public Provider createProvider(Providers providers) {
        Provider provider = new Provider();
        provider.setDescriptor(new Descriptor());
        provider.getDescriptor().setName(adaptor.getSubscriber().getSubscriberId());
        provider.getDescriptor().setShortDesc(provider.getDescriptor().getName());
        provider.getDescriptor().setLongDesc(getSettings("general","woocommerce_store_address"));
        provider.setId(BecknIdHelper.getBecknId(adaptor.getSubscriber().getSubscriberId(), adaptor.getSubscriber().getSubscriberId(), Entity.provider));
        provider.setTtl(120);

        // TODO: Set values of images and symbol correctly below
        provider.getDescriptor().setSymbol(adaptor.getSubscriber().getSubscriberId());
        provider.getDescriptor().setImages(new Images());
        provider.setFssaiLicenceNo("not-available");

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

        //BreakUpElement product_total = breakUp.createElement("item","Product Total", new Price());
        BreakupElement shipping_total = breakUp.createElement("fulfillment","Delivery Charges", new Price()).cast(BreakupElement.class);
        // Grocery does not expect Taxes to be shown, they must be shown only for F&B
        //BreakUpElement product_tax_total = breakUp.createElement("item","Tax", new Price());
        //BreakUpElement shipping_tax_total = breakUp.createElement("fulfillment","Shipping Tax", new Price());

        shipping_total.setTitleType("delivery");
        // FIXME dont rely only on 0th element
        shipping_total.setItemId(order.getFulfillments().get(0).getId());
        Price stp = shipping_total.get(Price.class,"price");
        stp.setCurrency("INR");
        //p.setListedValue(p.getListedValue() + price.getListedValue());
        //p.setOfferedValue(p.getOfferedValue() + price.getOfferedValue());
        stp.setValue(30);
        breakUp.add(shipping_total);
        //breakUp.add(product_tax_total);
        //breakUp.add(shipping_tax_total);

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

        orderPrice.setValue(shipping_total.get(Price.class,"price").getValue() );

        for (int i = 0 ; i < inItems.size() ; i ++ ){
            Item inItem = inItems.get(i).cast(Item.class);
            Item outItem = new Item();
            Item outBreakupItem = new Item();

            String localId = BecknIdHelper.getLocalUniqueId(inItem.getId(), Entity.item);

            // FIXME Use Product title instead of Product ID
            BreakupElement breakup_product = breakUp.createElement("item", inItem.getId(), new Price()).cast(BreakupElement.class);

            Quantity quantity = inItem.get(Quantity.class,"quantity");
            breakup_product.setItemQuantity(quantity);
            breakup_product.setTitleType("item");
            breakup_product.setItemId(inItem.getId());
            breakup_product.setItem(outBreakupItem);

            ItemQuantity iQuantity = new ItemQuantity();
            iQuantity.setAvailable(quantity);
            iQuantity.setMaximum(quantity);
            // FIXME Should be taken from item cache instead
            outBreakupItem.setItemQuantity(iQuantity);

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
            //outItem.setPrice(price);
            price.setCurrency("INR");
            //price.setListedValue(regular_price * quantity.getCount());
            //price.setOfferedValue(current_price * quantity.getCount());
            price.setValue(current_price * quantity.getCount());

            Price p = breakup_product.get(Price.class,"price");
            p.setCurrency("INR");
            //p.setListedValue(p.getListedValue() + price.getListedValue());
            //p.setOfferedValue(p.getOfferedValue() + price.getOfferedValue());
            p.setValue(p.getValue() + price.getValue());
            // FIXME Should be per quantity price instead
            outBreakupItem.setPrice(breakup_product.getItem().getPrice());

            //Price t = product_tax_total.get(Price.class,"price");
            //t.setCurrency("INR");
            //t.setValue(t.getValue() + tax * quantity.getCount());

            outItem.setId(inItem.getId());
            outItem.setFulfillmentId(order.getFulfillments().get(0).getId());
            items.add(outItem);
            breakUp.add(breakup_product);
            orderPrice.setValue(orderPrice.getValue() + breakup_product.get(Price.class,"price").getValue() );
        }

        //orderPrice.setListedValue(product_total.get(Price.class,"price").getListedValue() );
        //orderPrice.setOfferedValue(product_total.get(Price.class,"price").getOfferedValue() );
        //orderPrice.setValue(breakup_product.get(Price.class,"price").getValue()  + product_tax_total.get(Price.class,"price").getValue() );
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

            Item outItem = items.get(i).cast(Item.class);
            // FIXME Assign value from order object.
            outItem.setFulfillmentId("Fulfillment1");

            BreakUp breakUp = order.getQuote().getBreakUp();
            BreakupElement breakup_product = breakUp.createElement("item", outItem.getId(), new Price()).cast(BreakupElement.class);

            Quantity quantity = outItem.get(Quantity.class,"quantity");
            breakup_product.setItemQuantity(quantity);
            breakup_product.setTitleType("item");
            breakup_product.setItemId(outItem.getId());
            //breakup_product.setItem(outBreakupItem);

            Price p = breakup_product.get(Price.class,"price");
            p.setCurrency("INR");
            p.setValue(p.getValue() + doubleTypeConverter.valueOf(lineItem.get("total")));

            ItemQuantity iQuantity = new ItemQuantity();
            iQuantity.setAvailable(quantity);
            iQuantity.setMaximum(quantity);

            breakUp.add(breakup_product);
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
        item.setId(BecknIdHelper.getBecknId(String.valueOf(product.get("id")),adaptor.getSubscriber().getSubscriberId(),Entity.item));
        items.add(item);

        item.setDescriptor(new Descriptor());
        Descriptor descriptor = (Descriptor) item.getDescriptor();
        descriptor.setName((String)product.get("name"));
        //descriptor.setCode((String)product.get("sku"));
        descriptor.setShortDesc((String)product.get("short_description"));
        descriptor.setLongDesc((String)product.get("description"));
        descriptor.setSymbol("https://abc.com/images/18275/18275_ONDC_1650967420207.png");
        descriptor.setImages(new Images());
        JSONArray images = (JSONArray) product.get("images");
        // FIXME: Restrict size to 5 images per ONDC
        for (int i = 0 ; i< images.size() ;i ++){
            descriptor.getImages().add((String) ((JSONObject)images.get(i)).get("src"));
        }

        item.setItemQuantity(new ItemQuantity());
        Quantity iQuantity = new Quantity();
        iQuantity.setCount(10);
        item.getItemQuantity().setAvailable(iQuantity);
        item.getItemQuantity().setMaximum(iQuantity);

        Price price = new Price();
        item.setPrice(price);
        price.setCurrency("INR");
        price.setValue(Double.parseDouble((String)product.get("price")));
        // Not required for ONDC
        // price.setListedValue(Double.parseDouble((String)product.get("regular_price")));
        price.setMaximumValue(price.getListedValue());

        item.setReturnable(false);
        item.setCancellable(false);
        item.setReturnWindow("P7D");
        item.setTimeToShip("PT45M");
        item.setAvailableOnCod(false);
        item.setContactDetailsConsumerCare("Address");

        // FIXME: Implement additional fields based on Top level category
        // One approach is to map WooCommerce category to corresponding ONDC Category in this adaptor

        return item;

    }

    private void createItemFromWooLineItem(Items items, JSONObject wooLineItem) {
        Item item = new Item();
        item.setId(BecknIdHelper.getBecknId(String.valueOf(wooLineItem.get("product_id")), adaptor.getSubscriber().getSubscriberId(), Entity.item));
        item.setQuantity(new Quantity());
        item.getQuantity().setCount(doubleTypeConverter.valueOf(wooLineItem.get("quantity")).intValue());
        /*
        item.setDescriptor(new Descriptor());
        item.getDescriptor().setName((String)wooLineItem.get("name"));
        item.getDescriptor().setCode((String)wooLineItem.get("sku"));
        if (ObjectUtil.isVoid(item.getDescriptor().getCode())){
            item.getDescriptor().setCode(item.getDescriptor().getName());
        }
        item.getDescriptor().setLongDesc(item.getDescriptor().getName());
        item.getDescriptor().setShortDesc(item.getDescriptor().getName());


        Price price = new Price();
        item.setPrice(price);

        price.setListedValue(doubleTypeConverter.valueOf(wooLineItem.get("subtotal")));
        price.setValue(doubleTypeConverter.valueOf(wooLineItem.get("total")));
        price.setCurrency("INR");
        */
        items.add(item);


    }



    public JSONObject makeWooOrder(Order bo) {

        JSONObject order = new JSONObject();
        JSONObject billing = new JSONObject();
        JSONObject shipping = new JSONObject();

        if (!ObjectUtil.isVoid(bo.getId())){
            // FIXME ONDC uses uuid for ID, the logic below should expect uuid.
            // For now, we simply avoid setting the id in order, so that it is taken based on tx_id.
            //order.put("id",BecknIdHelper.getLocalUniqueId(bo.getId(),Entity.order));

        }else {
            order.put("set_paid",false);
        }
        setWooBilling(billing,bo.getBilling());
        if (!billing.isEmpty()){
            order.put("billing",billing);
        }
        setWooShipping(shipping,(Fulfillment) bo.getFulfillment());
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

        Config.instance().getLogger(getClass().getName()).info("WoocommerceHelper wooOrder" + order.toJSONString());
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
        Contact contact = (Contact) fulfillment.getEnd().getContact();

        Country country = Country.findByName(address.getCountry());
        State state = State.findByCountryAndName(country.getId(),address.getState());
        City city = City.findByStateAndName(state.getId(),address.getCity());

        shipping.put("address_1",address.getDoor()+"," +address.getBuilding());
        shipping.put("address_2",address.getStreet()+","+address.getLocality());
        shipping.put("city", city.getName());
        shipping.put("state",state.getCode());
        shipping.put("postcode",address.getAreaCode());
        shipping.put("country",country.getIsoCode2());
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
        if (boBilling.getAddress() != null){
            billing.put("address_1",boBilling.getAddress().getDoor()+"," +boBilling.getAddress().getBuilding());
            billing.put("address_2",boBilling.getAddress().getStreet()+","+boBilling.getAddress().getLocality());

            Country country = Country.findByName(boBilling.getAddress().getCountry());
            State state = State.findByCountryAndName(country.getId(),boBilling.getAddress().getState());
            City city = City.findByStateAndName(state.getId(),boBilling.getAddress().getCity());

            billing.put("city", city.getName());
            billing.put("state",city.getState().getCode());
            billing.put("country",city.getState().getCountry().getIsoCode2());
            billing.put("postcode",boBilling.getAddress().getAreaCode());
        }


        billing.put("email",boBilling.getEmail());
        billing.put("phone",boBilling.getPhone());

    }
    public Order getBecknOrder(JSONObject wooOrder) {
        Order order = new Order();
        order.setPayment(new Payment());
        setPayment(order.getPayment().cast(Payment.class),wooOrder);
        Quote quote = new Quote();
        order.setQuote(quote);
        //quote.setTtl(15*60);
        quote.setPrice(new Price());
        quote.getPrice().setValue(order.getPayment().getParams().getAmount());
        quote.getPrice().setCurrency(order.getPayment().getParams().getCurrency());
        BreakUp breakUp = new BreakUp();
        quote.setBreakUp(breakUp);
        BreakupElement shipping_total = breakUp.createElement("fulfillment","Delivery Charges", new Price()).cast(BreakupElement.class);
        shipping_total.setTitleType("delivery");
        // FIXME don't rely only on 0th element
        shipping_total.setItemId(BecknIdHelper.getBecknId(String.valueOf(wooOrder.get("id")),adaptor.getSubscriber().getSubscriberId(),Entity.fulfillment));
        Price stp = shipping_total.get(Price.class,"price");
        stp.setCurrency("INR");
        stp.setValue(30);
        breakUp.add(shipping_total);

        setBoBilling(order,wooOrder);
        order.setId(BecknIdHelper.getBecknId(String.valueOf(wooOrder.get("id")),adaptor.getSubscriber().getSubscriberId(),Entity.order));
        order.setState((String) wooOrder.get("status"));
        createItems(order,(JSONArray)wooOrder.get("line_items"));

        Fulfillment outFulfillment = new Fulfillment();
        Fulfillments oFulfillments = new Fulfillments();
        outFulfillment.setId(BecknIdHelper.getBecknId(String.valueOf(wooOrder.get("id")),adaptor.getSubscriber().getSubscriberId(),Entity.fulfillment));
        oFulfillments.add(outFulfillment);
        order.setFulfillments(oFulfillments);
        FulfillmentStop outEnd = new FulfillmentStop();
        outFulfillment.setEnd(outEnd);
        outEnd.setLocation(new Location());
        outEnd.getLocation().setAddress(new Address());
        outEnd.setContact(new Contact());
        //outFulfillment.setCustomer(new User());
        //outFulfillment.setState(order.getState());

        // FIXME Identify how to propagate type and provider_id to fulfillment in response
        outFulfillment.setFulfillmentType("Delivery");
        outFulfillment.setProviderId("glasshopper.in");
        outFulfillment.setTracking(false);

        Locations locations = new Locations();
        createLocation(locations);
        /*
        if (locations.size() > 0) {
            order.getFulfillment().setStart(new FulfillmentStop());
            order.getFulfillment().getStart().setLocation(locations.get(0));
        }
        */
        JSONObject shipping = (JSONObject) wooOrder.get("shipping");
        if (ObjectUtil.isVoid(shipping.get("address_1"))){
            shipping = (JSONObject) wooOrder.get("billing");
        }
        String[] address1_parts = ((String)shipping.get("address_1")).split(",");
        String[] address2_parts = ((String)shipping.get("address_2")).split(",");

        /*
        User user = order.getFulfillment().getCustomer();
        user.setPerson(new Person());
        Person person = (Person) user.getPerson();
        person.setName(shipping.get("first_name") + " " + shipping.get("last_name"));
        */
        Address address = order.getFulfillments().get(0).getEnd().getLocation().getAddress();
        // FIXME door value passed in init is not available in address1_parts
        if (address1_parts.length < 1) {
            address.setDoor("-");
        } else {
            address.setDoor(address1_parts[0]);
        }
        if (address1_parts.length > 1) {
            address.setBuilding(address1_parts[1]);
        }
        address.setStreet(address2_parts[0]);
        if (address2_parts.length > 1) {
            address.setLocality(address2_parts[1]);
        }
        Country country = Country.findByISO((String) shipping.get("country"));
        State state = State.findByCountryAndCode(country.getId(),(String)shipping.get("state"));
        City city = City.findByStateAndName(state.getId(),(String) shipping.get("city"));

        address.setCountry(country.getName());
        address.setState(state.getName());
        address.setPinCode((String)shipping.get("postcode"));
        address.setCity(city.getName());

        order.getFulfillments().get(0).getEnd().getContact().setPhone((String)shipping.get("phone"));
        order.getFulfillments().get(0).getEnd().getContact().setEmail((String)shipping.get("email"));


        order.setProvider(new Provider());
        order.getProvider().setId(BecknIdHelper.getBecknId(adaptor.getSubscriber().getSubscriberId(),
                adaptor.getSubscriber().getSubscriberId(), Entity.provider));
        Location outProviderLocation = new Location();
        outProviderLocation.setId(locations.get(0).getId());
        //order.setProviderLocation(locations.get(0));
        order.setProviderLocation(outProviderLocation);

        return order;
    }
    private void setPayment(Payment payment, JSONObject wooOrder) {

        if (!booleanTypeConverter.valueOf(wooOrder.get("date_paid"))) {
            payment.setStatus("NOT-PAID");
        }else {
            payment.setStatus("PAID");
        }
        payment.setType("ON-ORDER");
        payment.setCollectedBy("BPP");
        payment.setBuyerAppFinderFeeType("Percent");
        // FIXME Change this value to what is sent in init
        payment.setBuyerAppFinderFeeAmount(3);
        payment.setWithholdingAmount(0);
        payment.setReturnWindow("P1D");
        payment.setSettlementBasis("Collection");
        payment.setSettlementWindow("P2D");
        SettlementDetails stl_details = new SettlementDetails();
        SettlementDetail stl_detail = new SettlementDetail();
        stl_detail.setSettlementCounterparty("seller-app");
        stl_detail.setSettlementPhase("sale-amount");
        stl_detail.setSettlementType("upi");
        stl_detail.setUpiAddress("abc@okupi");
        stl_detail.setSettlementBankAccountNo("XXXXXXXXXX");
        stl_detail.setSettlementIfscCode("XXXXXXXXXX");
        stl_detail.setBeneficiaryName("Glasshopper");
        stl_detail.setBankName("SBI Bank");
        stl_detail.setBranchName("Jayanagar");
        stl_details.add(stl_detail);
        payment.setSettlementDetails(stl_details);

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
        // FIXME Extract Door info from wooOrder and assign here
        address.setDoor("-");
        address.setStreet((String)wooBilling.get("address_1"));
        address.setLocality((String)wooBilling.get("address_2"));
        address.setPinCode((String)wooBilling.get("postcode"));

        Country country= Country.findByISO((String)wooBilling.get("country"));
        State state = State.findByCountryAndCode(country.getId(),(String) wooBilling.get("state"));
        City city = City.findByStateAndName(state.getId(),(String)wooBilling.get("city"));

        address.setCountry(country.getName());
        address.setState(state.getName());
        address.setCity(city.getName());
        Date now = new Date(System.currentTimeMillis());
        billing.setCreatedAt(now);
        billing.setUpdatedAt(now);
    }

    public Location createLocation(Locations locations) {
        Location location = new Location();
        Address address = new Address();
        Circle circle = new Circle();
        Scalar radius = new Scalar();
        Time time = new Time();
        Schedule schedule = new Schedule();
        Range range = new Range();

        location.setAddress(address);
        location.setCircle(circle);
        circle.setScalarRadius(radius);
        location.setTime(time);
        time.setSchedule(schedule);
        time.setRange(range);

        address.setName(getSettings("general","woocommerce_store_address"));

        String dc = getSettings("general","woocommerce_default_country");
        String[] cs = dc.split(":");
        String  cityName = getSettings("general","woocommerce_store_city");
        if (cs.length == 2){
            Country country = Country.findByISO(cs[0]);

            String[] descriptions = getSettingsDescription("general","woocommerce_default_country",dc).split(" - ");

            if (!ObjectUtil.equals(country.getName(),descriptions[0])){
                country.setName(descriptions[0]);
                country.save();
            }
            address.setCountry(country.getName());


            State state = Database.getTable(State.class).newRecord();
            state.setCode("Unknown");
            state.setCountryId(country.getId());
            state = Database.getTable(State.class).getRefreshed(state);
            if (!state.getRawRecord().isNewRecord()){
                state.setCode(cs[1]);
                state.setName(descriptions[1]);
                state.save();
            }
            address.setState(state.getName());

            City city = Database.getTable(City.class).newRecord();
            city.setCode(adaptor.getConfiguration().get("in.succinct.bpp.woocommerce.city"));
            city.setStateId(state.getId());
            city.setName(cityName);

            city = Database.getTable(City.class).getRefreshed(city);
            city.save();

            address.setCity(city.getName());
        }
        address.setPinCode(getSettings("general","woocommerce_store_postcode"));
        address.setStreet(getSettings("general","woocommerce_store_address"));
        location.setId(BecknIdHelper.getBecknId(adaptor.getSubscriber().getSubscriberId(),
                adaptor.getSubscriber().getSubscriberId(),Entity.provider_location));
        // FIXME: Determine correct values for GPS
        GeoCoordinate myGps = new GeoCoordinate(12.967555,77.749666);
        location.setGps(myGps);
        circle.setGps(myGps);
        radius.setUnit("km");
        radius.setValue(5);
        schedule.setFrequency("PT4H");
        schedule.setHolidays(new BecknStrings());
        schedule.getHolidays().add("2022-08-15");

        schedule.setTimes(new BecknStrings());
        schedule.getTimes().add("1000");
        schedule.getTimes().add("1900");
        time.setDays("1,2,3,4,5,6,7");
        //FIXME: Fix below code so that range returns only hh:mm
        SimpleDateFormat worktimeformatter = new SimpleDateFormat("hh:mm");
        try {
            range.setStart(worktimeformatter.parse("10:00"));
            range.setEnd(worktimeformatter.parse("19:00"));
        } catch (ParseException pe) {
            pe.printStackTrace();
        }


        locations.add(location);
        return location;
    }

    public Fulfillments createFulfillments(Order outOrder) {
        Fulfillments outFulfillments = new Fulfillments();
        Fulfillment outFulfillment = new Fulfillment();
        outFulfillment.setId("Fulfillment1");
        // For Self ship (not using ONDC Logistics) Logistics provider can be the Retail Provider.
        // FIXME Use set value from Provider than hard code
        outFulfillment.setProviderName("Glasshopper");
        outFulfillment.setTracking(false);
        outFulfillment.setCategory("Standard Delivery");
        outFulfillment.setTAT("PT360M");
        in.succinct.beckn.ondc.retail.State fState = new in.succinct.beckn.ondc.retail.State();
        outFulfillment.setState(fState);
        Descriptor stateDescriptor = new Descriptor();
        fState.setDescriptor(stateDescriptor);
        stateDescriptor.setName("Serviceable");
        outFulfillments.add(outFulfillment);
        outOrder.setFulfillments(outFulfillments);
        return outFulfillments;
    }

    public void getTags(Order inOrder, Order outOrder) {
        Tags outTags = new Tags();
    }
}
