FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21

COPY build/libs/*.jar /app/

ENV TZ="Europe/Oslo"
ENV JAVA_OPTS='-XX:MaxRAMPercentage=90'

WORKDIR /app

CMD ["-jar", "app.jar"]

ARG BYGD_PA_NY='2026-01-30T10:18:46'
