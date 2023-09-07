package in.succinct.bpp.woocommerce.adaptor;

import com.venky.swf.plugins.beckn.messaging.Subscriber;
import in.succinct.beckn.*;
import in.succinct.bpp.core.adaptor.TimeSensitiveCache;
import in.succinct.bpp.core.adaptor.api.BecknIdHelper;
import in.succinct.bpp.core.adaptor.fulfillment.FulfillmentStatusAdaptor;
import in.succinct.bpp.search.adaptor.SearchAdaptor;
import in.succinct.bpp.woocommerce.model.*;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ECommerceAdaptor extends SearchAdaptor {

    final ApiHelper helper;
    final TimeSensitiveCache cache = new TimeSensitiveCache(Duration.ofDays(1));



    @Override
    public void clearCache(){
        cache.clear();
    }


    public ECommerceAdaptor(Map<String, String> configuration, Subscriber subscriber) {
        super(configuration, subscriber);
        this.helper = new ApiHelper(this);
        getProviderConfig().setLocation(getProviderLocations().get(0));
    }

    @Override
    public Locations getProviderLocations() {
        return cache.get(Locations.class, () -> {
            GeneralSetting generalSetting = getShop().getGeneralSetting();
            Locations locations = new Locations();
            ArrayList<Countries>  continents = getAllCountries(getAllContinents());
            Tuple<String, String> countryState = splitCountryState(generalSetting.getAttribute( SettingAttribute.AttributeKey.COUNTRY).getValue());
            Countries.Country country = getCountry(continents, countryState.first);
            States.State state = country.getState().get(countryState.second);
            String address1 = generalSetting.getAttribute( SettingAttribute.AttributeKey.ADDRESS_1).getValue();
            String address2 = generalSetting.getAttribute( SettingAttribute.AttributeKey.ADDRESS_2).getValue();
            String city = generalSetting.getAttribute( SettingAttribute.AttributeKey.CITY).getValue();
            String pincode = generalSetting.getAttribute( SettingAttribute.AttributeKey.CITY).getValue();


            Location location = new Location();
            location.setId(BecknIdHelper.getBecknId(this.getSubscriber().getSubscriberId(),
                    this.getSubscriber(), BecknIdHelper.Entity.provider_location));
            location.setAddress(new Address());
            location.getAddress().setStreet(address1);
            location.getAddress().setLocality(address2);
            location.getAddress().setCity(city);
            location.getAddress().setPinCode(pincode);
            location.getAddress().setCountry(country.getAttribute(Countries.CountryAttribute.NAME));
            location.getAddress().setState(state.getName());
            return locations;
        });
    }

    @Override
    public Items getItems() {
        return null;
    }

    @Override
    public boolean isTaxIncludedInPrice() {
        return getShop().getTaxSetting().getAttribute(SettingAttribute.AttributeKey.PRICES_INCLUDES_TAX).getValue().equals("yes");
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
            for (SettingAttribute.AttributeKey attributeKey : SettingAttribute.AttributeKey.getSettingGroupAttributes(SettingGroup.GENERAL)) {
                JSONObject response = fetchAttributeFromAPI(attributeKey);
                generalSetting.setAttribute(attributeKey, new SettingAttribute(response, attributeKey));
            }

            TaxSetting taxSetting = new TaxSetting();

            for (SettingAttribute.AttributeKey attributeKey : SettingAttribute.AttributeKey.getSettingGroupAttributes(SettingGroup.TAX)) {
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
    private @NotNull ArrayList<Continents.Continent> getAllContinents(){
        ArrayList<Continents.Continent> continentsList = new ArrayList<>();

        JSONArray continents =  helper.get(new Continents().getApiEndpoint(), new JSONObject());
        if (continents == null || continents.isEmpty()) return continentsList;

        continents.forEach(o -> {
            JSONObject continent = (JSONObject) o;
            continentsList.add(new Continents.Continent(continent));
        });

        return continentsList;
    }

    private ArrayList<Countries> getAllCountries(ArrayList<Continents.Continent> continents){
        ArrayList<Countries> countriesList = new ArrayList<>();

        continents.forEach(continent -> {
             countriesList.add(continent.getCountries());
        });
        return countriesList;
    }

    private Countries.Country getCountry(ArrayList<Countries> continentCountries, String code){
        return continentCountries.stream()
                .map(countries -> countries.getCountry(code)) // map each Country to the desired Country
                .filter(Objects::nonNull) // filter out any null results
                .findFirst() // get the first matching country
                .orElse(null); // return null if no match was found
    }



}
