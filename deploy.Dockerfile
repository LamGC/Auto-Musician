FROM openjdk:17.0.1-oraclelinux7

VOLUME /root/run
WORKDIR /root/run
EXPOSE 8080

ENTRYPOINT ["/bin/bash", "-c"]
CMD ["/root/Auto-Musician/bin/Auto-Musician"]

COPY ./build/install/Auto-Musician /root/Auto-Musician/
