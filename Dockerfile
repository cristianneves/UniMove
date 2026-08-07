# Imagem de runtime pura: o .jar chega pronto do CI (.github/workflows/deploy.yml),
# que roda `mvn -B verify` antes. Nao ha stage de build aqui de proposito.
#
# Nao ha nenhum `RUN` aqui de proposito: mantem o build multiplataforma barato
# (`buildx --platform ...` nao precisa emular nada) e a imagem minima.
#
# Para buildar na mao: `mvn package && cp target/unimove-backend-*.jar app.jar && docker build -t unimove .`
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY app.jar app.jar

# UID nao privilegiado. Numerico de proposito: dispensa `RUN adduser` (ver acima).
# O Docker aceita um UID sem entrada em /etc/passwd.
USER 65532:65532

# Calibrado para o container de 512 MB do Render (plano free):
#   MaxRAMPercentage=50  -> heap de ~256 MB, deixando espaco para metaspace,
#                           code cache, threads e memoria nativa. Com 75% a JVM
#                           estoura o limite e o container e morto por OOM.
#   SerialGC             -> o G1 sobe varios threads e estruturas proprias; num
#                           heap pequeno com 0.1 vCPU ele custa mais do que rende.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=50.0 -Duser.timezone=UTC"
EXPOSE 8080

# `exec` faz o java virar PID 1 e receber o SIGTERM do `docker compose down`.
# Sem ele o shell segura o sinal e o shutdown gracioso nunca acontece.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
