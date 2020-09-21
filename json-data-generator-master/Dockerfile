######################################################################
# the first stage of our build will use a maven 3.6.1 parent image
######################################################################

FROM maven:3.6.1-jdk-8-alpine AS MAVEN_BUILD

# copy the pom and src code to the container
COPY src /home/app/src
COPY pom.xml /home/app
COPY kafkasims.json /home/app
COPY kafkaSimsWorkFlow.json /home/app


# package our application code
RUN mvn -f /home/app/pom.xml clean package


########################################################################
# we will use openjdk 8 with alpine as it is a very small linux distro
########################################################################
FROM openjdk:8-jre-alpine3.9


#create a app dir in new image
#RUN mkdir -p /opt/dataGen
#RUN chmod 777 /opt/dataGen


# copy only the artifacts we need from the first stage and discard the rest
COPY --from=MAVEN_BUILD /home/app/target/json-data-generator-1.4.2-SNAPSHOT-bin.tar /json-data-generator-1.4.2-SNAPSHOT-bin.tar
COPY --from=MAVEN_BUILD /home/app/kafkasims.json /kafkasims.json
COPY --from=MAVEN_BUILD /home/app/kafkaSimsWorkFlow.json /kafkaSimsWorkFlow.json


#RUN cd /opt/dataGen/ && tar -xf json-data-generator-1.4.2-SNAPSHOT-bin.tar && cd json-data-generator-1.4.2-SNAPSHOT && chmod 777 json-data-generator-1.4.2-SNAPSHOT && chmod 777 conf && chmod 777 lib && cp /opt/dataGen/kafkasims.json /opt/dataGen/json-data-generator-1.4.2-SNAPSHOT/conf/ && chmod 777 /opt/dataGen/json-data-generator-1.4.2-SNAPSHOT/conf



#########################################################################
# we will untar the package and pass our own configs kafka sims and workflow 
#########################################################################
RUN tar -xf /json-data-generator-1.4.2-SNAPSHOT-bin.tar
RUN ls -l /
RUN chmod 777 /json-data-generator-1.4.2-SNAPSHOT
RUN chmod 777 /json-data-generator-1.4.2-SNAPSHOT/conf
RUN chmod 777 /json-data-generator-1.4.2-SNAPSHOT/lib
RUN cp /kafkasims.json /json-data-generator-1.4.2-SNAPSHOT/conf/
RUN cp /kafkaSimsWorkFlow.json /json-data-generator-1.4.2-SNAPSHOT/conf/
RUN chmod 777 /json-data-generator-1.4.2-SNAPSHOT/conf




#RUN mkdir -p /opt/dataGen
#RUN chmod 777 /opt/dataGen


RUN ["echo", "testing"]
#RUN mkdir -p /opt/logs
#RUN touch /opt/RELEASE.txt
#RUN chmod 777 /opt/logs 



#Copy the entire json data gen dir along with fat jar, libs and config to the image container
#COPY json-data-generator-1.4.2-SNAPSHOT /opt/dataGen


# set the startup command to execute the jar
CMD ["java", "-jar", "/json-data-generator-1.4.2-SNAPSHOT/json-data-generator-1.4.2-SNAPSHOT.jar", "kafkasims.json"]

