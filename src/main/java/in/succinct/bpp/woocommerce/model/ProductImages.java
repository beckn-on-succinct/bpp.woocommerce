package in.succinct.bpp.woocommerce.model;

import in.succinct.beckn.BecknObjectsWithId;
import in.succinct.bpp.woocommerce.model.ProductImages.ProductImage;
import org.json.simple.JSONObject;

public class ProductImages extends BecknObjectsWithId<ProductImage> {

    public  static  class  ProductImage extends WooCommerceObjectWithId{

        public ProductImage(JSONObject object) {
            super(object);
        }


        public String getSrc(){
            return get("src");
        }


    }
}
