package in.succinct.bpp.woocommerce.adaptor;

import com.venky.core.math.DoubleUtils;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.State;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Address;
import in.succinct.beckn.BecknStrings;
import in.succinct.beckn.Billing;
import in.succinct.beckn.BreakUp;
import in.succinct.beckn.BreakUp.BreakUpElement.BreakUpCategory;
import in.succinct.beckn.Contact;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.RetailFulfillmentType;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Fulfillments;
import in.succinct.beckn.Images;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Message;
import in.succinct.beckn.Order;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payments;
import in.succinct.beckn.Person;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.beckn.User;
import in.succinct.bpp.core.adaptor.TimeSensitiveCache;
import in.succinct.bpp.core.adaptor.fulfillment.FulfillmentStatusAdaptor;
import in.succinct.bpp.core.db.model.LocalOrderSynchronizer;
import in.succinct.bpp.core.db.model.LocalOrderSynchronizerFactory;
import in.succinct.bpp.core.db.model.ProviderConfig;
import in.succinct.bpp.search.adaptor.SearchAdaptor;
import in.succinct.bpp.woocommerce.model.AttributeKey;
import in.succinct.bpp.woocommerce.model.Continents;
import in.succinct.bpp.woocommerce.model.Countries;
import in.succinct.bpp.woocommerce.model.GeneralSetting;
import in.succinct.bpp.woocommerce.model.Products;
import in.succinct.bpp.woocommerce.model.SettingAttribute;
import in.succinct.bpp.woocommerce.model.SettingGroup;
import in.succinct.bpp.woocommerce.model.Shop;
import in.succinct.bpp.woocommerce.model.States;
import in.succinct.bpp.woocommerce.model.TaxSetting;
import in.succinct.bpp.woocommerce.model.Taxes;
import in.succinct.bpp.woocommerce.model.Tuple;
import in.succinct.bpp.woocommerce.model.WooCommerceOrder;
import in.succinct.bpp.woocommerce.model.WooCommerceOrder.LineItem;
import in.succinct.bpp.woocommerce.model.WooCommerceOrder.ShippingLine;
import in.succinct.bpp.woocommerce.model.WooCommerceOrder.ShippingLines;
import in.succinct.onet.core.api.BecknIdHelper;
import in.succinct.onet.core.api.BecknIdHelper.Entity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class ECommerceAdaptor extends SearchAdaptor {

    private static final JdbcTypeHelper.TypeConverter<Double> doubleTypeConverter = Database.getJdbcTypeHelper("")
            .getTypeRef(double.class).getTypeConverter();

    final ApiHelper helper;
    final TimeSensitiveCache cache = new TimeSensitiveCache(Duration.ofDays(1));

    public ECommerceAdaptor(Map<String, String> configuration, Subscriber subscriber) {
        super(configuration, subscriber);
        this.helper = new ApiHelper(this);
        getProviderConfig().setLocation(getProviderLocations().get(0));
    }

    @Override
    public void clearCache() {
        cache.clear();
    }

    @Override
    public Locations getProviderLocations() {
        return cache.get(Locations.class, () -> {
            Locations locations = new Locations();
            locations.add(providerLocation());
            return locations;
        });
    }

    @Override
    public Items getItems() {
        return cache.get(Items.class, () -> {
            Items items = new Items();
            Products products = getInStockPublicProducts();

            products.forEach(product -> {
                Item item  =ECommerceAdaptor.this.createItem(product);
                Location location =  getProviderLocations().get(0);
                item.setLocationId(location.getId());
                item.setLocationIds(new BecknStrings());
                item.getLocationIds().add(location.getId());
                items.add(item);
            });

            return items;
        });
    }

    @Override
    public boolean isTaxIncludedInPrice() {
        return getShop().getTaxSetting().getAttribute(SettingAttribute.AttributeKey.PRICES_INCLUDES_TAX).getValue()
                .equals("yes");
    }

    @Override
    public void init( Request request,  Request reply) {
        WooCommerceOrder woocommerceOrder = new WooCommerceOrder();
        Order becknOrder = request.getMessage().getOrder();
        fixFulfillment(request.getContext(), becknOrder);
        fixLocation(becknOrder);
        Fulfillment fulfillment = becknOrder.getFulfillment();
        Location storeLocation = becknOrder.getProviderLocation();

        ProviderConfig.Serviceability serviceability = getProviderConfig().getServiceability(fulfillment.getType(), fulfillment._getEnd(), storeLocation);
        if (!serviceability.isServiceable()) {
            throw serviceability.getReason();
        }

        woocommerceOrder.setId(LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber())
                .getLocalOrderId(request.getContext().getTransactionId()));
        woocommerceOrder.setCurrency(AttributeKey.inr.getKey());
        woocommerceOrder.setSourceName(AttributeKey.beckn.getKey());
        woocommerceOrder.setName(AttributeKey.becknDash.getKey() + request.getContext().getTransactionId());
        woocommerceOrder.setMetaDataArray(new WooCommerceOrder.MetaDataArray());

        for (String key : new String[] { AttributeKey.bapId.getKey(), AttributeKey.bapUri.getKey(),
                AttributeKey.domain.getKey(), AttributeKey.transactionId.getKey(), AttributeKey.city.getKey(),
                AttributeKey.country.getKey(),
                AttributeKey.coreVersion.getKey(), }) {
            WooCommerceOrder.MetaData meta = new WooCommerceOrder.MetaData();
            meta.setKey(String.format("context.%s", key));
            meta.setValue(request.getContext().get(key));
            woocommerceOrder.getMetaDataArray().add(meta);
        }

        if (!ObjectUtil.isVoid(woocommerceOrder.getId())) {
            delete(woocommerceOrder);
        }

        WooCommerceOrder.OrderShipping orderShipping = createWoocommerceShipping(fulfillment);
        if (orderShipping != null) {
            woocommerceOrder.setOrderShipping(orderShipping);
        }

        if (becknOrder.getBilling() == null) {
            becknOrder.setBilling(new Billing());
        }
        if (becknOrder.getBilling().getAddress() == null) {
            becknOrder.getBilling().setAddress(becknOrder.getFulfillment()._getEnd().getLocation().getAddress());
        }

        Billing billing = becknOrder.getBilling();
        WooCommerceOrder.OrderBilling orderBilling = createWoocommerceBilling(billing);
        if (orderBilling != null) {
            woocommerceOrder.setOrderBilling(orderBilling);
        }

        //woocommerceOrder.setId(BecknIdHelper.getLocalUniqueId(getProviderConfig().getLocation().getId(), Entity.provider_location));

        if (becknOrder.getItems() != null) {
            woocommerceOrder.setLineItems(createWoocommerceLineItems(becknOrder.getItems()));
        }

        woocommerceOrder.setShippingLines(new ShippingLines());
        ShippingLine shippingLine = new ShippingLine();
        shippingLine.setTotal(serviceability.getCharges());
        shippingLine.setMethodId("flat_rate");
        woocommerceOrder.getShippingLines().add(shippingLine);

        LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber()).sync(request.getContext().getTransactionId(), becknOrder);
        Order order = getBecknOrder(createWoocommerceOrder(woocommerceOrder.getInner()));

        if (reply.getMessage() == null){
            reply.setMessage(new Message());
        }
        reply.getMessage().setOrder(order);
    }

    @Override
    public Order confirmDraftOrder(Order order) {
        if (order == null) {
            throw new RuntimeException("No Order passed");
        }
        String wooCommerceOrderId = LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber()).getLocalOrderId(order);
        JSONObject params = new JSONObject();

        if (order.isPaid()) {
            params.put("set_paid", true );
        }else {
            params.put("status", "pending");
        }
        WooCommerceOrder wooCommerceOrder = new WooCommerceOrder(helper.putViaPost("/orders/" + wooCommerceOrderId,params));


        return getBecknOrder(wooCommerceOrder);
    }

    @Override
    public Order getStatus( Order order) {
        String woocommerceOrderId = LocalOrderSynchronizerFactory.getInstance()
                .getLocalOrderSynchronizer(getSubscriber()).getLocalOrderId(order);

        return getBecknOrder(getWooCommerceOrder(woocommerceOrderId));
    }

    @Override
    public Order cancel(Order order) {

        String woocommerceOrderId = LocalOrderSynchronizerFactory.getInstance()
                .getLocalOrderSynchronizer(getSubscriber()).getLocalOrderId(order);

        JSONObject params = new JSONObject();
        params.put(AttributeKey.status.getKey(), AttributeKey.cancelled.getKey());
        JSONObject outOrder = helper.putViaPost("/orders/" + woocommerceOrderId, params);
        return getBecknOrder(createWoocommerceOrder(outOrder));
    }

    @Override
    public String getTrackingUrl(Order order) {
        return null;
    }

    @Override
    public List<FulfillmentStatusAdaptor.FulfillmentStatusAudit> getStatusAudit(Order order) {

        String woocommerceOrderId = LocalOrderSynchronizerFactory.getInstance()
                .getLocalOrderSynchronizer(getSubscriber()).getLocalOrderId(order);
        WooCommerceOrder eCommerceOrder = getWooCommerceOrder(woocommerceOrderId);
        FulfillmentStatusAdaptor adaptor = getFulfillmentStatusAdaptor();
        assert eCommerceOrder != null;
        String transactionId = getBecknTransactionId(eCommerceOrder);

        if (adaptor != null) {
            List<FulfillmentStatusAdaptor.FulfillmentStatusAudit> audits = adaptor
                    .getStatusAudit(StringUtil.valueOf(eCommerceOrder.getNumber()));
            for (Iterator<FulfillmentStatusAdaptor.FulfillmentStatusAudit> i = audits.iterator(); i.hasNext();) {
                FulfillmentStatusAdaptor.FulfillmentStatusAudit audit = i.next();
                LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber())
                        .setFulfillmentStatusReachedAt(transactionId, audit.getFulfillmentStatus(), audit.getDate(),
                                !i.hasNext());
            }
        }
        return LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber())
                .getFulfillmentStatusAudit(transactionId);

    }

    private static  Date convertStringToDate(String dateString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        try {
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            // handle the parsing exception
            return null;
        }
    }

    private Shop getShop() {
        return cache.get(Shop.class, () -> {
            GeneralSetting generalSetting = new GeneralSetting();
            for (SettingAttribute.AttributeKey attributeKey : SettingAttribute.AttributeKey
                    .getSettingGroupAttributes(SettingGroup.GENERAL)) {
                JSONObject response = fetchAttributeFromAPI(attributeKey);
                generalSetting.setAttribute(attributeKey, new SettingAttribute(response, attributeKey));
            }

            TaxSetting taxSetting = new TaxSetting();

            for (SettingAttribute.AttributeKey attributeKey : SettingAttribute.AttributeKey
                    .getSettingGroupAttributes(SettingGroup.TAX)) {
                JSONObject response = fetchAttributeFromAPI(attributeKey);
                taxSetting.setAttribute(attributeKey, new SettingAttribute(response, attributeKey));
            }

            return new Shop(generalSetting, taxSetting);
        });
    }

    @SuppressWarnings("unchecked")
    private JSONObject fetchAttributeFromAPI(SettingAttribute.AttributeKey attributeKey) {
        JSONObject groupSettingJson = cache.get("setting.group."+attributeKey.getGroup().getKey(),()->{
            JSONArray array =  helper.get(new SettingAttribute(attributeKey).getGroupApiEndPoint(), new JSONObject());
            JSONObject json = new JSONObject();
            for (Object o : array) {
                JSONObject settingAttributeJSON = (JSONObject) o;
                json.put(settingAttributeJSON.get("id"),settingAttributeJSON);
            }
            return json;
        });
        return (JSONObject) groupSettingJson.get(attributeKey.getKey());
    }

    private Tuple<String, String> splitCountryState(String value) {
        if (value != null && value.contains(":")) {
            String[] parts = value.split(":");
            return new Tuple<>(parts[0], parts[1]);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Continents getAllContinents() {
        return cache.get(Continents.class, () -> {
            Continents continentsList = new Continents();

            JSONArray continents = helper.get(Continents.getApiEndpoint(), new JSONObject());
            if (continents == null || continents.isEmpty())
                return continentsList;

            continents.forEach(o -> {
                JSONObject continent = (JSONObject) o;
                continentsList.add(new Continents.Continent(continent));
            });

            return continentsList;
        });
    }

    private  Countries getAllCountries( Continents continents) {
        return cache.get(Countries.class, () -> {
            final Countries countries  = new Countries();

            continents.forEach(continent -> {
                continent.getCountries().forEach(countries::add);
            });
            return countries;
        });
    }

    private Countries.Country getCountry( Countries countries, String code) {
            return countries.get(code);
    }

    private  String generateQueryURL( String endpoint, Map<String, String> queryParams) {
        StringBuilder urlBuilder = new StringBuilder(endpoint);
        if (queryParams == null || queryParams.isEmpty()) {
            return urlBuilder.toString();
        }
        urlBuilder.append("?");
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Append the key and value as a query parameter
            urlBuilder.append(key).append("=").append(value).append("&");
        }

        // Remove the trailing "&" character
        urlBuilder.deleteCharAt(urlBuilder.length() - 1);

        return urlBuilder.toString();
    }

    private Products getInStockPublicProducts() {
        return cache.get(Products.class, () -> {
            int page = 1;
            final int per_page = 40;
            final int maxPages = 25;
            JSONArray allProductsJson = new JSONArray();
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put(AttributeKey.status.getKey(), AttributeKey.publish.getKey());
            queryParams.put(AttributeKey.stockStatus.getKey(), AttributeKey.instock.getKey());
            queryParams.put(AttributeKey.perPage.getKey(), StringUtil.valueOf(per_page));

            while (true) {
                queryParams.put(AttributeKey.page.getKey(), StringUtil.valueOf(page));
                String finalUrl = generateQueryURL(AttributeKey.products.getKey(), queryParams);
                JSONArray response = helper.get(finalUrl, new JSONObject());

                if (response.isEmpty()) {
                    break; // Exit the loop if the response is empty
                }

                // Append the current response to the accumulated responses
                allProductsJson.addAll(response);

                if (response.size() < per_page) {
                    break;
                }

                page++;
                if (page > maxPages) {
                    break;
                }
            }

            if (allProductsJson.isEmpty()) {
                return null;
            }

            return new Products(allProductsJson);
        });

    }

    private Taxes getTaxes() {
        return cache.get(Taxes.class, () -> {
            JSONArray response = helper.get(Taxes.AttributeKey.taxes.getKey(), new JSONObject());
            return new Taxes(response);
        });
    }

    private  Item createItem(Products. Product product) {
        Item item = new Item();
        product.getAttributes().forEach(a->{
            item.setTag("general_attributes",a.getName(),a.getOptions().get(a.getPosition()));
        });

        item.setId(BecknIdHelper.getBecknId(StringUtil.valueOf(product.getId()),
                this.getSubscriber(), Entity.item));
        item.setDescriptor(new Descriptor());
        Descriptor descriptor = item.getDescriptor();

        // Basic Details
        descriptor.setName(product.getName());
        descriptor.setCode(product.getSku());
        descriptor.setShortDesc(product.getShortDescription());
        descriptor.setLongDesc(product.getDescription());

        // Images
        descriptor.setImages(new Images());
        Products.ProductImages images = product.getImages();
        images.forEach(image -> {
            descriptor.getImages().add(image.getSrc());
        });
        descriptor.setSymbol(descriptor.getImages().get(0).getUrl());

        // Category
        item.setCategoryId(getProviderConfig().getCategory().getId());
        item.setCategoryIds(new BecknStrings());
        item.getCategoryIds().add(item.getCategoryId());
        product.getTags().forEach(tag -> {
            item.setTag("general_attributes",tag.getName(),"true");
        });

        // Price
        Price price = new Price();
        item.setPrice(price);
        price.setMaximumValue(price.getListedValue());
        price.setListedValue(product.getRegularPrice());
        price.setValue(product.getPrice());
        price.setCurrency("INR");

        // Payment
        item.setPaymentIds(new BecknStrings());
        for (Payment payment : getSupportedPaymentCollectionMethods()) {
            item.getPaymentIds().add(payment.getId()); // Only allow By BAP , ON_ORDER
        }

        // Shipping & Return
        item.setReturnable(getProviderConfig().isReturnSupported());
        if (item.isReturnable()) {
            item.setReturnWindow(getProviderConfig().getReturnWindow());
            item.setSellerPickupReturn(getProviderConfig().isReturnPickupSupported());
        } else {
            item.setReturnWindow(Duration.ofDays(0));
        }

        item.setCancellable(true);
        item.setTimeToShip(getProviderConfig().getTimeToShip());
        item.setAvailableOnCod(getProviderConfig().isCodSupported());
        item.setContactDetailsConsumerCare(getProviderConfig().getLocation().getAddress().flatten() + " "
                + getProviderConfig().getSupportContact().flatten());
        item.setFulfillmentIds(new BecknStrings());

        // Fulfillment
        for (Fulfillment fulfillment : getFulfillments()) {
            item.getFulfillmentIds().add(fulfillment.getId());
        }
        item.setTaxRate(0);
        item.setTax(new Price());
        item.getTax().setValue(0.0);
        item.getTax().setCurrency(getShop().getGeneralSetting().getAttribute(SettingAttribute.AttributeKey.CURRENCY).getValue());


        return item;

    }

    private void delete( WooCommerceOrder draftOrder) {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(AttributeKey.force.getKey(), "true");
        helper.delete(
                generateQueryURL(String.format("%s/%s", AttributeKey.orders.getKey(), draftOrder.getId()), queryParams),
                new JSONObject());
        draftOrder.rm(AttributeKey.id.getKey());
    }

    private WooCommerceOrder.OrderShipping createWoocommerceShipping(Fulfillment source) {
        if (source == null) {
            return null;
        }
        WooCommerceOrder.OrderShipping shipping = new WooCommerceOrder.OrderShipping();
        User user = source.getCustomer();
        Address address = source._getEnd().getLocation().getAddress();
        Contact contact = source._getEnd().getContact();
        if (user == null && address != null) {
            user = new User();
            user.setPerson(new Person());
            user.getPerson().setName(address.getName());
        }

        if (user != null) {
            String[] parts = user.getPerson().getName().split(" ");
            shipping.setFirstName(parts[0]);
            shipping.setLastName(user.getPerson().getName().substring(parts[0].length()));
        }
        if (address != null) {
            if (address.getCountry() == null) {
                address.setCountry(getProviderConfig().getLocation().getAddress().getCountry());
            }
            Country country = Country.findByName(address.getCountry());
            State state = findStateByName(country.getId(), address.getState());

            City city = findCity(state.getId(), address.getCity());
            shipping.setAddress1(address.flatten());
            //shipping.setAddress2(address.getStreet() + "," + address.getLocality());
            shipping.setCity(city.getName());
            shipping.setStateCode(state.getCode());
            shipping.setPostcode(address.getAreaCode());
            shipping.setCountryCode(country.getIsoCode2());
            shipping.setEmail(contact.getEmail());
            shipping.setPhone(contact.getPhone());
        }
        return shipping;
    }

    private WooCommerceOrder.OrderBilling createWoocommerceBilling(Billing source) {
        if (source == null) {
            return null;
        }

        WooCommerceOrder.OrderBilling billing = new WooCommerceOrder.OrderBilling();
        String[] parts = source.getName().split(" ");
        billing.setFirstName(parts[0]);
        billing.setLastName(source.getName().substring(parts[0].length()));
        Address address = source.getAddress();
        if (address != null) {
            billing.setAddress1(source.getAddress().flatten());
            Country country = Country.findByName(source.getAddress().getCountry());
            State state = findStateByName(country.getId(), source.getAddress().getState());
            City city = findCity(state.getId(), source.getAddress().getCity());
            billing.setCity(city.getName());
            billing.setStateCode(city.getState().getCode());
            billing.setCountryCode(city.getState().getCountry().getIsoCode2());
            billing.setPostcode(source.getAddress().getAreaCode());
        }

        billing.setEmail(source.getEmail());
        billing.setPhone(source.getPhone());

        return billing;

    }

    private WooCommerceOrder. LineItems createWoocommerceLineItems(Order.NonUniqueItems items) {
        WooCommerceOrder.LineItems lineItems = new WooCommerceOrder.LineItems();
        items.forEach(item -> {
            WooCommerceOrder.LineItem lineItem = new WooCommerceOrder.LineItem();
            lineItem.setProductId(BecknIdHelper.getLocalUniqueId(item.getId(), Entity.item));
            lineItem.setQuantity(item.getQuantity().getCount());
            lineItems.add(lineItem);
        });
        return lineItems;
    }

    private  Payment createBecknPayment( WooCommerceOrder woocommerceOrder) {
        Payment payment = new Payment();
        if (!woocommerceOrder.isOrderPaid()) {
            payment.setStatus(Payment.PaymentStatus.NOT_PAID);
        } else {
            payment.setStatus(Payment.PaymentStatus.PAID);
        }
        payment.setPaymentType(Payment.POST_FULFILLMENT);
        payment.setParams(new Payment.Params());
        payment.getParams().setCurrency(
                getShop().getGeneralSetting().getAttribute(SettingAttribute.AttributeKey.CURRENCY).getValue());
        payment.getParams().setAmount(woocommerceOrder.getTotal());
        if (!woocommerceOrder.isOrderPaid()){
            payment.setUri(Config.instance().getServerBaseUrl()+"/payment.html");
            payment.setTlMethod("http/get");
        }
        return payment;
    }

    private WooCommerceOrder createWoocommerceOrder(JSONObject parameter) {
        JSONObject order = helper.post("/orders", parameter);
        return new WooCommerceOrder(order);
    }

    private Order. NonUniqueItems createBecknItems(WooCommerceOrder. LineItems lineItems, Fulfillment fulfillment) {
        Order.NonUniqueItems items = new Order.NonUniqueItems();

        lineItems.forEach(lineItem -> {
            Item item = new Item();
            item.setDescriptor(new Descriptor());
            item.setId(BecknIdHelper.getBecknId(StringUtil.valueOf(lineItem.get(AttributeKey.productId.getKey())),
                    this.getSubscriber(), Entity.item));
            item.getDescriptor().setName(lineItem.getName());
            item.getDescriptor().setCode(lineItem.getSku());
            if (ObjectUtil.isVoid(item.getDescriptor().getCode())) {
                item.getDescriptor().setCode(item.getDescriptor().getName());
            }
            item.getDescriptor().setLongDesc(item.getDescriptor().getName());
            item.getDescriptor().setShortDesc(item.getDescriptor().getName());
            Quantity quantity = new Quantity();
            quantity.setCount(doubleTypeConverter.valueOf(lineItem.getQuantity()).intValue());
            item.setQuantity(quantity);
            item.setFulfillmentId(fulfillment.getId());

            Price price = new Price();
            item.setPrice(price);

            price.setListedValue(doubleTypeConverter.valueOf(lineItem.getSubtotal()));
            price.setValue(doubleTypeConverter.valueOf(lineItem.getTotal()));
            price.setCurrency(AttributeKey.inr.getKey());
            items.add(item);
        });

        return items;
    }

    private  Location providerLocation() {
        GeneralSetting generalSetting = getShop().getGeneralSetting();

        Countries countries = getAllCountries(getAllContinents());
        Tuple<String, String> countryState = splitCountryState(
                generalSetting.getAttribute(SettingAttribute.AttributeKey.COUNTRY).getValue());
        Countries.Country country = getCountry(countries, countryState.first);
        States.State state = country.getStates().get(countryState.second);
        String address1 = generalSetting.getAttribute(SettingAttribute.AttributeKey.ADDRESS_1).getValue();
        String address2 = generalSetting.getAttribute(SettingAttribute.AttributeKey.ADDRESS_2).getValue();
        String city = generalSetting.getAttribute(SettingAttribute.AttributeKey.CITY).getValue();
        String pincode = generalSetting.getAttribute(SettingAttribute.AttributeKey.CITY).getValue();

        Address address = new Address();
        address.setName(getProviderConfig().getStoreName());
        address.setStreet(address1);
        address.setCity(city);
        address.setPinCode(pincode);
        address.setCountry(country.getAttribute(AttributeKey.code));
        address.setState(state.getName());

        Location location = new Location();
        location.setId(BecknIdHelper.getBecknId("1",
                this.getSubscriber(), Entity.provider_location));

        location.setAddress(address);
        String[] latLng = address2.split(",");
        location.setGps(new GeoCoordinate(Double.parseDouble(latLng[0]),Double.parseDouble(latLng[1])));
        location.setTime(getProviderConfig().getTime());
        location.setDescriptor(new Descriptor());
        location.getDescriptor().setName(location.getAddress().getName());
        return location;
    }

    private  Order getBecknOrder(WooCommerceOrder woocommerceOrder) {
        String transactionId = getBecknTransactionId(woocommerceOrder);
        LocalOrderSynchronizer localOrderSynchronizer = LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber());
        localOrderSynchronizer.setLocalOrderId(transactionId, woocommerceOrder.getId());
        Order lastKnownOrder = localOrderSynchronizer.getLastKnownOrder(transactionId);

        Order order = new Order();
        order.update(lastKnownOrder);
        order.setPayments(new Payments(){{
            add(createBecknPayment(woocommerceOrder));
        }});
        Quote quote = new Quote();
        order.setQuote(quote);
        quote.setTtl(15 * 60);
        quote.setPrice(new Price());
        quote.getPrice().setValue(order.getPayments().get(0).getParams().getAmount());
        quote.getPrice().setCurrency(order.getPayments().get(0).getParams().getCurrency());
        quote.setBreakUp(new BreakUp());

        Bucket productTotal = new Bucket();
        for (LineItem lineItem : woocommerceOrder.getLineItems()) {
            productTotal.increment(lineItem.getTotal());
        }


        BreakUp.BreakUpElement element = quote.getBreakUp().createElement(BreakUp.BreakUpElement.BreakUpCategory.item,
                "Total Product", new Price(){{
                    setValue(productTotal.doubleValue());
                    setCurrency(woocommerceOrder.getCurrency());
                }});
        quote.getBreakUp().add(element);


        BreakUp.BreakUpElement delivery = quote.getBreakUp().createElement(BreakUpCategory.delivery,"Total Delivery", new Price(){{
            setValue(woocommerceOrder.getShippingTotal());
            setCurrency(woocommerceOrder.getCurrency());
        }});
        quote.getBreakUp().add(delivery);

        double others = woocommerceOrder.getTotal() - woocommerceOrder.getShippingTotal() - productTotal.doubleValue();
        if (DoubleUtils.compareTo(others,0) > 0){
            BreakUp.BreakUpElement misc = quote.getBreakUp().createElement(BreakUpCategory.misc,"Misc Fees & Other Charges", new Price(){{
                setValue(others);
                setCurrency(woocommerceOrder.getCurrency());
            }});
            quote.getBreakUp().add(misc);
        }



        // Delivery breakup to be filled.
        order.setBilling(woocommerceOrder.getOrderBilling().toBeckn());
        order.setStatus(woocommerceOrder.getBecknOrderStatus());


        if (order.getFulfillment() == null) {
            order.setFulfillment(new Fulfillment());
        }
        if (order.getFulfillment().getType() == null){
            order.getFulfillment().setType(RetailFulfillmentType.home_delivery.name());
        }
        order.getFulfillment()._setEnd(new FulfillmentStop());
        order.getFulfillment()._getEnd().setLocation(new Location());
        order.getFulfillment()._getEnd().getLocation().setAddress(new Address());
        order.getFulfillment()._getEnd().setContact(new Contact());
        order.getFulfillment().setCustomer(new User());
        order.getFulfillment().rm("id");
        order.getFulfillment().setId(BecknIdHelper.getBecknId(StringUtil.valueOf(woocommerceOrder.getId()),
                this.getSubscriber(), Entity.fulfillment));

        order.getFulfillment().setFulfillmentStatus(woocommerceOrder.getFulfillmentStatus());


        //Items added here.
        order.setItems(createBecknItems(woocommerceOrder.getLineItems(),order.getFulfillment()));

        Locations locations = new Locations();
        locations.add(providerLocation());

        if (!locations.isEmpty()) {
            order.getFulfillment()._setStart(new FulfillmentStop());
            order.getFulfillment()._getStart().setLocation(locations.get(0));
        }

        WooCommerceOrder.OrderShipping shipping = woocommerceOrder.getOrderShipping();
        WooCommerceOrder.OrderBilling billing = woocommerceOrder.getOrderBilling();
        String[] address1_parts = shipping.getAddress1().split(",");
        String[] address2_parts = shipping.getAddress2().split(",");
        User user = order.getFulfillment().getCustomer();
        user.setPerson(new Person());
        Person person = user.getPerson();
        person.setName(shipping.getFirstName() + " " + shipping.getLastName());
        if (address1_parts.length == 0) {
            address1_parts = billing.getAddress1().split(",");
            address2_parts = billing.getAddress2().split(",");
        }

        if (person.getName().isEmpty()) {
            person.setName(billing.getFirstName() + " " + billing.getLastName());
        }


        Address address = order.getFulfillment()._getEnd().getLocation().getAddress();
        order.getFulfillment()._getEnd().setPerson(person);
        if (address1_parts.length > 0) {
            address.setDoor(address1_parts[0]);
        }
        if (address1_parts.length > 1) {
            address.setBuilding(address1_parts[1]);
        }
        if (address2_parts.length > 0) {
            address.setStreet(address2_parts[0]);
        }
        if (address2_parts.length > 1) {
            address.setLocality(address2_parts[1]);
        }
        Country country = Country.findByISO(shipping.getCountryCode());
        State state = findStateByName(country.getId(),shipping.getStateCode());

        City city = findCity(state.getId(), shipping.getCity());

        address.setCountry(country.getName());
        address.setState(state.getName());
        address.setPinCode(shipping.getPostcode());
        address.setCity(city.getName());

        order.getFulfillment()._getEnd().getContact().setPhone(shipping.getPhone());
        order.getFulfillment()._getEnd().getContact().setEmail(shipping.getEmail());
        if (order.getFulfillments() == null){
            order.setFulfillments(new Fulfillments());
            order.getFulfillments().add(order.getFulfillment());
        }

        order.setProvider(new Provider());
        order.getProvider().setId(getSubscriber().getSubscriberId());
        order.getProvider().setDescriptor(new Descriptor());
        order.getProvider().getDescriptor().setName(getProviderConfig().getStoreName());
        order.getProvider().setCategoryId(getProviderConfig().getCategory().getId());
        order.getProvider().setLocations(new Locations());
        order.setProviderLocation(locations.get(0));
        order.getProvider().getLocations().add(order.getProviderLocation());

        order.setCreatedAt(woocommerceOrder.getCreateDateGmt());
        order.setUpdatedAt(woocommerceOrder.getUpdatedDateGmt());

        return order;
    }

    private City findCity(long stateId, String cityCode) {
        List<String> cityCodes = new ArrayList<>();
        if (cityCode.contains(",")){
            StringTokenizer tokenizer = new StringTokenizer(cityCode,",");
            while(tokenizer.hasMoreTokens()){
                cityCodes.add(tokenizer.nextToken().trim());
            }
        }else {
            cityCodes.add(cityCode);
        }
        boolean createIfAbsent = cityCodes.size() == 1 ;

        City city = null ;
        for ( String code : cityCodes ){
            city = City.findByStateAndName(stateId, code , createIfAbsent);
            if (city != null){
                break;
            }
        }

        if (city == null){
            throw new RuntimeException("Unknown City "  + cityCode);
        }
        return city;

    }

    private State findStateByName(long countryId, String stateName) {
        List<String> stateNames = new ArrayList<>();

        if (stateName.contains(",")){
            StringTokenizer tokenizer = new StringTokenizer(stateName, ",");
            while(tokenizer.hasMoreTokens()){
                stateNames.add(tokenizer.nextToken().trim());
            }
        }else {
            stateNames.add(stateName);
        }
        boolean createIfAbsent = stateNames.size() == 1;
        State state = null ;
        for ( String name : stateNames ){
            state = State.findByCountryAndName(countryId, name , createIfAbsent);
            if (state != null){
                break;
            }
        }

        if (state == null){
            throw new RuntimeException("Unknown state" + stateName);
        }
        return state;
    }
    private WooCommerceOrder getWooCommerceOrder(String orderId) {
        try {

            JSONObject orderDetails = helper.get("/orders/" + orderId, new JSONObject());
            return new WooCommerceOrder(orderDetails);
        } catch (Exception e) {
            return null;
        }

    }

    private  String getBecknTransactionId( WooCommerceOrder draftOrder) {
        for (WooCommerceOrder.MetaData metaData : draftOrder.getMetaDataArray()) {
            if (metaData.getKey().equals("context.transaction_id")) {
                return metaData.getValue();
            }
        }
        return null;
    }

}
