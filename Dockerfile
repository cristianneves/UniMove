# Imagem de runtime pura: o .jar chega pronto do CI (.github/workflows/deploy.yml),
# que roda `mvn -B verify` antes. Nao ha stage de build aqui de proposito.
#
# A VPS de producao e ARM64 (Oracle Ampere A1). Como o .jar independe de
# arquitetura e este Dockerfile nao tem NENHUM `RUN`, o
# `docker buildx build --platform linux/arm64` monta a imagem num runner x86
# sem emulacao QEMU — segundos em vez de minutos.
#
# Para buildar na mao: `mvn package && cp target/unimove-backend-*.jar app.jar && docker build -t unimove .`
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY app.jar app.jar

# UID nao privilegiado. Numerico de proposito: dispensa `RUN adduser` (ver acima).
# O Docker aceita um UID sem entrada em /etc/passwd.
USER 65532:65532

# MaxRAMPercentage respeita o limite do container (mem_limit no compose),
# nao a RAM do host.
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"
EXPOSE 8080

# `exec` faz o java virar PID 1 e receber o SIGTERM do `docker compose down`.
# Sem ele o shell segura o sinal e o shutdown gracioso nunca acontece.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
