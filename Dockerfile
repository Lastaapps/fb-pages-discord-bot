FROM gradle:9.1-jdk21 AS build
ARG SENTRY_AUTH_TOKEN
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle :shadowJar --no-daemon -Psentry.authToken=${SENTRY_AUTH_TOKEN}

FROM openjdk:21
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
