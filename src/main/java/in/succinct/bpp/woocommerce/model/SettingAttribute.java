package in.succinct.bpp.woocommerce.model;

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SettingAttribute extends WooCommerceObjectWithId {

    private  @NotNull final SettingGroup group;
    private  @NotNull final AttributeKey apiResourceId;

    public enum AttributeKey {
        ADDRESS_1("woocommerce_store_address", SettingGroup.GENERAL),
        ADDRESS_2("woocommerce_store_address_2", SettingGroup.GENERAL),
        CITY("woocommerce_store_city", SettingGroup.GENERAL),
        COUNTRY("woocommerce_default_country", SettingGroup.GENERAL),
        CONTINENTS("continents", SettingGroup.DATA),
        PRICES_INCLUDES_TAX("woocommerce_prices_include_tax", SettingGroup.TAX),
        CURRENCY("woocommerce_currency", SettingGroup.GENERAL),
        ;

        private final String key;

        private final SettingGroup group;

        AttributeKey(String key, SettingGroup group) {
            this.key = key;
            this.group = group;
        }

        public String getKey() {
            return key;
        }

        public SettingGroup getGroup() {
            return group;
        }

        public static List<AttributeKey> getSettingGroupAttributes(SettingGroup group) {
            return Arrays.stream(values())
                    .filter(e -> e.group == group)
                    .collect(Collectors.toList());
        }
    }

    public SettingAttribute(@NotNull AttributeKey apiResourceId) {
        this.group = apiResourceId.getGroup();
        this.apiResourceId = apiResourceId;
    }


    public SettingAttribute(JSONObject object, @NotNull AttributeKey apiResourceId) {
        super(object);
        this.group = apiResourceId.getGroup();
        this.apiResourceId = apiResourceId;
    }

    public @NotNull SettingGroup getGroupId() {
        return group;
    }

    /*
    public String getApiEndpoint() {
        return String.format("/settings/%s/%s",group.getKey(), apiResourceId.getKey());
    }

     */

    public String getGroupApiEndPoint(){
        return String.format("/settings/%s",group.getKey());
    }

    public String getValue() {
        return get("value");
    }

    public String getDefault() {
        return get("default");
    }
}
