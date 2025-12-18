package in.succinct.bpp.woocommerce.adaptor;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import in.succinct.bpp.core.adaptor.TimeSensitiveCache;
import in.succinct.bpp.woocommerce.model.AttributeKey;
import in.succinct.bpp.woocommerce.model.GeneralSetting;
import in.succinct.bpp.woocommerce.model.Products;
import in.succinct.bpp.woocommerce.model.SettingAttribute;
import in.succinct.bpp.woocommerce.model.SettingGroup;
import in.succinct.bpp.woocommerce.model.Shop;
import in.succinct.bpp.woocommerce.model.TaxSetting;
import in.succinct.bpp.woocommerce.model.ECommerceOrder;
import in.succinct.json.JSONObjectWrapper;
import org.jose4j.base64url.Base64;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ECommerceSDK {
    final Map<String,String> creds ;
    public ECommerceSDK(Map<String,String> creds){
        this.creds = creds;
    }
    
    @SuppressWarnings("unused")
    private final TypeConverter<Double> doubleTypeConverter = Database.getJdbcTypeHelper("").getTypeRef(double.class).getTypeConverter();
    @SuppressWarnings("unused")
    private final TypeConverter<Boolean> booleanTypeConverter  = Database.getJdbcTypeHelper("").getTypeRef(boolean.class).getTypeConverter();

    public <T extends JSONAware> T post(String relativeUrl, JSONObject parameter ){
        return post(relativeUrl,parameter,new IgnoreCaseMap<>());
    }
    @SuppressWarnings("unused")
    public <T extends JSONAware> T putViaPost(String relativeUrl, JSONObject parameter ){
        return post(relativeUrl,parameter,new IgnoreCaseMap<>(){{
            put("X-HTTP-Method-Override","PUT");
        }});
    }
    @SuppressWarnings("unused")
    public <T extends JSONAware> T putViaGet(String relativeUrl, JSONObject parameter ){
        return get(relativeUrl,parameter,new IgnoreCaseMap<>(){{
            put("X-HTTP-Method-Override","PUT");
        }});
    }
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public <T extends JSONAware> T delete(String relativeUrl, JSONObject parameter ){
        return post(relativeUrl,parameter,new IgnoreCaseMap<>(){{
            put("X-HTTP-Method-Override","DELETE");
        }});
    }
    public <T extends JSONAware> T post(String relativeUrl, JSONObject parameter , Map<String,String> addnlHeaders){
        Call<JSONObject> call =  new Call<JSONObject>().url(getAdminApiUrl(),relativeUrl).header("content-type", MimeType.APPLICATION_JSON.toString()).header("Authorization",getAccessToken()).headers(addnlHeaders)
                .inputFormat(InputFormat.JSON).input(parameter).method(HttpMethod.POST);
        T object = call.getResponseAsJson();
        Bucket tries = new Bucket(3);
        while (call.getStatus() > 200 && call.getStatus() < 299 && tries.intValue() > 0){
            List<String> locations = call.getResponseHeaders().get("location");
            if (locations.isEmpty()){
                break;
            }
            String location = locations.get(0);
            List<String> after = call.getResponseHeaders().get("retry-after");
            if (after == null || after.isEmpty()){
                break;
            }
            long millis = Long.parseLong(after.get(0)) * 1000  + 500 ;
            try {
                Thread.currentThread().wait(millis);
            }catch (Exception ex){
                //
            }
            call = new Call<JSONObject>().url(String.format("%s.json" ,location)).header("content-type", MimeType.APPLICATION_JSON.toString()).header("Authorization",getAccessToken()).
                    inputFormat(InputFormat.FORM_FIELDS).input(new JSONObject()).method(HttpMethod.GET);
            object  = call.getResponseAsJson();
            tries.decrement();
        }
        if (call.hasErrors()){
            object =  JSONObjectWrapper.parse(new InputStreamReader(call.getErrorStream()));
        }
        return object;
    }
    public <T extends JSONAware> T graphql(String payload){
        Call<InputStream> call = new Call<InputStream>().url(getAdminApiUrl(),"graphql.json").header("content-type", "application/graphql")
                .header("Authorization",getAccessToken())
                .inputFormat(InputFormat.INPUT_STREAM)
                .input(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)))
                .method(HttpMethod.POST);

        T o = call.getResponseAsJson();

        if (o == null && call.hasErrors()){
            o = JSONObjectWrapper.parse(new InputStreamReader(call.getErrorStream()));
        }
        return o;
    }
    public <T extends JSONAware> T get(String relativeUrl, JSONObject parameter){
        return get(relativeUrl,parameter,new IgnoreCaseMap<>());
    }
    public <T extends JSONAware> T get(String relativeUrl, JSONObject parameter, Map<String,String> addnlHeaders){
        Call<JSONObject> call = new Call<JSONObject>().url(getAdminApiUrl(),relativeUrl).header("content-type", MimeType.APPLICATION_JSON.toString()).header("Authorization",getAccessToken()).
                headers(addnlHeaders)
                .input(parameter)
                .method(HttpMethod.GET);
        T o = call.getResponseAsJson();
        if (o == null && call.hasErrors()){
            o = JSONObjectWrapper.parse(new InputStreamReader(call.getErrorStream()));
        }
        return o;
    }

    public <T extends JSONAware> Page<T> getPage(String relativeUrl, JSONObject parameter){
        return getPage(relativeUrl,parameter,new IgnoreCaseMap<>());
    }
    public <T extends JSONAware> Page<T> getPage(String relativeUrl, JSONObject parameter, Map<String,String> addnlHeaders){
        Call<JSONObject> call = new Call<JSONObject>().url(getAdminApiUrl(),relativeUrl).header("content-type", MimeType.APPLICATION_JSON.toString()).header("Authorization",getAccessToken()).headers(addnlHeaders)
                .input(parameter)
                .method(HttpMethod.GET);

        Page<T> page = new Page<>();
        page.data = call.getResponseAsJson();
        List<String> links = call.getResponseHeaders().get("Link");
        if (links != null && !links.isEmpty()){
            String link = links.get(0);
            Matcher matcher = Pattern.compile("(<)(https://[^\\?]*\\?page_info=[^&]*&limit=[0-9]*)(>; rel=)(previous|next)([ ,])*").matcher(link);
            matcher.results().forEach(mr->{
                if (mr.group(4).equals("next")) {
                    page.next = mr.group(2);
                }else {
                    page.previous = mr.group(2);
                }
            });
        }


        return page;
    }
    
    public void delete(ECommerceOrder shopifyOrder) {
        if (!ObjectUtil.isVoid(shopifyOrder.getId())) {
            delete(String.format("/orders/%s.json", shopifyOrder.getId()), new JSONObject());
            shopifyOrder.rm("id");
        }
    }
    
    
    public static class Page<T extends JSONAware> {
        T data;
        String next = null;
        String previous = null;
    }
    
    
    private String getStoreUrl(){
        return creds.get("X-Store-Url");
    }
    public String getClientId(){
        return creds.get("X-Client-Id");
    }
    
    public String getClientSecret(){
        return creds.get("X-Client-Secret");
    }
    
    public String getAccessToken(){
        // Encode the client ID and consumer secret in Base64
        String credentials = String.format("%s:%s", getClientId(), getClientSecret());
        byte[] encodedCredentials = Base64.encode(credentials.getBytes(StandardCharsets.UTF_8)).getBytes();
        // Format the authentication string with the encoded credentials
        return String.format("Basic %s", new String(encodedCredentials));
    }
    
    public String getWebhookSecret(){
        return creds.get("X-Webhook-Secret");
    }
    private String getAdminApiUrl(){
        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        int month = calendar.get(Calendar.MONTH) ;
        int m = (month/3) * 3 + 1 ;
        int y = calendar.get(Calendar.YEAR);
        
        
        return String.format("%s/admin/api/%d-%02d", getStoreUrl(), y,m);
    }
    
    /* Shopify Store data utilities
     *
     *
     */
    
    public Shop getShop() {
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
        JSONObject groupSettingJson = cache.get("setting.group."+attributeKey.getGroup().getKey(),()->{
            JSONArray array =  get(new SettingAttribute(attributeKey).getGroupApiEndPoint(), new JSONObject());
            JSONObject json = new JSONObject();
            for (Object o : array) {
                JSONObject settingAttributeJSON = (JSONObject) o;
                json.put(settingAttributeJSON.get("id"),settingAttributeJSON);
            }
            return json;
        });
        return (JSONObject) groupSettingJson.get(attributeKey.getKey());
    }
    
    
    public Products getProducts() {
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
                JSONArray response = get(finalUrl, new JSONObject());
                
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
    
    
    public ECommerceOrder findShopifyOrder(String shopifyOrderId, boolean draft){
        try {
            
            JSONObject orderDetails = get("/orders/" + shopifyOrderId, new JSONObject());
            return new ECommerceOrder(orderDetails);
        } catch (Exception e) {
            return null;
        }
        
    }
    
    private final TimeSensitiveCache cache = new TimeSensitiveCache(Duration.ofDays(1L));
    

}
