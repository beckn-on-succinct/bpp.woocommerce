package in.succinct.bpp.woocommerce.model;

public class GeneralSetting extends WooCommerceObjectWithId {

    public GeneralSetting(){
    }

    public void setAttribute(SettingAttribute.AttributeKey key, SettingAttribute attribute) {
        set(key.getKey(), attribute);
    }

    public SettingAttribute getAttribute(SettingAttribute.AttributeKey key) {
        return get(SettingAttribute.class, key.getKey());
    }

}
