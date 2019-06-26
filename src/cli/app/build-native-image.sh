native-image --no-server \
    -jar target/geogig/libexec/geogig-cli-app-2.0-SNAPSHOT.jar \
    -H:Name=geogig \
    -H:ReflectionConfigurationFiles=reflections.json,../../cli/app/target/classes/META-INF/native-image/picocli-generated/reflect-config.json,../../cli/remoting/target/classes/META-INF/native-image/picocli-generated/reflect-config.json,../../cli/core/target/classes/META-INF/native-image/picocli-generated/reflect-config.json \
    -H:DynamicProxyConfigurationFiles=proxies.json \
    --allow-incomplete-classpath \
    --initialize-at-build-time=org.postgresql.Driver,org.postgresql.util.SharedTimer,org.hsqldb.jdbc.JDBCDriver,org.sqlite.JDBC \
    --static \
    --no-fallback \
    -Dorg.geotools.referencing.forceXY=true
