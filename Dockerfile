FROM node:20-alpine3.19 AS nodejs
RUN apk add --no-cache git
WORKDIR /usr/src/node
RUN git clone https://github.com/ravel57/ultunnel-front
WORKDIR /usr/src/node/ultunnel-front
RUN npm install
RUN npm run build

FROM gradle:8.7.0-jdk21-alpine AS gradle
COPY --chown=gradle:gradle . /home/gradle/
COPY --from=nodejs /usr/src/node/ultunnel-front/dist/.   /home/gradle/src/main/resources/static/
WORKDIR /home/gradle/
RUN gradle bootJar

FROM alpine/java:22-jdk AS java
WORKDIR /home/java/
COPY --from=gradle /home/gradle/build/libs/*.jar /home/java/ultunnel-admin-panel.jar
CMD ["java", "-jar", "ultunnel-admin-panel.jar"]