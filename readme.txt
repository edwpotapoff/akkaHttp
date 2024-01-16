
Генерация сертефиката
keytool.exe -genkey -alias mykey -keyalg RSA -keypass 123456 -storepass 123456 -keystore c:\temp\keystore.jks
keytool -certreq -keyalg RSA -alias mykey -file domain.csr -keystore c:\temp\keystore.jks
keytool -importkeystore -srckeystore c:\temp\keystore.jks -destkeystore c:\temp\keystore.jks -deststoretype pkcs12