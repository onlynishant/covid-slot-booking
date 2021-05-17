# covid-slot-booking

This is a simple Java program that will help you to book COVID appointment.</br>
It requires Java 8.</br>
Just run bellow command and follow instrcutions:

<b>It requires you to enter captcha manually before sending booking request. so wait for beep sound. </b>

```

1. you need to find out district id of the targeting area. (easy for tech folks. just use developer mode in browser. screenshot attached)
2. you need to enter OTP manually (you will hear 2 beep sound) every time session expires (15 mins)
3. you need to enter captcha (you will hear 5 beep sound) when slot is available in Popup (you will get 4-5 captcha window if it fails to book.)

---------------------------
Follow these steps to run:
---------------------------
git clone https://github.com/onlynishant/covid-slot-booking.git
cd covid-slot-booking
mvn package
// for weekly search
java -cp "target/dependency/*:target/CowinHelp-1.0-SNAPSHOT-jar-with-dependencies.jar" Runner
// for daily search
java -cp "target/dependency/*:target/CowinHelp-1.0-SNAPSHOT-jar-with-dependencies.jar" Runner2
```

Please install <b>maven</b>: https://maven.apache.org/install.html
