# covid-slot-booking

This is a simple Java program that will help you to book COVID appointment.</br>
It requires Java 8.</br>
Just run bellow command and follow instrcutions:

<b>It requires you to enter captcha manually before sending booking request. so wait for beep sound. </b>

```
git clone https://github.com/onlynishant/covid-slot-booking.git
cd covid-slot-booking
mvn package
// for weekly search
java -cp "target/dependency/*:target/CowinHelp-1.0-SNAPSHOT-jar-with-dependencies.jar" Runner
// for daily search
java -cp "target/dependency/*:target/CowinHelp-1.0-SNAPSHOT-jar-with-dependencies.jar" Runner2
```

Please install <b>maven</b>: https://maven.apache.org/install.html
