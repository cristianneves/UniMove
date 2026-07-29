# -----------------------------------------------------------------------------
# Stage 1: Build da Aplicação
# -----------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copia arquivos de dependência primeiro para aproveitar cache do Docker
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN mvn dependency:go-offline -B

# Copia código-fonte e compila o pacote
COPY src ./src
RUN mvn clean package -DskipTests

# -----------------------------------------------------------------------------
# Stage 2: Runtime da Aplicação (Imagem Final Leve)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Criar usuário sem privilégios de root por segurança
RUN addgroup -S unimove && adduser -S unimove -G unimove
USER unimove:unimove

# Copiar apenas o JAR gerado no stage anterior
COPY --from=builder /app/target/*.jar app.jar

# Configurações de JVM para ambiente containerizado
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
