package in.succinct.bpp.woocommerce.model;

public enum SettingGroup {
    GENERAL("general"),
    DATA("data"),
    TAX("tax"),;

    private final String key;

    SettingGroup(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
