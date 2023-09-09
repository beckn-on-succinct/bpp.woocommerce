package in.succinct.bpp.woocommerce.adaptor;

import com.venky.swf.plugins.beckn.messaging.Subscriber;
import in.succinct.beckn.*;
import in.succinct.bpp.core.adaptor.TimeSensitiveCache;
import in.succinct.bpp.core.adaptor.api.BecknIdHelper;
import in.succinct.bpp.core.adaptor.fulfillment.FulfillmentStatusAdaptor;
import in.succinct.bpp.search.adaptor.SearchAdaptor;
import in.succinct.bpp.core.adaptor.api.BecknIdHelper.Entity;
import in.succinct.bpp.woocommerce.model.*;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import java.time.Duration;
import java.util.*;
import java.util.List;

public class ECommerceAdaptor extends SearchAdaptor {

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
            GeneralSetting generalSetting = getShop().getGeneralSetting();
            Locations locations = new Locations();
            ArrayList<Countries> continents = getAllCountries(getAllContinents());
            Tuple<String, String> countryState = splitCountryState(
                    generalSetting.getAttribute(SettingAttribute.AttributeKey.COUNTRY).getValue());
            Countries.Country country = getCountry(continents, countryState.first);
            States.State state = country.getState().get(countryState.second);
            String address1 = generalSetting.getAttribute(SettingAttribute.AttributeKey.ADDRESS_1).getValue();
            String address2 = generalSetting.getAttribute(SettingAttribute.AttributeKey.ADDRESS_2).getValue();
            String city = generalSetting.getAttribute(SettingAttribute.AttributeKey.CITY).getValue();
            String pincode = generalSetting.getAttribute(SettingAttribute.AttributeKey.CITY).getValue();

            Location location = new Location();
            location.setId(BecknIdHelper.getBecknId(this.getSubscriber().getSubscriberId(),
                    this.getSubscriber(), Entity.provider_location));
            location.setAddress(new Address());
            location.getAddress().setStreet(address1);
            location.getAddress().setLocality(address2);
            location.getAddress().setCity(city);
            location.getAddress().setPinCode(pincode);
            location.getAddress().setCountry(country.getAttribute(Countries.AttributeKey.NAME));
            location.getAddress().setState(state.getName());
            return locations;
        });
    }

    @Override
    public Items getItems() {
        return cache.get(Items.class, () -> {
            Items items = new Items();
            Products products = getInStockPublicProducts();

            products.forEach(product -> {
                items.add(createItem(items, product));

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
    public Order initializeDraftOrder(Request request) {
        return null;
    }

    @Override
    public Order confirmDraftOrder(Order order) {
        return null;
    }

    @Override
    public Order getStatus(Order order) {
        return null;
    }

    @Override
    public Order cancel(Order order) {
        return null;
    }

    @Override
    public String getTrackingUrl(Order order) {
        return null;
    }

    @Override
    public List<FulfillmentStatusAdaptor.FulfillmentStatusAudit> getStatusAudit(Order order) {
        return null;
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

    private JSONObject fetchAttributeFromAPI(SettingAttribute.AttributeKey attributeKey) {
        return helper.get(new SettingAttribute(attributeKey).getApiEndpoint(), new JSONObject());
    }

    private Tuple<String, String> splitCountryState(String value) {
        if (value != null && value.contains(":")) {
            String[] parts = value.split(":");
            return new Tuple<>(parts[0], parts[1]);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private @NotNull ArrayList<Continents.Continent> getAllContinents() {
        ArrayList<Continents.Continent> continentsList = new ArrayList<>();

        JSONArray continents = helper.get(new Continents().getApiEndpoint(), new JSONObject());
        if (continents == null || continents.isEmpty())
            return continentsList;

        continents.forEach(o -> {
            JSONObject continent = (JSONObject) o;
            continentsList.add(new Continents.Continent(continent));
        });

        return continentsList;
    }

    private ArrayList<Countries> getAllCountries(ArrayList<Continents.Continent> continents) {
        ArrayList<Countries> countriesList = new ArrayList<>();

        continents.forEach(continent -> {
            countriesList.add(continent.getCountries());
        });
        return countriesList;
    }

    private Countries.Country getCountry(ArrayList<Countries> continentCountries, String code) {
        return continentCountries.stream()
                .map(countries -> countries.getCountry(code)) // map each Country to the desired Country
                .filter(Objects::nonNull) // filter out any null results
                .findFirst() // get the first matching country
                .orElse(null); // return null if no match was found
    }

    private String generateQueryURL(@NotNull String endpoint, Map<String, String> queryParams) {
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
            queryParams.put(Products.AttributeKey.status.getKey(), "publish");
            queryParams.put(Products.AttributeKey.stock_status.getKey(), "instock");
            queryParams.put(Products.AttributeKey.per_page.getKey(), String.valueOf(per_page));

            while (true) {
                queryParams.put(Products.AttributeKey.page.getKey(), String.valueOf(page));
                String finalUrl = generateQueryURL("products", queryParams);
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

    private Item createItem(Items items, Products.Product product) {
        Item item = new Item();

        item.setId(BecknIdHelper.getBecknId(String.valueOf(product.get(Products.AttributeKey.id.getKey())),
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
        descriptor.setSymbol(descriptor.getImages().get(0));

        // Category
        item.setCategoryId(getProviderConfig().getCategory().getId());
        item.setCategoryIds(new BecknStrings());
        item.getCategoryIds().add(item.getCategoryId());
        item.setTags(new Tags());
        product.getTags().forEach(tag -> {
            item.getTags().set(tag.getName(), "true");
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
        item.setTimeToShip(getProviderConfig().getTurnAroundTime());
        item.setAvailableOnCod(getProviderConfig().isCodSupported());
        item.setContactDetailsConsumerCare(getProviderConfig().getLocation().getAddress().flatten() + " "
                + getProviderConfig().getSupportContact().flatten());
        item.setFulfillmentIds(new BecknStrings());

        // Fulfillment
        for (Fulfillment fulfillment : getFulfillments()) {
            item.getFulfillmentIds().add(fulfillment.getId());
        }



        return item;

    }

}
