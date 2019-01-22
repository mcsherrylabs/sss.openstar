Cert had to be generated 
~~~
keytool -genkey -keyalg RSA -alias localhost -keystore keystore.jks -storepass password -validity 360 -keysize 2048
~~~
exported 
~~~
keytool -export -alias localhost -file mydomain.crt -keystore keystore.jks
~~~
And then imported into cacerts

~~~
sudo keytool -import -trustcacerts -file mydomain.crt -alias CA_LOCALHOST -keystore /usr/lib/jvm/java-1.8.0-openjdk-amd64/jre/lib/security/cacerts
~~~

To debug ssl use VM option 
~~~
-Djavax.net.debug=all
~~~