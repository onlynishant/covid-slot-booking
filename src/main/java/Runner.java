import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import lombok.ToString;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @author Nishant Kumar
 * @date 10/05/21
 */


@ToString
class Session {
  public String session_id;
  public String date;
  public int available_capacity;
  public int min_age_limit;
  public String vaccine;
  public List<String> slots;
}

@ToString
class VaccineFee {
  public String vaccine;
  public String fee;
}

@ToString
class Center {
  public int center_id;
  public String name;
  public String address;
  public String state_name;
  public String district_name;
  public String block_name;
  public int pincode;
  public int lat;
  @JsonAlias({"long"})
  public int _long;
  public String from;
  public String to;
  public String fee_type;
  public List<VaccineFee> vaccine_fees;
  public List<Session> sessions;
}

@ToString
class Root {
  public List<Center> centers;
}

@ToString
class Appointment {
  public String appointment_id;
  public int center_id;
  public String name;
  public String state_name;
  public String district_name;
  public String block_name;
  public String from;
  public String to;
  public int dose;
  public String session_id;
  public String date;
  public String slot;
}

@ToString
class Beneficiary {
  public String beneficiary_reference_id;
  public String name;
  public String birth_year;
  public String gender;
  public String mobile_number;
  public Object photo_id_type;
  public String photo_id_number;
  public String comorbidity_ind;
  public String vaccination_status;
  public String vaccine;
  public String dose1_date;
  public String dose2_date;
  public List<Appointment> appointments;
}

@ToString
class BeneficiaryList {
  public List<Beneficiary> beneficiaries;
}

public class Runner {

  public static final ObjectMapper jsonMapper = new ObjectMapper();

  // These APIs are subject to a rate limit of 100 API calls per 5 minutes per IP.
  private static final String BASE_URL = "https://cdn-api.co-vin.in/api";
  private static final String authentication_URL = "/v2/auth/generateMobileOTP";
  private static final String otp_validation_URL = "/v2/auth/validateMobileOtp";
  private static final String beneficiaries_URL = "/v2/appointment/beneficiaries";
  private static final String findByPin_URL = "/v2/appointment/sessions/public/findByPin";
  private static final String findByDistrict_URL = "/v2/appointment/sessions/calendarByDistrict";
  private static final String appointment_URL = "/v2/appointment/schedule";
  private static final String captcha_URL = "/v2/auth/getRecaptcha";

  private static final File tokenFile = new File("token.txt");

  static final String PATTERN = "dd-MM-yyyy";
  static final SimpleDateFormat dateFormat = new SimpleDateFormat();

  static final String API_KEY = "3sjOr2rmM52GzhpMHjDEE1kpQeRxwFDr4YcBEimi";

  // configuration data
  static final List<String> BENEFICIARIES = new ArrayList<>();//
  static int MIN_AGE = 18;
  static int district_id = 294; // Bangalore BBMP
  private static String mobile;
  private static String VACCINE;
  private static String DOSE;
  private static String START_DATE;

  static {
    jsonMapper.registerModule(new Jdk8Module());
    jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    dateFormat.applyPattern(PATTERN);
  }

  public static Map<String, Object> getResponse(String url, Map<String, Object> input, Map<String, String> header, Method method) throws IOException {
//    System.out.println("URL: " + url);
    CloseableHttpResponse response;
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpPost httppost = new HttpPost(url);
      HttpGet httpget = null;
      if (method == Method.GET) {
        httpget = new HttpGet(url);
      }

//      if(input != null)
      httppost.setHeader("Content-type", ContentType.APPLICATION_JSON);
      if (httpget != null)
        httpget.setHeader("Content-type", ContentType.APPLICATION_JSON);

      if (header != null) {
        header.forEach(httppost::setHeader);
        if (httpget != null) {
          header.forEach(httpget::setHeader);
        }
      }

      if (input != null && method != Method.GET) {
        String json = jsonMapper.writeValueAsString(input);
//        System.out.println("Request: " + json);
        httppost.setEntity(new StringEntity(json));
      }

      if (method == Method.GET) {
//        System.out.println(header);
        response = httpclient.execute(httpget);
      } else {
        response = httpclient.execute(httppost);
      }
      HttpEntity entity = response.getEntity();

      if (response.getCode() != 200)
        System.out.println("URL: " + url + ", response Code: " + response.getCode());

      if (entity != null) {
        try (InputStream instream = entity.getContent()) {
          String res = new String(IOUtils.toByteArray(instream));
//          System.out.println("Response: " + res);
          try {
            Map<String, Object> responseMap = jsonMapper.readValue(res, Map.class);
            responseMap.put("_raw", res);
            responseMap.put("status_code", response.getCode());
//            System.out.println(responseMap);
            return responseMap;
          } catch (Exception e) {
//            System.out.println("Exception: " + e.getMessage() + ", response: " + res);
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("response", res);
            responseMap.put("error", e.getMessage());
            responseMap.put("status_code", response.getCode());
            return responseMap;
          }
        }
      }
    }
    return null;
  }

  public static final String pattern = "(<path d=)(.*?)(fill=\\\"none\\\"/>)";
  public static final Pattern search = Pattern.compile(pattern);
