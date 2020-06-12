all: target/security-1.0.jar

target/security-1.0.jar: src/main/java/com/dish/anywhere/*.java
	mvn package

clean:
	mvn clean
