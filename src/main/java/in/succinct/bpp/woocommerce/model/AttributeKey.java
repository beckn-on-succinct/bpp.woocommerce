package in.succinct.bpp.woocommerce.model;

public enum AttributeKey {
    item("item"),
    email("email"),
    code("code"),
    name("name"),
    countries("countries"),
    currencyCode("currency_code"),
    weightUnit("weight_unit"),
    dimensionUnit("dimension_unit"),
    state("state"),
    states("states"),
    images("images"),
    attributes("attributes"),
    defaultAttributes("default_attributes"),
    status("status"),
    stockStatus("stock_status"),
    purchasable("purchasable"),
    length("length"),
    width("width"),
    height("height"),
    position("position"),
    options("options"),
    type("type"),
    price("price"),
    regularPrice("regular_price"),
    weight("weight"),
    dimension("dimension"),
    parentId("parent_id"),
    categories("categories"),
    tags("tags"),
    variations("variations"),
    page("page"),
    perPage("per_page"),
    id("id"),
    sku("sku"),
    description("description"),
    shortDescription("short_description"),
    taxStatus("tax_status"),
    taxClass("tax_class"),
    metaKey("key"),
    metaValue("value"),
    currency("currency"),
    sourceName("source_name"),
    inr("INR"),
    beckn("beckn"),
    becknDash("beckn-"),
    bapId("bap_id"),
    bapUri("bap_uri"),
    domain("domain"),
    transactionId("transaction_id"),
    city("city"),
    country("country"),
    coreVersion("core_version"),
    publish("publish"),
    instock("instock"),
    products("products"),
    force("force"),
    orders("orders"),
    address1("address_1"),
    address2("address_2"),
    firstName("first_name"),
    lastName("last_name"),
    phone("phone"),
    postcode("postCode"),
    shipping("shipping"),
    billing("billing"),
    locationId("location_id"),
    quantity("quantity"),
    productId("product_id"),
    lineItems("line_items"),
    total("total"),
    subTotal("subTotal"),
    dateCreatedGmt("date_created_gmt"),
    dateUpdatedGmt("date_updated_gmt"),
    datePaidGmt("date_paid_gmt"),
    dateCompletedGmt("date_completed_gmt"),
    cancelled("cancelled"),
    number("number"),
    metaDataArray("meta_data"),

;

    private final String key;

    AttributeKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
