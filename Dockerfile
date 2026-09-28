FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
RUN apt-get update && apt-get install -y maven && \
    cd backend && mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/target/apiflow-*.jar app.jar
COPY apiflow /usr/local/bin/apiflow
RUN chmod +x /usr/local/bin/apiflow
ENV APIFLOW_DATA=/data
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--apiflow.data-file=/data/apiflow.json"]
