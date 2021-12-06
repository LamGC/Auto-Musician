FROM openjdk:17.0.1-oraclelinux7

VOLUME /root/run/
ENV PROJECT_RUN_WORKDIR /root/run/
WORKDIR /root/run/
EXPOSE 8080
EXPOSE 8443

ENTRYPOINT ["/bin/bash", "-c"]
CMD ["/root/Auto-Musician/gradlew", "run"]

COPY . /root/Auto-Musician/