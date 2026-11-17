# ---- Etapa 1: compilar el .jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
# gradlew se subio sin permiso de ejecucion; se lo damos aqui
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# ---- Etapa 2: imagen ligera solo para ejecutar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# Limita la memoria para que quepa en el plan gratuito (512 MB)
ENTRYPOINT ["java", "-Xmx350m", "-jar", "app.jar"]
