package in.succinct.bpp.woocommerce.model;

public class Shop extends WooCommerceObjectWithId{

    private  final GeneralSetting generalSetting;
    private final TaxSetting taxSetting;


    public Shop( GeneralSetting generalSetting, TaxSetting taxSetting) {
        super();
        this.generalSetting = generalSetting;
        this.taxSetting = taxSetting;
    }

    public GeneralSetting getGeneralSetting() {
        return generalSetting;
    }

    public TaxSetting getTaxSetting() {
        return taxSetting;
    }
    
    
    public Long getPrimaryLocationId() {
        return 1L;
    }
    
    public boolean isTaxesIncluded() {
        return getTaxSetting().getAttribute(SettingAttribute.AttributeKey.PRICES_INCLUDES_TAX).getValue()
                .equals("yes");
    }
}
