# Usa una imagen base de OpenJDK
FROM openjdk:11-jdk-slim

# Establece el directorio de trabajo en el contenedor
WORKDIR /app

# Copia el archivo JAR compilado al contenedor
COPY target/mi-aplicacion.jar /app.jar

# Expone el puerto 8080 para la aplicación
EXPOSE 8080

# Define el comando de ejecución
ENTRYPOINT ["java", "-jar", "/app.jar"]