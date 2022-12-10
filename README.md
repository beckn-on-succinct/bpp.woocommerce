# bpp.woocommerce
WooCommerce adaptor to bpp-shell 

# Installation (Linux)

To install from source: 

1. Install dependencies
	* jdk (17+)
	* apache maven(3.8.4+)
	 
1. Run the following command from your home directory. 

		$ curl https://raw.githubusercontent.com/venkatramanm/easy-installers/master/bpp-woo-installer.sh | bash
		$ cd site/bpp.woocommerce
		$ cp overrideProperties/config/swf.properties.sample overrideProperties/config/swf.properties 

1. Edit  overrideProperties/config/swf.properties and point swf.host etc to the correct domain/subdomain where this server would be hosted.

``` 
#Point to your public url reachable by the beckn network like ondc.
#swf.host=your_fully_qualified_domain
#swf.external.port=443
#swf.external.scheme=https
...
#swf.encryption.support=true
#swf.key.store.directory=./.keystore
#swf.key.store.password=mypassword
#swf.key.entry.succinct.password=myentrypassword
...
in.succinct.bpp.woocommerce.storeUrl=https://your-wordpress-domain/index.php/wp-json/wc/v3
#Generated in Woocomerce Settings->Advanced->REST API
in.succinct.bpp.woocommerce.clientId=ck_....
in.succinct.bpp.woocommerce.secret=cs_...

#Stores city std code as std:080 for.e.g.
in.succinct.bpp.woocommerce.city=std:080
in.succinct.bpp.woocommerce.hmac.key=some_secret_you_like

```

1. On Woocommerce store, do the following
	* Settings->Advanced->Webhooks and create a webhook for topic Order updated to be delivered to https://your_fully_qualified_domain/woo_commerce/hook 
	* Choose the secret specified in your swf.properties against in.succinct.bpp.woocommerce.hmac.key
		