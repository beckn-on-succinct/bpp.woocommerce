# bpp.woocommerce
WooCommerce adaptor to [bpp-shell](https://github.com/venkatramanm/bpp.shell) 

# Installation (Linux)

To install from source: 

1. Install dependencies
	* jdk (17+)
	* apache maven(3.8.4+)
---	 
2. Run the following command from your home directory. 
```
    $ curl https://raw.githubusercontent.com/venkatramanm/easy-installers/master/bpp-woo-installer.sh | bash
    $ cd site/woocommerce.app
    $ cp overrideProperties/config/swf.properties.sample overrideProperties/config/swf.properties 
```
---	 
3. Edit  overrideProperties/config/swf.properties and point swf.host etc to the correct domain/subdomain where this server would be hosted.
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
    in.succinct.bpp.shell.registry.url=https://registry.becknprotocol.io/subscribers
    in.succinct.bpp.shell.registry.id=registry.becknprotocol.io..LREG
    in.succinct.bpp.shell.country.iso.3=IND
    in.succinct.bpp.shell.country.iso.2=IN
    
    ##Usually same as host but can be different.
    in.succinct.bpp.shell.subscriber.id=your_subscriber_id
    
    in.succinct.bpp.woocommerce.storeUrl=https://your-wordpress-domain/index.php/wp-json/wc/v3
    #Generated in Woocomerce Settings->Advanced->REST API
    in.succinct.bpp.woocommerce.clientId=ck_....
    in.succinct.bpp.woocommerce.secret=cs_...
    
    #Stores city std code as std:080 for.e.g.
    in.succinct.bpp.woocommerce.city=std:080
    in.succinct.bpp.woocommerce.hmac.key=some_secret_you_like
```
---	 

4. On Woocommerce store, do the following
	* Settings->Advanced->Webhooks and create a webhook for topic Order updated to be delivered to https://your_fully_qualified_domain/bpp/hook 
	* Choose the secret specified in your swf.properties against in.succinct.bpp.woocommerce.hmac.key
---	 
	
5. To Register on a network registry:
	* On Beckn reference registry , you need not do any thing. The application automatically onboards on startup. 
	* To register on Other registries, you need to follow that network's SOP. 
	* To get your subscriber_json run,
``` 
    $ curl https://your_domain/bpp/subscriber_json
```
---	 
6. To bring up your service, Run:
```
    $ chmod +x bin/swfstart ; bin/swfstart 		
```
---	 
