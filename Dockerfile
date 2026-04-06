FROM eclipse-temurin:21-jdk

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    maven git curl wget unzip ca-certificates bash \
    postgresql postgresql-contrib \
    && rm -rf /var/lib/apt/lists/*

ARG JAVAFX_VERSION=21.0.5
RUN mkdir -p /opt/javafx && \
    wget --progress=dot:giga --tries=5 --timeout=20 --waitretry=3 \
      -O /tmp/javafx.zip \
      "https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_linux-x64_bin-sdk.zip" && \
    unzip -q /tmp/javafx.zip -d /opt/javafx && \
    rm /tmp/javafx.zip && \
    ln -s /opt/javafx/javafx-sdk-${JAVAFX_VERSION} /opt/javafx/current

ENV JAVAFX_HOME=/opt/javafx/current

ARG GLASSFISH_VERSION=8.0.0
RUN mkdir -p /opt/glassfish && \
    wget -qO /tmp/glassfish.zip "https://download.eclipse.org/ee4j/glassfish/glassfish-${GLASSFISH_VERSION}.zip" && \
    unzip -q /tmp/glassfish.zip -d /opt && \
    rm /tmp/glassfish.zip && \
    mv /opt/glassfish8 /opt/glassfish

ENV GLASSFISH_HOME=/opt/glassfish/glassfish8
ENV PATH="${GLASSFISH_HOME}/bin:${PATH}"

WORKDIR /work
COPY . /work
COPY entrypoint-single.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

VOLUME ["/var/lib/postgresql/data"]

EXPOSE 8080

CMD ["/entrypoint.sh"]