//  public static String getCaptcha(Map<String, Object> input){
//
//  }

  public static String getToken(boolean readOld) throws IOException, InterruptedException {
    Map<String, String> header = new HashMap<>();
    header.put(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36");

    Map<String, Object> input = new HashMap<>();
    input.put("mobile", mobile);
    input.put("secret", "U2FsdGVkX1+z/4Nr9nta+2DrVJSv7KS6VoQUSQ1ZXYDx/CJUkWxFYG6P3iM/VW+6jLQ9RDQVzp/RcZ8kbT41xw==");

    String token = null;
    Scanner sc = new Scanner(System.in);

    String txnIdOld = null;
    boolean isValid = false;
    while (!isValid) {
      Map<String, Object> getOtp = getResponse(BASE_URL + authentication_URL, input, header, Method.POST);

      String txnId = (String) getOtp.get("txnId");

      if ((int) getOtp.getOrDefault("status_code", 0) != 200) {
        System.out.println(getOtp);
      }

//      if (getOtp.get("response") != null && getOtp.get("response").equals("OTP Already Sent")) {
//        txnId = txnIdOld;
//      }

      System.out.println("Enter OTP :");
      // alert sound
      for (int j = 0; j < 5; j++) {
        Toolkit.getDefaultToolkit().beep();
      }

      String otp = sc.next();

      String sha256hex = DigestUtils.sha256Hex(otp);

      System.out.println("txnId: " + txnId);

      Map<String, Object> input1 = new HashMap<>();
      input1.put("txnId", txnId);
      input1.put("otp", sha256hex);
      Map<String, Object> getToken = getResponse(BASE_URL + otp_validation_URL, input1, header, Method.POST);

      if ((int) getToken.getOrDefault("status_code", 0) != 200) {
        System.out.println(getToken);
      }

      txnIdOld = txnId;

      token = (String) getToken.get("token");

      if (token != null && !token.isEmpty()) {
        FileWriter fileWriter = new FileWriter(tokenFile);
        fileWriter.write(token);
        fileWriter.close();
        isValid = true;
      }
    }

    return token;
  }

  public static String validateToken(String token) throws IOException, InterruptedException {
    boolean isValid = false;
    while (!isValid) {
//      System.out.println("Validating token..");
      Map<String, String> header = new HashMap<>();
      header.put(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      header.put(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36");

      Map<String, Object> getToken = getResponse(BASE_URL + beneficiaries_URL, null, header, Method.GET);

      if ((int) getToken.getOrDefault("status_code", 0) != 200) {
        System.out.println(getToken);
      }

      if (BENEFICIARIES.isEmpty()) {
        String s = (String) getToken.get("_raw");
        //ignore it
        if (s == null || s.toLowerCase().contains("unauthenticated"))
          continue;
        Scanner sc = new Scanner(System.in);
        BeneficiaryList beneficiaries = jsonMapper.readValue(s, BeneficiaryList.class);
        beneficiaries.beneficiaries.forEach(beneficiary -> {
          System.out.println(beneficiary.name);
          System.out.println("Do you want to include it (y/n):");
          String ans = sc.next();
          if (ans.toLowerCase().equals("y")) {
            System.out.println("Adding " + beneficiary.name);
            BENEFICIARIES.add(beneficiary.beneficiary_reference_id);
            int age = Calendar.getInstance().get(Calendar.YEAR) - Integer.parseInt(beneficiary.birth_year);
            if (age >= 45) {
              MIN_AGE = 45;
            }

            if (beneficiary.dose1_date.isEmpty()) {
              System.out.println("First Dose");
              DOSE = "1";
            } else {
              System.out.println("Second Dose");
              DOSE = "2";
              Calendar calendar = Calendar.getInstance();
              try {
                calendar.setTime(dateFormat.parse(beneficiary.dose1_date));
              } catch (ParseException e) {
                e.printStackTrace();
              }
              calendar.add(Calendar.DAY_OF_MONTH, 42);
              START_DATE = dateFormat.format(calendar.getTime());
            }
          }
        });

        if (BENEFICIARIES.isEmpty()) {
          System.out.println("Need at least one BENEFICIARY");
          System.exit(0);
        }
        System.out.println("MIN AGE:" + MIN_AGE);
        System.out.println("Vaccine preference (0 = any, 1 = COVAXIN, 2 = COVISHIELD): ");
        int c = Integer.parseInt(sc.next());
        if (c == 1) {
          VACCINE = "COVAXIN";
        } else if (c == 2) {
          VACCINE = "COVISHIELD";
        }
      }

      if (getToken != null && (int) getToken.getOrDefault("status_code", 0) == 200) {
        isValid = true;
      } else {
        System.out.println("Generating new token");
        token = getToken(false);
      }
    }
    return token;
  }

  public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException {

    System.out.println("Searching for AGE: " + MIN_AGE + " in district_id: " + district_id);
    if (tokenFile.createNewFile()) {
      System.out.println("tokenFile File created: " + tokenFile.getName());
    } else {
      System.out.println("tokenFile File already exists.");
    }

    Scanner sc = new Scanner(System.in);

    System.out.println("Enter phone number: ");
    mobile = sc.next();

    System.out.println("Do you want to change default district_id (Bangalore BBMP): " + district_id + " (y/n)");
    String o = sc.next();
    if (o.equalsIgnoreCase("y")) {
      System.out.println("Enter new district_id: ");
      district_id = Integer.parseInt(sc.next());
      System.out.println("Updating district_id: " + district_id);
    }

    String token = getToken(true);
    System.out.println("token: " + token);

    Map<String, String> header = new HashMap<>();
    header.put(HttpHeaders.AUTHORIZATION, "Bearer " + token);
//    header.put("origin", "https://selfregistration.cowin.gov.in");
//    header.put("referer", "https://selfregistration.cowin.gov.in/");
    header.put(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36");

    AtomicBoolean done = new AtomicBoolean(false);
    while (!done.get()) {
      for (int i = 0; i < 4; i++) {

        token = validateToken(token);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 7 * i);
        String date1 = dateFormat.format(calendar.getTime());
        if (START_DATE != null) {
          date1 = START_DATE;
        }
        String urlPath = "?district_id=" + district_id + "&date=" + date1;
        if (VACCINE != null && !VACCINE.isEmpty()) {
          urlPath = urlPath + "&vaccine=" + VACCINE;
        }
        Map<String, Object> getSlot = getResponse(BASE_URL + findByDistrict_URL + urlPath, null, header, Method.GET);

        String s = (String) getSlot.get("_raw");
        //ignore it
        if (s == null || s.toLowerCase().contains("unauthenticated"))
          continue;
        Root root = jsonMapper.readValue(s, Root.class);

        if (root != null) {
          Map<String, String> finalHeader = header;
          root.centers.forEach(center -> {
            center.sessions.forEach(session -> {
              if (session.available_capacity > 1 && session.min_age_limit == MIN_AGE) {
                System.out.println("Slot available: " + center);
                System.out.println("Pin code:" + center.pincode);
                System.out.println("Hospital Name:" + center.name);
                System.out.println("Hospital Address:" + center.address);
                System.out.println("Available Capacity:" + session.available_capacity);
                System.out.println("Date:" + session.date);
                System.out.println("Slots:" + Arrays.toString(session.slots.toArray()));

                session.slots.forEach(s1 -> {
                  List<String> beneficiaries = new ArrayList<>();
                  beneficiaries.addAll(BENEFICIARIES);

                  Map<String, Object> input1 = new HashMap<>();
                  input1.put("dose", Integer.valueOf(DOSE));
                  input1.put("session_id", session.session_id);
                  input1.put("center_id", center.center_id);
                  input1.put("slot", s1);
                  input1.put("beneficiaries", beneficiaries);

                  try {
                    System.out.println("Trying to book appointment:");

                    // alert sound
                    for (int j = 0; j < 5; j++) {
                      Toolkit.getDefaultToolkit().beep();
                    }

                    // generate captcha
                    System.out.println("Getting captcha");
                    Map<String, Object> getCaptcha = getResponse(BASE_URL + captcha_URL, null, finalHeader, Method.POST);
//                    System.out.println(getCaptcha);

                    String svg = (String) getCaptcha.get("captcha");

                    if (svg == null) {
                      System.out.println("Empty SVG file:" + getCaptcha);
                      return;
                    }

                    FileWriter fileWriter = new FileWriter("captcha-new.svg");
                    fileWriter.write(svg.toCharArray());
                    fileWriter.close();

                    try {
                      CaptchaSolver.svgToPng("captcha-new.svg", "captcha-new.png");
                    } catch (TranscoderException e) {
                      e.printStackTrace();
                    }

//                    Desktop.getDesktop().open(new File("captcha-new.png"));
                    String captcha = CaptchaWindow.createWindow();

//                    System.out.println("Enter captcha:");
//
//                    String captcha = sc.next();
                    System.out.println(captcha);

                    input1.put("captcha", captcha);

                    Map<String, Object> getAppointment = getResponse(BASE_URL + appointment_URL, input1, finalHeader, Method.POST);

                    System.out.println("Appointment status code: " + getAppointment.get("status_code"));

                    String appointment_id = (String) getAppointment.get("appointment_confirmation_no");
                    if (appointment_id != null && !appointment_id.isEmpty()) {
                      System.out.println("----------------------------------------------------------------------");
                      System.out.println("Appointment booked: " + appointment_id);
                      System.out.println("Center: " + center);
                      System.out.println("Session: " + session);
                      System.out.println("Slot: " + s1);
                      done.set(true);
                      System.out.println("----------------------------------------------------------------------");
                      System.exit(0);
                      return;
                    } else {
                      System.out.println("Failed :(" + getAppointment);
                    }
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                });


              }
            });
          });
//          System.out.println("No Slot available :(");
          Thread.sleep(500);
        }
      }
    }
  }
}
