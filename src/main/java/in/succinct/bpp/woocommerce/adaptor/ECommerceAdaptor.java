package in.succinct.bpp.woocommerce.adaptor;

import com.venky.cache.Cache;
import com.venky.core.date.DateUtils;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.participants.admin.Facility;
import com.venky.swf.plugins.gst.db.model.assets.AssetCode;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.Address;
import in.succinct.beckn.BecknException;
import in.succinct.beckn.BecknStrings;
import in.succinct.beckn.Billing;
import in.succinct.beckn.Cancellation;
import in.succinct.beckn.Cancellation.CancelledBy;
import in.succinct.beckn.CancellationReasons.CancellationReasonCode;
import in.succinct.beckn.Catalog;
import in.succinct.beckn.Categories;
import in.succinct.beckn.Category;
import in.succinct.beckn.Circle;
import in.succinct.beckn.City;
import in.succinct.beckn.Contact;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Error;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentStatus;
import in.succinct.beckn.Fulfillment.RetailFulfillmentType;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Fulfillments;
import in.succinct.beckn.Images;
import in.succinct.beckn.Invoice;
import in.succinct.beckn.Invoice.Invoices;
import in.succinct.beckn.Item;
import in.succinct.beckn.ItemQuantity;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Message;
import in.succinct.beckn.Option;
import in.succinct.beckn.Order;
import in.succinct.beckn.Order.Status;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.CollectedBy;
import in.succinct.beckn.Payment.Params;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Payment.PaymentTransaction;
import in.succinct.beckn.Payments;
import in.succinct.beckn.Person;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Request;
import in.succinct.beckn.Scalar;
import in.succinct.beckn.SellerException;
import in.succinct.beckn.SellerException.NoDataAvailable;
import in.succinct.beckn.State;
import in.succinct.bpp.core.adaptor.CommerceAdaptor;
import in.succinct.bpp.core.db.model.LocalOrderSynchronizer;
import in.succinct.bpp.core.db.model.LocalOrderSynchronizerFactory;
import in.succinct.bpp.core.db.model.User;
import in.succinct.bpp.woocommerce.db.model.Company;
import in.succinct.bpp.woocommerce.model.AttributeKey;
import in.succinct.bpp.woocommerce.model.ECommerceOrder;
import in.succinct.bpp.woocommerce.model.ECommerceOrder.LineItem;
import in.succinct.bpp.woocommerce.model.ECommerceOrder.LineItems;
import in.succinct.bpp.woocommerce.model.ECommerceOrder.MetaData;
import in.succinct.bpp.woocommerce.model.ECommerceOrder.MetaDataArray;
import in.succinct.bpp.woocommerce.model.ECommerceOrder.ShippingLine;
import in.succinct.bpp.woocommerce.model.Products;
import in.succinct.bpp.woocommerce.model.Products.Product;
import in.succinct.bpp.woocommerce.model.SettingAttribute;
import in.succinct.bpp.woocommerce.model.Shop;
import in.succinct.onet.core.adaptor.NetworkAdaptor.Domain;
import in.succinct.onet.core.adaptor.NetworkAdaptor.DomainCategory;
import in.succinct.onet.core.adaptor.NetworkAdaptorFactory;
import in.succinct.onet.core.api.BecknIdHelper;
import in.succinct.onet.core.api.BecknIdHelper.Entity;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ECommerceAdaptor extends CommerceAdaptor {
     public ECommerceAdaptor(Map<String, String> configuration, Subscriber subscriber) {
        super(configuration, subscriber);
    }
    
    protected Set<String> getCredentialAttributes() {
        return new HashSet<>() {{
            // Example Attributes
            add("X-Client-Id");
            add("X-Client-Secret");
            add("X-Store-Url");
            add("X-Webhook-Secret");
        }};
    }
    
    public Map<String, String> getCredentials(User user) {
        return user == null ? new HashMap<>() : user.getCredentials(true, getCredentialAttributes());
    }
    
    
    private void setErrorResponse(Request response, Exception ex){
        response.setError(new Error(){{
            if (ex instanceof BecknException) {
                setCode(((BecknException)ex).getErrorCode());
            }else{
                setCode(new SellerException.GenericBusinessError().getErrorCode());
            }
            setMessage(ex.getMessage());
        }});
    }
    
    
    @Override
    public void search(Request request, Request response) {
        Domain domain = NetworkAdaptorFactory.getInstance().getAdaptor(request.getContext().getNetworkId()).getDomains().get(request.getContext().getDomain());
        
        DomainCategory allowedDomainCategory = getProviderConfig().getDomainCategory();
        if (domain.getDomainCategory() != allowedDomainCategory) {
            setErrorResponse(response, new NoDataAvailable());
            return;
        }
        response.setMessage(new Message() {{
            setCatalog(new Catalog());
        }});
    }
    
    @Override
    public void confirm(Request request, Request response) {
        User user = getUser(request);
        if (user == null || ObjectUtil.isVoid(getCredentials(user))) {
            throw new RuntimeException("User Not Authorized");
        }
        ECommerceSDK helper  = new ECommerceSDK(user.getCredentials(true,getCredentialAttributes()));
        ECommerceOrder appResponse = save(request,true);
        if (response.getMessage() == null){
            response.setMessage(new Message());
        }
        response.getMessage().setOrder(convert(helper,appResponse));
    }
    
    @Override
    public void track(Request request, Request response) {
        Order order = request.getMessage().getOrder();
        User user = getUser(request);
        
        ECommerceSDK helper = new ECommerceSDK(user.getCredentials(true,getCredentialAttributes()));
        ECommerceOrder latest = findShopifyOrder(helper,order);
        Order current = convert(helper,latest);
        if (response.getMessage() == null){
            response.setMessage(new Message());
        }
        response.getMessage().setOrder(current);
        
    }
    
    @Override
    public void cancel(Request request, Request response) {
        throw new UnsupportedOperationException();
    }
    
    
    
    
    @Override
    public void select(Request request, Request response) {
        throw new UnsupportedOperationException();
    }
    
    
    @Override
    public void init(Request request, Request response) {
        response.update(request);
        response.getExtendedAttributes().setInner(request.getExtendedAttributes().getInner());
    }
    
    
    @Override
    public void update(Request updateRequest, Request response) {
        String target = updateRequest.getMessage().getUpdateTarget();
        String action = updateRequest.getContext().getAction();
        LocalOrderSynchronizer localOrderSynchronizer  = LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber());
        
        Order order = localOrderSynchronizer.getLastKnownOrder(updateRequest.getContext().getTransactionId(),true);
        
        if (ObjectUtil.equals(target,"invoices")){
            for (Invoice invoice :updateRequest.getMessage().getOrder().getInvoices()){
                Invoice persistedInvoice = order.getInvoices().get(invoice.getId());
                if (persistedInvoice.getUnpaidAmount().doubleValue() > 0) {
                    persistedInvoice.setPaymentTransactions(invoice.getPaymentTransactions());
                }
            }
            localOrderSynchronizer.updateOrderStatus(order);
        }else {
            throw new RuntimeException("Action %s not supported for target %s".formatted(action,target));
        }
        if (response.getMessage() == null){
            response.setMessage(new Message());
        }
        response.getMessage().setOrder(order);
        //We are not updating Ecommerce App. !!
    }
    
    @Override
    public void status(Request request, Request response) {
        track(request,response);
    }
    
    
    @Override
    public void _search(String providerId, Request reply) {
        super._search(providerId, reply);
        reply.setMessage(new Message() {{
            setCatalog(new Catalog() {{
                setDescriptor(new Descriptor(){{
                    setCode(Config.instance().getHostName());
                    setName(getCode());
                    setShortDesc(getCode());
                    setLongDesc(getCode());
                }});
                
                setProviders(new Providers());
                Select select = new Select().from(User.class);
                if (!ObjectUtil.isVoid(providerId)) {
                    select.where(new Expression(select.getPool(), "PROVIDER_ID", Operator.EQ, providerId));
                } else {
                    select.where(new Expression(select.getPool(), "PROVIDER_ID", Operator.NE));
                }
                for (User u : select.execute(User.class)) {
                    getProviders().add(getProvider(u));
                }
            }});
        }});
    }
    
    public static final String PREPAID = "PRE-PAID";
    public static final String POST_DELIVERY = "POST-DELIVERY";
    
    private Provider getProvider(User u) {
        Provider provider = new Provider();
        provider.setId(u.getProviderId());
        
        List<com.venky.swf.plugins.collab.db.model.participants.admin.Company> companies = u.getCompanies();
        if (!companies.isEmpty()) {
            Company company = companies.get(0).getRawRecord().getAsProxy(Company.class);
            provider.setDescriptor(new Descriptor() {{
                setName(company.getName());
                setLongDesc(company.getName());
                setShortDesc(company.getName());
                if (!ObjectUtil.isVoid(company.getTaxIdentificationNumber())) {
                    setCode(company.getTaxIdentificationNumber());
                }
            }});
            provider.setLocations(getLocations(company));
            provider.setFulfillments(getFulfillments(company));
            Facility facility = company.getFacilities().get(0);
            for (Fulfillment fulfillment : provider.getFulfillments()) {
                fulfillment.setContact(new Contact(){{
                    setEmail(facility.getEmail());
                    setPhone(facility.getPhoneNumber());
                }});
                fulfillment.setProviderId(provider.getId());
                fulfillment.setProviderName(provider.getDescriptor().getName());
                fulfillment.setTracking(false);
                FulfillmentStop start = fulfillment._getStart();
                if (start == null && !provider.getLocations().isEmpty()){
                    start = new FulfillmentStop();
                    Location providerLocation = new Location();
                    providerLocation.update(provider.getLocations().get(0));
                    start.setLocation(providerLocation);// Dont carry same reference.!!
                    fulfillment._setStart(start);
                }
                if (start != null) {
                    start.setContact(fulfillment.getContact());
                    start.setPerson(new Person(){{
                        setName(u.getLongName());
                    }});
                }
            }
            
            provider.setPayments(getPayments(company));
            provider.setCategories(new Categories());
            
            provider.setItems(getItems(provider,new ECommerceSDK(u.getCredentials(true,getCredentialAttributes()))));
            
            TypeConverter<Boolean> converter =  company.getReflector().getJdbcTypeHelper().getTypeRef(boolean.class).getTypeConverter();
            provider.setTag("kyc","tax_id",company.getTaxIdentificationNumber());
            provider.setTag("kyc","owner_name",!ObjectUtil.isVoid(company.getCompanyOwnerName()) ?
                    company.getCompanyOwnerName() :
                    !ObjectUtil.isVoid(company.getAccountHolderName()) ?
                            company.getAccountHolderName() :
                            u.getName());
            provider.setTag("kyc","registration_id",company.getRegistrationNumber());
            provider.setTag("kyc","complete",converter.toString(ObjectUtil.equals(company.getVerificationStatus(), "APPROVED")));
            provider.setTag("kyc","ok",converter.toString(company.isKycOK()));
            
            provider.setTag("network","environment",company.getNetworkEnvironment());
            provider.setTag("network","suspended",converter.toString(company.isSuspended()));
            
        }
        return provider;
    }
    
    private Payments getPayments(Company company) {
        return new Payments() {{
            if (company.isPrepaidSupported()) {
                Payment prepaid = new Payment();
                prepaid.setCollectedBy(CollectedBy.BPP);
                prepaid.setInvoiceEvent(FulfillmentStatus.Created);
                prepaid.setId(PREPAID);
                if (!ObjectUtil.isVoid(company.getVirtualPaymentAddress())) {
                    prepaid.setParams(new Params() {{
                        setVirtualPaymentAddress(company.getVirtualPaymentAddress());
                        setBankAccountName(company.getAccountHolderName());
                        setMcc(company.getMerchantCategoryCode());
                        setCurrency(company.getFacilities().get(0).getCountry().getWorldCurrency().getCode());
                    }});
                }
                add(prepaid);
            }
            if (company.isPostDeliverySupported()) {
                Payment cod = new Payment();
                cod.setCollectedBy(CollectedBy.BPP);
                cod.setInvoiceEvent(FulfillmentStatus.Completed);
                cod.setId(POST_DELIVERY);
                if (!ObjectUtil.isVoid(company.getVirtualPaymentAddress())) {
                    cod.setParams(new Params() {{
                        setVirtualPaymentAddress(company.getVirtualPaymentAddress());
                        setBankAccountName(company.getAccountHolderName());
                        setMcc(company.getMerchantCategoryCode());
                        setCurrency(company.getFacilities().get(0).getCountry().getWorldCurrency().getCode());
                    }});
                }
                add(cod);
            }
        }};
    }
    
    private Locations getLocations(Company company) {
        Locations locations = new Locations();
        for (Facility facility : company.getFacilities()) {
            locations.add(toLocation(company, facility));
        }
        return locations;
    }
    
    private Location toLocation(Company company, Facility facility) {
        return new Location() {{
            setAddress(ECommerceAdaptor.this.getAddress(facility.getName(), facility));
            setPinCode(getAddress().getPinCode());
            setGps(new GeoCoordinate(facility));
            setCountry(new in.succinct.beckn.Country() {{
                setName(facility.getCountry().getName());
                setCode(facility.getCountry().getIsoCode());
            }});
            setCity(new City() {{
                setName(facility.getCity().getName());
                setCode(facility.getCity().getCode());
            }});
            setState(new State() {{
                setName(facility.getState().getName());
                setCode(facility.getState().getCode());
            }});
            setDescriptor(new Descriptor() {{
                setName(facility.getName());
            }});
            setId(BecknIdHelper.getBecknId(StringUtil.valueOf(facility.getId()), getSubscriber(), Entity.provider_location));
            if (company.isHomeDeliverySupported() && company.getMaxDistance() >= 0) {
                setCircle(new Circle() {{
                    setGps(new GeoCoordinate(facility));
                    setRadius(new Scalar() {{
                        setValue(Database.getJdbcTypeHelper("").getTypeRef(double.class).getTypeConverter().valueOf(company.getMaxDistance()));
                        setUnit("km");
                    }});
                }});
            }
        }};
    }
    
    private Address getAddress(String name, com.venky.swf.plugins.collab.db.model.participants.admin.Address facility) {
        Address address = new Address();
        address.setState(facility.getState().getName());
        address.setName(name);
        address.setPinCode(facility.getPinCode().getPinCode());
        address.setCity(facility.getCity().getName());
        address.setState(facility.getState().getName());
        address.setCountry(facility.getCountry().getName());
        address._setAddressLines(facility.getAddressLine1(), facility.getAddressLine2(), facility.getAddressLine3(), facility.getAddressLine4());
        return address;
    }
    
    private Fulfillments getFulfillments(Company company) {
        return new Fulfillments() {{
            if (company.isHomeDeliverySupported()) {
                Fulfillment home_delivery = new Fulfillment();
                home_delivery.setId(RetailFulfillmentType.home_delivery.toString());
                home_delivery.setType(RetailFulfillmentType.home_delivery.toString());
                
                if (company.getMaxDistance() > 0) {
                    home_delivery.setTag("APPLICABILITY", "MAX_DISTANCE", company.getMaxDistance());
                    if (!ObjectUtil.isVoid(company.getNotesOnDeliveryCharges())) {
                        home_delivery.setTag("DELIVERY_CHARGES", "NOTES", company.getNotesOnDeliveryCharges());
                    }
                }
                add(home_delivery);
            }
            if (company.isStorePickupSupported()) {
                Fulfillment store_pickUp = new Fulfillment();
                store_pickUp.setId(RetailFulfillmentType.store_pickup.toString());
                store_pickUp.setType(RetailFulfillmentType.store_pickup.toString());
                add(store_pickUp);
            }
        }};
    }
    
    private Items getItems(Provider provider, ECommerceSDK helper) {
        Items items = new Items();
        Domain domain = null;
        
        for (Domain d : NetworkAdaptorFactory.getInstance().getAdaptor().getDomains()){
            if (ObjectUtil.equals(d.getDomainCategory(),DomainCategory.BUY_MOVABLE_GOODS)){
                domain = d;
                break;
            }
        }
        
        
        Products products = helper.getProducts();
        
        Map<String, Double> taxRateMap = getTaxRateMap();
        
        for (Product product : products) {
            Item item = createItem(helper,product,provider);
            item.setTag("DOMAIN","CATEGORY",DomainCategory.BUY_MOVABLE_GOODS.name());
            if (!ObjectUtil.isVoid(domain)){
                item.setTag ("DOMAIN","ID",domain.getId());
            }
            items.add(item);
        }
        return items;
    }
    private  Item createItem(ECommerceSDK helper, Products.Product product , Provider provider) {
        Shop store  = helper.getShop();
        
        Item item = new Item();
        product.getAttributes().forEach(a->{
            item.setTag("general_attributes",a.getName(),a.getOptions().get(a.getPosition()));
        });
        
        item.setId(BecknIdHelper.getBecknId(StringUtil.valueOf(product.getId()),
                this.getSubscriber(), Entity.item));
        item.setDescriptor(new Descriptor(){{
            setName(product.getName());
            setCode(product.getSku());
            setShortDesc(product.getShortDescription());
            setLongDesc(product.getDescription());
            
            // Images
            setImages(new Images());
            Products.ProductImages images = product.getImages();
            images.forEach(image -> {
                getImages().add(image.getSrc());
            });
            setSymbol(getImages().get(0).getUrl());
            
        }});
        
        // Basic Details
        
        // Category
        item.setCategoryId(getProviderConfig().getCategory().getId());
        item.setCategoryIds(new BecknStrings());
        item.getCategoryIds().add(item.getCategoryId());
        product.getTags().forEach(tag -> {
            String token = tag.getName().toUpperCase();
            if (provider.getCategories().get(token) == null){
                Category category = provider.getObjectCreator().create(Category.class);
                category.setId(token);
                category.setDescriptor(provider.getObjectCreator().create(Descriptor.class));
                Descriptor descriptor = category.getDescriptor();
                descriptor.setName(token);
                descriptor.setCode(token);
                descriptor.setShortDesc(token);
                descriptor.setLongDesc(token);
                provider.getCategories().add(category);
            }
        });

        
        // Price
        Price price = new Price();
        item.setPrice(price);
        price.setMaximumValue(price.getListedValue());
        price.setListedValue(product.getRegularPrice());
        price.setValue(product.getPrice());
        price.setCurrency(helper.getShop().getGeneralSetting().getAttribute(SettingAttribute.AttributeKey.CURRENCY).getValue());
        
        // Payment
        item.setPaymentIds(new BecknStrings());
        for (Payment payment : provider.getPayments()) {
            item.getPaymentIds().add(payment.getId()); //Only allow By BAP , ON_ORDER
        }
        item.setFulfillmentIds(new BecknStrings());
        for (Fulfillment fulfillment : provider.getFulfillments()) {
            item.getFulfillmentIds().add(fulfillment.getId());
        }
        item.setLocationIds(new BecknStrings());
        for (Location location : provider.getLocations()) {
            item.getLocationIds().add(location.getId());
        }
        
        
        // Shipping & Return
        item.setReturnable(false);
        item.setCancellable(true);
        

        /*
        double factor = store.isTaxesIncluded() ? (taxRate / (1 + taxRate)) : taxRate;
                    
                    unitTax.setValue(factor * item.getPrice().getValue());
         */
        item.setTaxRate(0);
        item.setTax(new Price());
        item.getTax().setValue(0.0);
        item.getTax().setCurrency(price.getCurrency());
        item.setCountryOfOrigin(Country.findByISO(provider.getLocations().get(0).getAddress().getCountry()).getIsoCode());
        item.setTag("general_attributes", "product_id", item.getId());
        return item;
        
    }
    private static final TypeConverter<Double> doubleTypeConverter = Database.getJdbcTypeHelper("").getTypeRef(double.class).getTypeConverter();
    
    public Map<String, Double> getTaxRateMap() {
        return new Cache<>(0, 0) {
            @Override
            protected Double getValue(String taxClass) {
                if (!ObjectUtil.isVoid(taxClass)) {
                    AssetCode assetCode = AssetCode.findLike(taxClass, AssetCode.class);
                    if (assetCode != null) {
                        return assetCode.getReflector().getJdbcTypeHelper().getTypeRef(double.class).getTypeConverter().valueOf(assetCode.getGstPct());
                    }
                }
                return 0.0D;
            }
        };
    }
    
    //From shopify to beckn
    private  String getBecknTransactionId( ECommerceOrder draftOrder) {
        for (ECommerceOrder.MetaData metaData : draftOrder.getMetaDataArray()) {
            if (metaData.getKey().equals("context.transaction_id")) {
                return metaData.getValue();
            }
        }
        return null;
    }
    public Order convert(ECommerceSDK helper, ECommerceOrder eCommerceOrder){
        
        String transactionId = getBecknTransactionId(eCommerceOrder);
        
        LocalOrderSynchronizer localOrderSynchronizer = LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber());
        
        Order lastKnownOrder = localOrderSynchronizer.getLastKnownOrder(transactionId, true);
        localOrderSynchronizer.setLocalOrderId(transactionId, eCommerceOrder.getId());
        
        Order order = new Order();
        order.update(lastKnownOrder);
        
        Date updatedAt = eCommerceOrder.getUpdatedDateGmt();
        
        localOrderSynchronizer.setFulfillmentStatusReachedAt(transactionId, eCommerceOrder.getFulfillmentStatus(), updatedAt, false);
        /*
        if (!localOrderSynchronizer.hasOrderReached(transactionId, eCommerceOrder.getStatus())) {
            order.setStatus(eCommerceOrder.getStatus());
        }
        localOrderSynchronizer.setStatusReachedAt(transactionId, eCommerceOrder.getStatus(), updatedAt, false);
        */
        order.setUpdatedAt(DateUtils.max(updatedAt, order.getUpdatedAt()));
        
        
        if (order.getStatus() == Status.Cancelled) {
            if (order.getCancellation() == null) {
                order.setCancellation(new Cancellation());
                order.getCancellation().setCancelledBy(CancelledBy.PROVIDER);
                order.getCancellation().setSelectedReason(new Option());
                order.getCancellation().getSelectedReason().setDescriptor(new Descriptor());
                Descriptor descriptor = order.getCancellation().getSelectedReason().getDescriptor();
                descriptor.setLongDesc("Item not Available");
                descriptor.setCode(CancellationReasonCode.convertor.toString(CancellationReasonCode.ITEM_NOT_AVAILABLE));
            }
        }
        ensureDeliveryItem(order,eCommerceOrder);
        
        setPayment(order, eCommerceOrder);
        
        Fulfillment fulfillment = order.getFulfillment();
        fulfillment.setFulfillmentStatus(eCommerceOrder.getFulfillmentStatus());
        
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(eCommerceOrder.getCreateDateGmt());
        }
        
        return order;
    }
    
    private void ensureDeliveryItem(Order order, ECommerceOrder eCommerceOrder) {
        Item deliveryItem= null;
        for (Item item : order.getItems()) {
            if (item.getDescriptor().getName().matches("^DELIVERY")){
                deliveryItem = item;
            }
        }
        Item tmpdeliveryItem = new Item(){{
            
            setId("DELIVERY");
            setDescriptor(new Descriptor(){{
                setCode("DELIVERY");
                setName("DELIVERY");
            }});
            setPrice(new Price(){{
                setValue(0.0);
                for (ShippingLine shippingLine  : eCommerceOrder.getShippingLines()){
                    setValue( getValue() + shippingLine.getPrice());
                }
                
            }});
            setItemQuantity(new ItemQuantity(){{
                setSelected(new Quantity(){{
                    setCount(1);
                }}) ;
            }});
            
        }};
        if (deliveryItem == null) {
            deliveryItem = tmpdeliveryItem;
            order.getItems().add(deliveryItem);
        }else {
            deliveryItem.update(tmpdeliveryItem);
        }
    }
    
    
    
    private void setPayment(Order order, ECommerceOrder eCommerceOrder) {
        Payment payment = order.getPayments().get(0);
        payment.getParams().setAmount(eCommerceOrder.getTotalPrice());
        
        LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber()).updateOrderStatus(order); // Ensure invoices are created.
        
        Invoices invoices = order.getInvoices();
        boolean paymentConfirmed = eCommerceOrder.isPaid();
        
        Map<String, PaymentTransaction> paymentTransactionMap = new HashMap<>();
        
        paymentTransactionMap.put(eCommerceOrder.getId(),new PaymentTransaction() {{
            setAmount(eCommerceOrder.getTotalPrice());
            setDate(eCommerceOrder.getPaidDateGmt());
            setTransactionId(eCommerceOrder.getId());//payment transaction id missing in woocommerce.
            setPaymentStatus(PaymentStatus.COMPLETE);
        }});
        
        
        
        List<Invoice> unpaidInvoices = new ArrayList<>();
        for (Invoice invoice : invoices) {
            boolean paid = invoice.getUnpaidAmount().intValue() == 0;
            if (!paid){
                unpaidInvoices.add(invoice);
            }else {
                for (PaymentTransaction paymentTransaction : invoice.getPaymentTransactions()) {
                    if (paymentTransaction.getPaymentStatus() == null) {
                        paymentTransaction.setPaymentStatus(PaymentStatus.PAID);
                    }else if (paymentTransaction.getPaymentStatus() != PaymentStatus.COMPLETE){
                        if (paymentTransactionMap.containsKey(paymentTransaction.getTransactionId())) {
                            paymentTransaction.setPaymentStatus(PaymentStatus.COMPLETE);
                            paymentTransactionMap.remove(paymentTransaction.getTransactionId());
                        } else if (paymentConfirmed) {
                            paymentTransaction.setPaymentStatus(PaymentStatus.COMPLETE);
                        }
                    }
                }
            }
        }
        if (unpaidInvoices.size() == 1){
            for (PaymentTransaction value : paymentTransactionMap.values()) {
                unpaidInvoices.get(0).getPaymentTransactions().add(value);
            }
        }else if (unpaidInvoices.size() > 1){
            throw new RuntimeException("Cannot have multiple unpaid invoices!");
        }
        if (paymentConfirmed){
            for (Payment orderPayment : order.getPayments()) {
                orderPayment.setStatus(PaymentStatus.COMPLETE);
            }
        }
    }
    public ECommerceSDK getHelper(User user){
        return new ECommerceSDK(user.getCredentials(true,getCredentialAttributes()));
    }
    
    public ECommerceOrder findShopifyOrder(ECommerceSDK helper, Order order){
        
        String shopifyOrderId = LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber()).getLocalOrderId(order);
        String draftOrderId = LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber()).getLocalDraftOrderId(order);
        if (shopifyOrderId != null){
            return helper.findShopifyOrder(shopifyOrderId, false);
        }else if (draftOrderId != null){
            return helper.findShopifyOrder(draftOrderId,true);
        }
        return null;
    }
    //From beckn to shopify
    public ECommerceOrder save(Request request, boolean create){
        ECommerceOrder eCommerceOrder = new ECommerceOrder();
        Order bo = request.getMessage().getOrder();
        Provider provider = bo.getProvider();
        
        User user = getUser(request);
        ECommerceSDK helper = new ECommerceSDK(user.getCredentials(true,getCredentialAttributes()));
        
        Location storeLocation = bo._getProviderLocation();
        
        eCommerceOrder.setId(LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber()).getLocalOrderId(request.getContext().getTransactionId()));
        
        eCommerceOrder.setCurrency(provider.getPayments().get(0).getParams().getCurrency());
        eCommerceOrder.setSourceName("beckn");
        eCommerceOrder.setName("beckn-" + request.getContext().getTransactionId());
        eCommerceOrder.setMetaDataArray(new MetaDataArray());
        
        for (String key : new String[] { AttributeKey.bapId.getKey(), AttributeKey.bapUri.getKey(),
                AttributeKey.domain.getKey(), AttributeKey.transactionId.getKey(), AttributeKey.city.getKey(),
                AttributeKey.country.getKey(),
                AttributeKey.coreVersion.getKey(), AttributeKey.ttl.getKey()}) {
            ECommerceOrder.MetaData meta = new MetaData();
            meta.setKey(String.format("context.%s", key));
            meta.setValue(request.getContext().get(key));
            eCommerceOrder.getMetaDataArray().add(meta);
        }
        
        
        if (!ObjectUtil.isVoid(eCommerceOrder.getId()) && create) {
            helper.delete(eCommerceOrder);
        }
        
        setShipping(bo, eCommerceOrder);
        
        setBilling(bo.getBilling(), eCommerceOrder);
        Bucket totalPrice = new Bucket();
        
        if (bo.getItems() != null) {
            bo.getItems().forEach(boItem -> {
                LineItem lineItem  = addItem(eCommerceOrder, boItem);
                totalPrice.increment(boItem.getPrice().getValue() * lineItem.getQuantity());
            });
        }

        LocalOrderSynchronizerFactory.getInstance().getLocalOrderSynchronizer(getSubscriber()).sync(request.getContext().getTransactionId(), bo);
        if (!eCommerceOrder.getLineItems().isEmpty()) {
            saveDraftOrder(helper,eCommerceOrder);
        }
        return  eCommerceOrder;
    }
    
    
    private LineItem addItem(ECommerceOrder draftOrder, Item item) {
        LineItems line_items = draftOrder.getLineItems();
        if (line_items == null) {
            line_items = new LineItems();
            draftOrder.setLineItems(line_items);
        }
        LineItem lineItem = new LineItem();
        
        JSONObject inspectQuantity = (JSONObject) item.getInner().get("quantity");
        if (inspectQuantity.containsKey("count")) {
            lineItem.setQuantity(item.getQuantity().getCount());
        } else if (inspectQuantity.containsKey("selected")) {
            lineItem.setQuantity(item.getItemQuantity().getSelected().getCount());
        }
        
        lineItem.setProductId(item.getTags().get("product_id").getValue());
        line_items.add(lineItem);
        return lineItem;
    }
    
    private boolean isTaxIncludedInPrice() {
        return true;
    }
    private void saveDraftOrder(ECommerceSDK helper, ECommerceOrder order) {
        JSONObject jsOrder = helper.post("/orders", order.getInner());
        order.setPayload(jsOrder.toString());
    }
    
    public void setShipping(Order bo, ECommerceOrder target) {
        Fulfillment source = bo.getFulfillment();
        
        if (source == null) {
            return;
        }
        ECommerceOrder.OrderShipping shipping = new ECommerceOrder.OrderShipping();
        target.setOrderShipping(shipping);
        
        in.succinct.beckn.User user = source.getCustomer();
        
        Address address = source._getEnd().getLocation().getAddress();
        if (address != null) {
            if (user == null ) {
                user = new in.succinct.beckn.User();
            }
            if (user.getPerson() == null) {
                user.setPerson(new Person());
            }
            if (ObjectUtil.isVoid(user.getPerson().getName())) {
                user.getPerson().setName(address.getName());
            }
        }
        
        if (user != null && !ObjectUtil.isVoid(user.getPerson().getName())) {
            String[] parts = user.getPerson().getName().split(" ");
            shipping.setFirstName(parts[0]);
            shipping.setLastName(user.getPerson().getName().substring(parts[0].length()));
        }
        
        
        Contact contact = source._getEnd().getContact();
        GeoCoordinate gps = source._getEnd().getLocation().getGps();
        
        if (address != null) {
            if (address.getCountry() == null) {
                address.setCountry(source._getStart().getLocation().getCountry().getName());
            }
            if (address.getState() == null ){
                address.setState(source._getStart().getLocation().getState().getName());
            }
            if (address.getCity() == null){
                address.setCity(source._getStart().getLocation().getCity().getName());
            }
            Country country = Country.findByName(address.getCountry());
            com.venky.swf.plugins.collab.db.model.config.State state = com.venky.swf.plugins.collab.db.model.config.State.findByCountryAndName(country.getId(), address.getState());
            
            String[] lines = address._getAddressLines(2);
            shipping.setAddress1(lines[0]);
            shipping.setAddress2(lines[1]);
            
            shipping.setCity(address.getCity());
            shipping.setStateCode(state.getCode());
            shipping.setPostcode(address.getAreaCode());
            shipping.setCountryCode(country.getIsoCode2());
        }
        //shipping.put("email",contact.getEmail());
        shipping.setPhone(contact.getPhone());
        target.setPhone(contact.getPhone());
        target.setEmail(contact.getEmail());
        
        
        if (bo.getBilling() == null) {
            bo.setBilling(new Billing());
        }
        if (bo.getBilling().getAddress() == null) {
            bo.getBilling().setAddress(new Address());
        }
        bo.getBilling().getAddress().update(address,false);
        if (bo.getBilling().getName() == null) {
            bo.getBilling().setName(bo.getBilling().getAddress().getName());
        }
        
    }
    
    public void setBilling(Billing source, ECommerceOrder target) {
        
        if (source == null) {
            return;
        }
        
        
        ECommerceOrder.OrderBilling billing = new ECommerceOrder.OrderBilling();
        target.setOrderBilling(billing);
        
        String[] parts = source.getName().split(" ");
        billing.setFirstName(parts[0]);
        billing.setLastName(source.getName().substring(parts[0].length()));
        if (source.getAddress() != null) {
            billing.setAddress1(source.getAddress().getDoor() + "," + source.getAddress().getBuilding());
            billing.setAddress2(source.getAddress().getStreet() + "," + source.getAddress().getLocality());
            
            Country country = Country.findByName(source.getAddress().getCountry());
            com.venky.swf.plugins.collab.db.model.config.State state = com.venky.swf.plugins.collab.db.model.config.State.findByCountryAndName(country.getId(), source.getAddress().getState());
            com.venky.swf.plugins.collab.db.model.config.City city =  com.venky.swf.plugins.collab.db.model.config.City.findByStateAndName(state.getId(), source.getAddress().getCity());
            
            billing.setCity(city.getName());
            billing.setStateCode(city.getState().getCode());
            billing.setCountryCode(city.getState().getCountry().getIsoCode2());
            billing.setPostcode(source.getAddress().getAreaCode());
        }
        
        
        billing.setPhone(source.getPhone());
        
    }
}
